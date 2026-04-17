package com.ncf.demo.web.dto;

import com.ncf.demo.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank String username,
        @NotNull UserRole role,
        @NotBlank String password
) {
}
