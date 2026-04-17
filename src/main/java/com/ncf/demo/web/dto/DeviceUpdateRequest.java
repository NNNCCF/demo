package com.ncf.demo.web.dto;

import com.ncf.demo.domain.DeviceType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DeviceUpdateRequest(
    @NotNull DeviceType deviceType,
    String address,
    String homeLocation,
    String roomNumber,
    String medicalInstitution,
    String propertyManagement,
    Long targetId,
    Long guardianId,
    List<Long> wardIds,
    Double latitude,
    Double longitude,
    Long familyId
) {}
