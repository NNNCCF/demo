package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.WardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class TargetService {
    private final WardRepository wardRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public TargetService(
            WardRepository wardRepository,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper
    ) {
        this.wardRepository = wardRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getTargetInfo(Long targetId) {
        String key = "target:" + targetId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, Map.class);
            } catch (Exception ignored) {
            }
        }
        Ward ward = wardRepository.findById(targetId)
                .orElseThrow(() -> new BizException(404, "被监护人不存在"));
        Map<String, Object> data = new HashMap<>();
        data.put("name", ward.getName());
        data.put("mobile", ward.getMobile());
        data.put("emergencyPhone", ward.getEmergencyPhone());
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), Duration.ofHours(24));
        } catch (Exception ignored) {
        }
        return data;
    }
}
