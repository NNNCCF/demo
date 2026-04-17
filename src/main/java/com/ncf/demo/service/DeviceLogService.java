package com.ncf.demo.service;

import com.ncf.demo.entity.DeviceLog;
import com.ncf.demo.repository.DeviceLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DeviceLogService {
    private final DeviceLogRepository deviceLogRepository;
    private final TdengineService tdengineService;

    public DeviceLogService(DeviceLogRepository deviceLogRepository, TdengineService tdengineService) {
        this.deviceLogRepository = deviceLogRepository;
        this.tdengineService = tdengineService;
    }

    public void log(String deviceId, String type, String content) {
        Instant occurredAt = Instant.now();
        tdengineService.saveDeviceLog(deviceId, type, content, occurredAt);
    }
    
    public List<DeviceLog> getLogs(String deviceId) {
        List<Map<String, Object>> tdRows = tdengineService.queryDeviceLogs(deviceId, 300);
        if (!tdRows.isEmpty()) {
            List<DeviceLog> logs = new ArrayList<>();
            long idSeed = tdRows.size();
            for (Map<String, Object> row : tdRows) {
                DeviceLog log = new DeviceLog();
                log.setId(idSeed--);
                log.setDeviceId(deviceId);
                log.setLogType(String.valueOf(row.getOrDefault("log_type", "")));
                log.setContent(String.valueOf(row.getOrDefault("content", "")));
                log.setOccurredAt(resolveOccurredAt(row.get("ts")));
                logs.add(log);
            }
            return logs;
        }
        return deviceLogRepository.findByDeviceIdOrderByOccurredAtDesc(deviceId);
    }

    @Transactional
    public void clearAll(String deviceId) {
        tdengineService.clearDeviceData(deviceId);
        deviceLogRepository.deleteByDeviceId(deviceId);
    }

    private Instant resolveOccurredAt(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (value != null) {
            try {
                return Instant.parse(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return Instant.now();
    }
}
