package com.ncf.demo.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceUnbindRequest(
        @NotBlank String deviceId
) {
}
