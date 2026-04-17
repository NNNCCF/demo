package com.ncf.demo.web.dto;

import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.Gender;

import java.time.Instant;
import java.util.List;

public record ClientUserListItemResponse(
    Long id,
    String mobile,
    String name,
    ClientUserRole role,
    Instant createdAt,
    Instant updatedAt,
    List<ClientUserDeviceResponse> devices,
    String sourceType,
    Gender gender,
    Instant birthday,
    Double height,
    Double weight,
    String wardRole,
    String chronicDisease,
    String remark,
    Long orgId
) {
}
