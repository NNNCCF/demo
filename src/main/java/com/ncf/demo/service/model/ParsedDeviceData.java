package com.ncf.demo.service.model;

import com.ncf.demo.domain.DeviceType;

import java.time.Instant;
import java.util.Map;

public record ParsedDeviceData(
        String deviceId,
        DeviceType deviceType,
        Instant collectTime,
        Map<String, Object> payload
) {
}
