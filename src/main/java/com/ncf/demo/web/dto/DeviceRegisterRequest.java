package com.ncf.demo.web.dto;

import com.ncf.demo.domain.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceRegisterRequest(
        @NotBlank String deviceId,
        @NotNull DeviceType deviceType,
        Long targetId,
        String address,
        String homeLocation,
        String roomNumber,
        String medicalInstitution,
        String propertyManagement,
        Long guardianId,
        java.util.List<Long> wardIds,
        Double latitude,
        Double longitude,
        Long familyId
) {
}
