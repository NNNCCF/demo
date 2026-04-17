package com.ncf.demo.service;

import com.ncf.demo.service.model.ParsedDeviceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Service
public class HealthDataService {
    private final StringRedisTemplate stringRedisTemplate;
    private final TdengineService tdengineService;
    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;

    public HealthDataService(
            StringRedisTemplate stringRedisTemplate,
            TdengineService tdengineService,
            AlarmService alarmService,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tdengineService = tdengineService;
        this.alarmService = alarmService;
        this.objectMapper = objectMapper;
    }

    public boolean shouldDropAsDuplicate(ParsedDeviceData data) {
        String key = "device:latest:hash:" + data.deviceId();
        String timestampKey = "device:latest:ts:" + data.deviceId();
        String hash = toHash(data.payload());
        String oldHash = stringRedisTemplate.opsForValue().get(key);
        String oldTs = stringRedisTemplate.opsForValue().get(timestampKey);
        long now = data.collectTime().toEpochMilli();
        if (oldHash != null && oldTs != null && oldHash.equals(hash)) {
            long last = Long.parseLong(oldTs);
            if (now - last <= 10000) {
                return true;
            }
        }
        stringRedisTemplate.opsForValue().set(key, hash, Duration.ofHours(24));
        stringRedisTemplate.opsForValue().set(timestampKey, String.valueOf(now), Duration.ofHours(24));
        return false;
    }

    public void persistAndAnalyze(ParsedDeviceData data) {
        tdengineService.save(data);
        alarmService.evaluate(data);
        cacheKeyHealthStats(data);
    }

    private String toHash(Map<String, Object> payload) {
        try {
            return String.valueOf(objectMapper.writeValueAsString(payload).hashCode());
        } catch (Exception e) {
            return String.valueOf(payload.hashCode());
        }
    }

    private void cacheKeyHealthStats(ParsedDeviceData data) {
        if (data.payload().containsKey("heartRate")) {
            Integer heartRate = Integer.parseInt(String.valueOf(data.payload().get("heartRate")));
            LocalDate date = data.collectTime().atZone(ZoneId.systemDefault()).toLocalDate();
            String key = "health:stat:" + data.deviceId() + ":" + date;
            String max = stringRedisTemplate.opsForHash().get(key, "max") == null ? null :
                    String.valueOf(stringRedisTemplate.opsForHash().get(key, "max"));
            String min = stringRedisTemplate.opsForHash().get(key, "min") == null ? null :
                    String.valueOf(stringRedisTemplate.opsForHash().get(key, "min"));
            int maxValue = max == null ? heartRate : Math.max(Integer.parseInt(max), heartRate);
            int minValue = min == null ? heartRate : Math.min(Integer.parseInt(min), heartRate);
            stringRedisTemplate.opsForHash().put(key, "max", String.valueOf(maxValue));
            stringRedisTemplate.opsForHash().put(key, "min", String.valueOf(minValue));
            stringRedisTemplate.expire(key, Duration.ofDays(2));
        }
    }
}
