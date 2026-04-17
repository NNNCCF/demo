package com.ncf.demo.web.dto.mini;

import jakarta.validation.constraints.NotBlank;

public record GuardianRegisterRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String smsCode,   // OTP 验证暂未启用，非必填
        @NotBlank String password
) {}
