package com.ncf.demo.web.dto;

public record AuthCaptchaResponse(
        String captchaToken,
        String imageUrl,
        long expireSeconds,
        long cooldownSeconds
) {
}
