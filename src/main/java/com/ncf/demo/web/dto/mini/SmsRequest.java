package com.ncf.demo.web.dto.mini;

import jakarta.validation.constraints.NotBlank;

public record SmsRequest(
        @NotBlank String phone,
        @NotBlank String scene
) {}
