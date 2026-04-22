package com.ncf.demo.web.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedbackSaveRequest(
        Long submitterId,
        String submitterRole,
        @NotBlank String type,
        @NotBlank String content,
        String status
) {
}
