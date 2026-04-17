package com.ncf.demo.security;

import com.ncf.demo.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_HMAC_KEY_BYTES = 32;
    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtService(AppProperties appProperties) {
        String jwtSecret = appProperties.getJwt().getSecret();
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must not be empty");
        }
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_HMAC_KEY_BYTES) {
            keyBytes = sha256(jwtSecret);
            log.warn("JWT secret is shorter than {} bytes, using SHA-256 derived key for HS256 compatibility", MIN_HMAC_KEY_BYTES);
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expireSeconds = appProperties.getJwt().getExpireSeconds();
    }

    private byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    public String generate(Long userId, String role, Long orgId) {
        Instant now = Instant.now();
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("uid", userId);
        claims.put("role", role);
        if (orgId != null) claims.put("orgId", orgId);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
