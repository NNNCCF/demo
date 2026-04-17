package com.ncf.demo.web.dto;

public record MonthlyDeviceStat(
        String month,
        long activeCount,
        long alarmCount
) {}
