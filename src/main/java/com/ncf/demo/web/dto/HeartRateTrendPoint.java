package com.ncf.demo.web.dto;

public record HeartRateTrendPoint(
        String time,
        double avgRate,
        double maxRate,
        double minRate
) {}
