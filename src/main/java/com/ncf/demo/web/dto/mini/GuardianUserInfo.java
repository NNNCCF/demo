package com.ncf.demo.web.dto.mini;

public record GuardianUserInfo(
        Long id,
        String name,
        String phone,
        String role,
        String avatar,
        String address
) {}
