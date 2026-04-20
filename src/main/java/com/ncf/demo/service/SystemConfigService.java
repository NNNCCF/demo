package com.ncf.demo.service;

import com.ncf.demo.entity.SystemConfig;
import com.ncf.demo.repository.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemConfigService {
    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);

    private static final Map<String, String> DEFAULTS = Map.of(
            "dataRetentionDays", "30",
            "notifyRetryTimes", "3",
            "rateLimitPerMinute", "100",
            "defaultMapRegion", "110000"
    );

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initDefaults() {
        try {
            DEFAULTS.forEach((key, value) -> {
                if (!repository.existsById(key)) {
                    repository.save(new SystemConfig(key, value));
                }
            });
        } catch (Exception e) {
            log.warn("Skip system config default init for now: {}", e.getMessage());
        }
    }

    public String get(String key, String defaultValue) {
        return repository.findById(key)
                .map(SystemConfig::getValue)
                .orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Map<String, String> getAll() {
        Map<String, String> result = new LinkedHashMap<>();
        DEFAULTS.keySet().forEach(key ->
                result.put(key, get(key, DEFAULTS.get(key)))
        );
        return result;
    }

    public void setAll(Map<String, String> params) {
        params.forEach((key, value) -> {
            if (DEFAULTS.containsKey(key)) {
                SystemConfig config = repository.findById(key)
                        .orElse(new SystemConfig(key, value));
                config.setValue(value);
                config.setUpdatedAt(Instant.now());
                repository.save(config);
            }
        });
    }
}
