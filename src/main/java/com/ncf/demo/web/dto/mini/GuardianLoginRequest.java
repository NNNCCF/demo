package com.ncf.demo.web.dto.mini;

import jakarta.validation.constraints.NotBlank;

public record GuardianLoginRequest(
        @NotBlank String phone,
        @NotBlank String password
) {}
