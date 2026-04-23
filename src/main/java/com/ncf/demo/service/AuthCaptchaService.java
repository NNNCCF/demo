package com.ncf.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.common.BizException;
import com.ncf.demo.config.AppProperties;
import com.ncf.demo.web.dto.AuthCaptchaResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthCaptchaService {
    private static final String LOGIN_SCENE = "LOGIN";
    private static final String REGISTER_SCENE = "REGISTER";
    private static final String DATA_URL_BASE64_MARKER = ";base64,";
    private static final int MAX_FAIL_ATTEMPTS = 3;

    private final AppProperties appProperties;
    private final RestClient restClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AuthCaptchaService(
            AppProperties appProperties,
            RestClient restClient,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper
    ) {
        this.appProperties = appProperties;
        this.restClient = restClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public String buildClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return sha256(ip + "|" + (userAgent == null ? "" : userAgent));
    }

    public AuthCaptchaResponse issueCaptcha(String scene, String clientKey) {
        String normalizedScene = normalizeScene(scene);
        long cooldownSeconds = getCooldownSeconds(normalizedScene, clientKey);
        if (cooldownSeconds > 0) {
            return new AuthCaptchaResponse("", "", 0, cooldownSeconds);
        }

        AppProperties.Captcha captcha = appProperties.getCaptcha();
        CaptchaCreateResponse response = authorizedGet(captcha.getBaseUrl() + "?width=" + captcha.getWidth()
                        + "&height=" + captcha.getHeight()
                        + "&length=" + captcha.getLength()
                        + "&type=" + captcha.getType())
                .retrieve()
                .body(CaptchaCreateResponse.class);

        if (response == null || response.code() != 200 || response.data() == null
                || response.data().id() == null || response.data().url() == null) {
            throw new BizException(5001, "Captcha provider is unavailable");
        }

        String captchaToken = UUID.randomUUID().toString().replace("-", "");
        saveSession(captchaToken, new CaptchaSession(
                normalizedScene,
                clientKey,
                response.data().id(),
                response.data().url()
        ));

        return new AuthCaptchaResponse(
                captchaToken,
                "/api/auth/captcha/" + captchaToken + "/image",
                captcha.getSessionTtlSeconds(),
                0
        );
    }

    public byte[] fetchCaptchaImage(String captchaToken, String clientKey) {
        CaptchaSession session = loadSession(captchaToken);
        ensureSessionOwnership(session, clientKey);
        byte[] imageBytes = decodeCaptchaImage(session.imageUrl());
        if (imageBytes != null) {
            return imageBytes;
        }

        byte[] remoteImageBytes = restClient.get()
                .uri(session.imageUrl())
                .retrieve()
                .body(byte[].class);
        if (remoteImageBytes == null || remoteImageBytes.length == 0) {
            throw new BizException(5004, "Failed to load captcha image");
        }
        return remoteImageBytes;
    }

    public void verifyCaptcha(String scene, String clientKey, String captchaToken, String captchaCode) {
        String normalizedScene = normalizeScene(scene);
        long cooldownSeconds = getCooldownSeconds(normalizedScene, clientKey);
        if (cooldownSeconds > 0) {
            throw new BizException(4290, "Too many attempts, retry in " + cooldownSeconds + " seconds");
        }

        CaptchaSession session = loadSession(captchaToken);
        ensureSessionOwnership(session, clientKey);
        if (!normalizedScene.equals(session.scene())) {
            throw new BizException(4008, "Captcha scene does not match");
        }

        AppProperties.Captcha captcha = appProperties.getCaptcha();
        CaptchaVerifyResponse response = authorizedGet(captcha.getBaseUrl() + "?id=" + session.thirdPartyId()
                        + "&key=" + captchaCode
                        + "&type=" + captcha.getType())
                .retrieve()
                .body(CaptchaVerifyResponse.class);

        stringRedisTemplate.delete(sessionKey(captchaToken));
        if (response == null || response.code() != 200) {
            int fails = incrementFailCount(normalizedScene, clientKey);
            if (fails >= MAX_FAIL_ATTEMPTS) {
                resetFailCount(normalizedScene, clientKey);
                startCooldown(normalizedScene, clientKey);
                throw new BizException(4291, "Too many wrong attempts, retry in " + captcha.getCooldownSeconds() + " seconds");
            }
            throw new BizException(4291, "Captcha is invalid (" + fails + "/" + MAX_FAIL_ATTEMPTS + ")");
        }
        resetFailCount(normalizedScene, clientKey);
    }

    static byte[] decodeCaptchaImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("data:image/")) {
            return null;
        }
        int markerIndex = imageUrl.indexOf(DATA_URL_BASE64_MARKER);
        if (markerIndex < 0) {
            throw new BizException(5004, "Failed to load captcha image");
        }
        String payload = imageUrl.substring(markerIndex + DATA_URL_BASE64_MARKER.length());
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw new BizException(5004, "Failed to load captcha image");
        }
    }

    private RestClient.RequestHeadersSpec<?> authorizedGet(String uri) {
        AppProperties.Captcha captcha = appProperties.getCaptcha();
        if (captcha.getApiKey() == null || captcha.getApiKey().isBlank()) {
            throw new BizException(5005, "Captcha API key is not configured");
        }
        return restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + captcha.getApiKey());
    }

    private void startCooldown(String scene, String clientKey) {
        long cooldownSeconds = appProperties.getCaptcha().getCooldownSeconds();
        stringRedisTemplate.opsForValue().set(
                cooldownKey(scene, clientKey),
                "1",
                Duration.ofSeconds(cooldownSeconds)
        );
    }

    private int incrementFailCount(String scene, String clientKey) {
        String key = failKey(scene, clientKey);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        // 失败计数窗口与冷却时长保持一致
        stringRedisTemplate.expire(key, Duration.ofSeconds(appProperties.getCaptcha().getCooldownSeconds()));
        return count == null ? 1 : count.intValue();
    }

    private void resetFailCount(String scene, String clientKey) {
        stringRedisTemplate.delete(failKey(scene, clientKey));
    }

    private long getCooldownSeconds(String scene, String clientKey) {
        Long ttl = stringRedisTemplate.getExpire(cooldownKey(scene, clientKey));
        return ttl == null || ttl <= 0 ? 0 : ttl;
    }

    private void saveSession(String captchaToken, CaptchaSession session) {
        try {
            stringRedisTemplate.opsForValue().set(
                    sessionKey(captchaToken),
                    objectMapper.writeValueAsString(session),
                    Duration.ofSeconds(appProperties.getCaptcha().getSessionTtlSeconds())
            );
        } catch (JsonProcessingException e) {
            throw new BizException(5002, "Failed to persist captcha session");
        }
    }

    private CaptchaSession loadSession(String captchaToken) {
        String raw = stringRedisTemplate.opsForValue().get(sessionKey(captchaToken));
        if (raw == null || raw.isBlank()) {
            throw new BizException(4006, "Captcha has expired, please refresh it");
        }
        try {
            return objectMapper.readValue(raw, CaptchaSession.class);
        } catch (JsonProcessingException e) {
            throw new BizException(5003, "Failed to parse captcha session");
        }
    }

    private void ensureSessionOwnership(CaptchaSession session, String clientKey) {
        if (!session.clientKey().equals(clientKey)) {
            throw new BizException(4007, "Captcha session is no longer valid");
        }
    }

    private String sessionKey(String captchaToken) {
        return "auth:captcha:session:" + captchaToken;
    }

    private String cooldownKey(String scene, String clientKey) {
        return "auth:captcha:cooldown:" + scene + ":" + clientKey;
    }

    private String failKey(String scene, String clientKey) {
        return "auth:captcha:fails:" + scene + ":" + clientKey;
    }

    private String normalizeScene(String scene) {
        if (scene == null) {
            throw new BizException(4005, "Captcha scene is required");
        }
        String normalized = scene.trim().toUpperCase();
        if (!LOGIN_SCENE.equals(normalized) && !REGISTER_SCENE.equals(normalized)) {
            throw new BizException(4005, "Unsupported captcha scene");
        }
        return normalized;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record CaptchaSession(
            String scene,
            String clientKey,
            String thirdPartyId,
            String imageUrl
    ) {
    }

    private record CaptchaCreateResponse(
            int code,
            String msg,
            CaptchaCreateData data
    ) {
    }

    private record CaptchaCreateData(
            String id,
            String url
    ) {
    }

    private record CaptchaVerifyResponse(
            int code,
            String msg,
            Object data
    ) {
    }
}
