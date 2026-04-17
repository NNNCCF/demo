package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.entity.Device;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.web.dto.DataHistoryResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class DataQueryService {
    private final DeviceRepository deviceRepository;
    private final TdengineService tdengineService;

    public DataQueryService(DeviceRepository deviceRepository, TdengineService tdengineService) {
        this.deviceRepository = deviceRepository;
        this.tdengineService = tdengineService;
    }

    public DataHistoryResponse queryHistory(String deviceId, Instant start, Instant end) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new BizException(404, "设备不存在"));
        List<Map<String, Object>> list;
        if (device.getDeviceType() == DeviceType.FALL_DETECTOR) {
            list = tdengineService.queryDeviceData("health_monitor", deviceId, start, end);
            if (list.isEmpty()) {
                list = tdengineService.queryDeviceData("fall_status", deviceId, start, end);
            }
        } else {
            String prefix = resolvePrefix(device.getDeviceType());
            list = tdengineService.queryDeviceData(prefix, deviceId, start, end);
        }
        long avg = avgInterval(list);
        return new DataHistoryResponse(list, list.size(), avg);
    }

    private String resolvePrefix(DeviceType deviceType) {
        if (deviceType == DeviceType.HEART_RATE) {
            return "heart_rate";
        }
        if (deviceType == DeviceType.FALL_DETECTOR) {
            return "fall_status";
        }
        if (deviceType == DeviceType.HEALTH_MONITOR) {
            return "health_monitor";
        }
        return "location";
    }

    private long avgInterval(List<Map<String, Object>> rows) {
        if (rows.size() < 2) {
            return 0;
        }
        long total = 0L;
        for (int i = 1; i < rows.size(); i++) {
            Instant prev = castTs(rows.get(i - 1).get("ts"));
            Instant curr = castTs(rows.get(i).get("ts"));
            total += Duration.between(prev, curr).getSeconds();
        }
        return total / (rows.size() - 1);
    }

    private Instant castTs(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        return Instant.parse(String.valueOf(value));
    }
}
