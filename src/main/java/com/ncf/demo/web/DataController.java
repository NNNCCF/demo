package com.ncf.demo.web;

import com.ncf.demo.service.DataQueryService;
import com.ncf.demo.service.DeviceLogService;
import com.ncf.demo.web.dto.DataHistoryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/data")
public class DataController {
    private final DataQueryService dataQueryService;
    private final DeviceLogService deviceLogService;

    public DataController(DataQueryService dataQueryService, DeviceLogService deviceLogService) {
        this.dataQueryService = dataQueryService;
        this.deviceLogService = deviceLogService;
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> clearData(@PathVariable String deviceId) {
        deviceLogService.clearAll(deviceId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/history")
    public ApiResponse<DataHistoryResponse> history(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        return ApiResponse.ok(dataQueryService.queryHistory(deviceId, startTime, endTime));
    }
}
