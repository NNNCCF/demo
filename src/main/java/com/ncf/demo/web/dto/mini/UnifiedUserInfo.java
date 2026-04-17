package com.ncf.demo.web.dto.mini;

public record UnifiedUserInfo(
        Long id,
        String name,
        String phone,
        String role,            // "caregiver" | "guardian"
        Long institutionId,
        String institutionName
) {}
