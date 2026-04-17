package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.service.model.ParsedDeviceData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class DeviceDataParserService {
    private final ObjectMapper objectMapper;

    public DeviceDataParserService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedDeviceData parse(String deviceId, DeviceType deviceType, byte[] payload) {
        return switch (deviceType) {
            case HEART_RATE -> parseHeartRate(deviceId, payload);
            case FALL_DETECTOR -> parseFall(deviceId, payload);
            case LOCATOR -> parseLocation(deviceId, payload);
            case HEALTH_MONITOR -> parseHealthMonitor(deviceId, payload);
        };
    }

    private ParsedDeviceData parseHeartRate(String deviceId, byte[] payload) {
        Map<String, Object> map = toJsonMap(payload);
        Integer heartRate = getInt(map, "heartRate");
        long collectTime = getLong(map, "collectTime");
        Integer battery = getInt(map, "battery");
        if (heartRate < 0 || heartRate > 260 || battery < 0 || battery > 100) {
            throw new BizException(1001, "数据格式非法");
        }
        return new ParsedDeviceData(deviceId, DeviceType.HEART_RATE, Instant.ofEpochMilli(collectTime),
                Map.of("heartRate", heartRate, "battery", battery));
    }

    private ParsedDeviceData parseFall(String deviceId, byte[] payload) {
        Map<String, Object> map = toJsonMap(payload);

        if (map.containsKey("params") && map.get("params") instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) map.get("params");
                map = params;
            } catch (ClassCastException e) {
            }
        }

        Map<String, Object> source = map;
        if (map.containsKey("health_data") && map.get("health_data") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> healthData = (Map<String, Object>) map.get("health_data");
            source = healthData;
        }

        Boolean presence = getOptionalBoolean(source, "presence",
                getOptionalBoolean(source, "is_person_present", false));
        Boolean stationary = getOptionalBoolean(source, "stationary", false);
        Boolean fallStatus = getOptionalBoolean(source, "fall_status",
                getOptionalBoolean(source, "is_fall", getOptionalBoolean(source, "fallState", null)));
        Integer heartRate = getOptionalInt(source, "heart_rate_per_min", getOptionalInt(source, "heartRate", null));
        Integer breathRate = getOptionalInt(source, "breath_rate_per_min", getOptionalInt(source, "breathRate", null));
        if (fallStatus == null) {
            throw new BizException(1001, "数据格式非法: fall_status");
        }

        long ts = getOptionalLong(map, "collectTime", getOptionalLong(map, "timestamp", System.currentTimeMillis()));

        Map<String, Object> result = new HashMap<>();
        result.put("presence", presence);
        result.put("is_person_present", presence);
        result.put("stationary", stationary);
        result.put("fall_status", fallStatus);
        result.put("is_fall", fallStatus);
        result.put("fallState", fallStatus ? 1 : 0);
        if (heartRate != null) {
            result.put("heart_rate_per_min", heartRate);
            result.put("heartRate", heartRate);
        }
        if (breathRate != null) {
            result.put("breath_rate_per_min", breathRate);
            result.put("breathRate", breathRate);
        }

        return new ParsedDeviceData(deviceId, DeviceType.FALL_DETECTOR, Instant.ofEpochMilli(ts), result);
    }

    private ParsedDeviceData parseLocation(String deviceId, byte[] payload) {
        Map<String, Object> map = toJsonMap(payload);
        double lat = getDouble(map, "lat");
        double lng = getDouble(map, "lng");
        long collectTime = getLong(map, "collectTime");
        double speed = getDouble(map, "speed");
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180 || speed < 0) {
            throw new BizException(1001, "数据格式非法");
        }
        return new ParsedDeviceData(deviceId, DeviceType.LOCATOR, Instant.ofEpochMilli(collectTime),
                Map.of("lat", lat, "lng", lng, "speed", speed));
    }

    private ParsedDeviceData parseHealthMonitor(String deviceId, byte[] payload) {
        Map<String, Object> map = toJsonMap(payload);
        Map<String, Object> healthData = map;
        if (map.containsKey("health_data") && map.get("health_data") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) map.get("health_data");
            healthData = nested;
        }

        long ts = getOptionalLong(map, "timestamp", getOptionalLong(map, "collectTime", System.currentTimeMillis()));
        Integer heartRate = getOptionalInt(healthData, "heart_rate_per_min", getOptionalInt(healthData, "heartRate", null));
        Integer breathRate = getOptionalInt(healthData, "breath_rate_per_min", getOptionalInt(healthData, "breathRate", null));
        Boolean isFall = getOptionalBoolean(healthData, "is_fall",
                getOptionalBoolean(healthData, "fall_status", getOptionalBoolean(healthData, "fallState", null)));
        Boolean isPersonPresent = getOptionalBoolean(healthData, "is_person_present",
                getOptionalBoolean(healthData, "presence", null));

        if (heartRate == null || breathRate == null || isFall == null || isPersonPresent == null) {
             throw new BizException(1001, "Incomplete health data");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("heart_rate_per_min", heartRate);
        result.put("heartRate", heartRate);
        result.put("breath_rate_per_min", breathRate);
        result.put("breathRate", breathRate);
        result.put("is_fall", isFall);
        result.put("fallState", isFall ? 1 : 0);
        result.put("is_person_present", isPersonPresent);
        
        return new ParsedDeviceData(deviceId, DeviceType.HEALTH_MONITOR, Instant.ofEpochMilli(ts), result);
    }

    private Map<String, Object> toJsonMap(byte[] payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BizException(1001, "数据格式非法");
        }
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new BizException(1001, "数据格式非法");
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new BizException(1001, "数据格式非法");
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new BizException(1001, "数据格式非法");
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new BizException(1001, "数据格式非法: " + key);
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        throw new BizException(1001, "数据格式非法: " + key);
    }

    private Integer getOptionalInt(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long getOptionalLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Boolean getOptionalBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }
}
