package com.ncf.demo.web.dto;

import com.ncf.demo.domain.AlarmHandleStatus;
import jakarta.validation.constraints.NotNull;

public record AlarmHandleRequest(
        @NotNull AlarmHandleStatus handleStatus,
        @NotNull Long handlerId,
        String handleRemark
) {
}
