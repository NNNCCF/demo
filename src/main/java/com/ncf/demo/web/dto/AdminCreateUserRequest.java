package com.ncf.demo.web.dto;

import com.ncf.demo.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminCreateUserRequest(
        Long userId,
        @NotBlank String username,
        @NotBlank String password,
        @NotNull UserRole role,
        String region,
        String phone,
        Long orgId
) {
}
