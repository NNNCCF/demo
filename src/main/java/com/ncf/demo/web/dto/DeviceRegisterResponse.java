package com.ncf.demo.web.dto;

import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;

public record DeviceRegisterResponse(
        String deviceId,
        DeviceType deviceType,
        DeviceStatus status
) {
}
