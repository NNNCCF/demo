package com.ncf.demo.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PublicRegisterRequest(
        @NotBlank String username,
        @NotBlank String password,
        String region,
        String phone,
        @NotBlank String captchaToken,
        @NotBlank String captchaCode
) {
}
