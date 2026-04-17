package com.ncf.demo.service.model;

import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;

import java.time.Instant;

public record AlarmEvent(
        Long alarmId,
        Long targetId,
        String deviceId,
        AlarmType alarmType,
        AlarmLevel alarmLevel,
        Instant occurredAt,
        String currentValue
) {
}
