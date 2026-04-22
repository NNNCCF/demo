package com.ncf.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;

@Component
public class MiniAppSignatureFilter extends OncePerRequestFilter {
    private static final String HEADER_CLIENT_ID = "X-Mini-Client-Id";
    private static final String HEADER_TIMESTAMP = "X-Mini-Timestamp";
    private static final String HEADER_NONCE = "X-Mini-Nonce";
    private static final String HEADER_SIGNATURE = "X-Mini-Signature";
    private static final String NONCE_KEY_PREFIX = "miniapp:nonce:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public MiniAppSignatureFilter(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AppProperties appProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!appProperties.getMiniapp().isSignatureEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isAdminCaptchaPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean strictPath = isStrictProtectedPath(path);
        boolean signedRequest = hasSignatureHeaders(request);
        if (!strictPath && !signedRequest) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        if (!validateRequest(wrappedRequest)) {
            reject(response, "Invalid miniapp signature");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean validateRequest(CachedBodyHttpServletRequest request) {
        String clientId = request.getHeader(HEADER_CLIENT_ID);
        String timestampHeader = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (!StringUtils.hasText(clientId)
                || !StringUtils.hasText(timestampHeader)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)) {
            return false;
        }

        AppProperties.Miniapp miniapp = appProperties.getMiniapp();
        if (!clientId.equals(miniapp.getClientId()) || !StringUtils.hasText(miniapp.getSharedSecret())) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException ex) {
            return false;
        }

        long allowedClockSkewMillis = miniapp.getAllowedClockSkewSeconds() * 1000L;
        if (Math.abs(System.currentTimeMillis() - timestamp) > allowedClockSkewMillis) {
            return false;
        }

        String query = request.getQueryString() == null ? "" : request.getQueryString();
        String body = request.getCachedBodyAsString();
        String expected = sha256Hex(String.join("\n",
                clientId,
                timestampHeader,
                nonce,
                request.getMethod().toUpperCase(),
                request.getRequestURI(),
                query,
                sha256Hex(body),
                miniapp.getSharedSecret()
        ));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }

        String nonceKey = NONCE_KEY_PREFIX + clientId + ":" + nonce;
        Boolean accepted = stringRedisTemplate.opsForValue().setIfAbsent(
                nonceKey,
                timestampHeader,
                Duration.ofSeconds(miniapp.getNonceTtlSeconds())
        );
        return Boolean.TRUE.equals(accepted);
    }

    private boolean hasSignatureHeaders(HttpServletRequest request) {
        return StringUtils.hasText(request.getHeader(HEADER_CLIENT_ID))
                || StringUtils.hasText(request.getHeader(HEADER_TIMESTAMP))
                || StringUtils.hasText(request.getHeader(HEADER_NONCE))
                || StringUtils.hasText(request.getHeader(HEADER_SIGNATURE));
    }

    private boolean isStrictProtectedPath(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/institution/")
                || path.startsWith("/api/mini/");
    }

    private boolean isAdminCaptchaPath(String path) {
        return path.equals("/api/auth/captcha")
                || path.startsWith("/api/auth/captcha/");
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", 403, "message", message)
        ));
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
