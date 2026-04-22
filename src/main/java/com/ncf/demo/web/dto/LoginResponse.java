package com.ncf.demo.web.dto;

public record LoginResponse(
        Long userId,
        String username,
        String role,
        Long orgId,
        String orgType,
        String token,
        Long expireSeconds,
        boolean forcePasswordChange
) {
}
