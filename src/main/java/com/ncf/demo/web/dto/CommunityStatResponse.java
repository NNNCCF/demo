package com.ncf.demo.web.dto;

public record CommunityStatResponse(
        Long communityId,
        String communityName,
        long deviceCount,
        long alarmCount
) {}
