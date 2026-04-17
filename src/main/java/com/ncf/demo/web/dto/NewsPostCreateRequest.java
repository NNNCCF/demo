package com.ncf.demo.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record NewsPostCreateRequest(
        @NotBlank String title,
        String content,
        String visibility,
        Long publisherId,
        String publisherName,
        Instant publishTime,
        List<String> attachments
) {}
