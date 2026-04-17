package com.ncf.demo.web.dto;

public record AlarmReportRow(
        String alarmType,
        Long count,
        String handleRate,
        String majorSource
) {
}
