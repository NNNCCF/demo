package com.ncf.demo.web.dto;

import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;
import jakarta.validation.constraints.NotNull;

public record AlarmRuleUpdateRequest(
        @NotNull AlarmType alarmType,
        Integer minValue,
        Integer maxValue,
        Integer continuousTimes,
        Integer offlineMinutes,
        AlarmLevel alarmLevel
) {
}
