package com.ncf.demo.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record DeviceControlRequest(
        @NotBlank String commandType,
        @NotNull Map<String, Object> payload
) {
}
