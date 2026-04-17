package com.ncf.demo.web.dto;

import com.ncf.demo.domain.AlarmHandleStatus;
import com.ncf.demo.domain.AlarmLevel;

import java.time.Instant;

public record AlarmQueryRequest(
        Long guardianId,
        AlarmLevel alarmLevel,
        AlarmHandleStatus handleStatus,
        Instant startTime,
        Instant endTime
) {
}
