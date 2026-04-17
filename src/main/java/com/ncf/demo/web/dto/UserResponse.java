package com.ncf.demo.web.dto;

import com.ncf.demo.domain.UserRole;
import com.ncf.demo.domain.UserStatus;

public record UserResponse(
        Long userId,
        String username,
        UserRole role,
        String region,
        String phone,
        UserStatus status,
        Long orgId
) {
}
