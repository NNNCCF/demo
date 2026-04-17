package com.ncf.demo.web.dto.mini;

public record UnifiedLoginResponse(
        String token,
        String role,            // "caregiver" | "guardian"
        UnifiedUserInfo userInfo
) {}
