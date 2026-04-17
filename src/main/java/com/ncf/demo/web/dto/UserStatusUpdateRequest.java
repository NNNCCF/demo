package com.ncf.demo.web.dto;

import com.ncf.demo.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull UserStatus status
) {
}
