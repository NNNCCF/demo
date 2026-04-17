package com.ncf.demo.web.dto.mini;

import jakarta.validation.constraints.NotBlank;

public record NurseLoginRequest(
        @NotBlank String phone,
        @NotBlank String password
) {}
