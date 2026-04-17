package com.ncf.demo.web;

import com.ncf.demo.service.StatsService;
import com.ncf.demo.web.dto.CommunityStatResponse;
import com.ncf.demo.web.dto.HeartRateTrendPoint;
import com.ncf.demo.web.dto.MonthlyDeviceStat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/monthly")
    public ApiResponse<List<MonthlyDeviceStat>> monthly() {
        return ApiResponse.ok(statsService.monthlyStats());
    }

    @GetMapping("/trend")
    public ApiResponse<List<HeartRateTrendPoint>> trend(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "7") int days
    ) {
        return ApiResponse.ok(statsService.trend(deviceId, days));
    }

    @GetMapping("/community")
    public ApiResponse<List<CommunityStatResponse>> community() {
        return ApiResponse.ok(statsService.communityStats());
    }
}
