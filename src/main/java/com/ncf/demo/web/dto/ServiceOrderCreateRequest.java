package com.ncf.demo.web.dto;

import com.ncf.demo.domain.ServiceOrderType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ServiceOrderCreateRequest(
        @NotNull ServiceOrderType orderType,
        @NotNull Long targetId,
        @NotNull Instant appointmentTime
) {
}
