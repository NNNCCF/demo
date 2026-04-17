package com.ncf.demo.web;

import com.ncf.demo.domain.AlarmHandleStatus;
import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;
import com.ncf.demo.entity.Alarm;
import com.ncf.demo.entity.AlarmRule;
import com.ncf.demo.service.AlarmService;
import com.ncf.demo.service.AlarmManagementService;
import com.ncf.demo.web.dto.AlarmHandleRequest;
import com.ncf.demo.web.dto.AlarmRuleUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {
    private final AlarmManagementService alarmManagementService;
    private final AlarmService alarmService;

    public AlarmController(AlarmManagementService alarmManagementService, AlarmService alarmService) {
        this.alarmManagementService = alarmManagementService;
        this.alarmService = alarmService;
    }

    @GetMapping
    public ApiResponse<List<Alarm>> list(
            @RequestParam(required = false) Long guardianId,
            @RequestParam(required = false) AlarmLevel alarmLevel,
            @RequestParam(required = false) AlarmHandleStatus handleStatus,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        List<Alarm> alarms = alarmManagementService.query(guardianId, startTime, endTime).stream()
                .filter(alarm -> alarmLevel == null || alarm.getAlarmLevel() == alarmLevel)
                .filter(alarm -> handleStatus == null || alarm.getHandleStatus() == handleStatus)
                .toList();
        return ApiResponse.ok(alarms);
    }

    @PatchMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id, @RequestBody @Valid AlarmHandleRequest request) {
        alarmManagementService.handle(id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/handle-all")
    public ApiResponse<Integer> handleAll(@RequestParam Long handlerId) {
        int count = alarmManagementService.handleAll(handlerId);
        return ApiResponse.ok(count);
    }

    @DeleteMapping
    public ApiResponse<Void> clearAll() {
        alarmManagementService.clearAll();
        return ApiResponse.ok(null);
    }

    @GetMapping("/rules")
    public ApiResponse<Map<AlarmType, AlarmRule>> getRules() {
        return ApiResponse.ok(alarmService.getAllRules());
    }

    @PatchMapping("/rules")
    public ApiResponse<AlarmRule> updateRule(@RequestBody @Valid AlarmRuleUpdateRequest request) {
        return ApiResponse.ok(alarmService.updateRule(request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        byte[] data = alarmManagementService.exportReport(startTime, endTime);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename("alarm-report.xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
