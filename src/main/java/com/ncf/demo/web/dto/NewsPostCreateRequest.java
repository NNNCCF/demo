package com.ncf.demo.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record NewsPostCreateRequest(
        @NotBlank String title,
        String content,
        String visibility,
        String category,
        String targetScope,
        Long targetFamilyId,
        String targetFamilyName,
        Long publisherId,
        String publisherName,
        Instant publishTime,
        List<String> attachments
) {}
