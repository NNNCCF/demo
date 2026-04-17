package com.ncf.demo.web.dto.mini;

public record NurseUserInfo(
        Long id,
        String name,
        String phone,
        String role,
        String institution,
        Long institutionId,
        String avatar,
        boolean verified
) {}
