package com.ncf.demo.web;

import com.ncf.demo.entity.CommandLog;
import com.ncf.demo.entity.NotificationLog;
import com.ncf.demo.repository.CommandLogRepository;
import com.ncf.demo.repository.NotificationLogRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {
    private final CommandLogRepository commandLogRepository;
    private final NotificationLogRepository notificationLogRepository;

    public AdminLogController(CommandLogRepository commandLogRepository, NotificationLogRepository notificationLogRepository) {
        this.commandLogRepository = commandLogRepository;
        this.notificationLogRepository = notificationLogRepository;
    }

    @GetMapping("/commands")
    public ApiResponse<List<CommandLog>> commandLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        return ApiResponse.ok(commandLogRepository.findBySentAtBetween(startTime, endTime));
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationLog>> notificationLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        return ApiResponse.ok(notificationLogRepository.findBySentAtBetween(startTime, endTime));
    }

    /** 操作日志（暂无持久化，返回空列表占位） */
    @GetMapping("/operations")
    public ApiResponse<List<Map<String, Object>>> operationLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        return ApiResponse.ok(List.of());
    }

    /** 登录日志（暂无持久化，返回空列表占位） */
    @GetMapping("/logins")
    public ApiResponse<List<Map<String, Object>>> loginLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        return ApiResponse.ok(List.of());
    }
}
