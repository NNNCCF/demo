package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.AlarmHandleStatus;
import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;
import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.entity.Alarm;
import com.ncf.demo.entity.AlarmRule;
import com.ncf.demo.entity.Device;
import com.ncf.demo.repository.AlarmRepository;
import com.ncf.demo.repository.AlarmRuleRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.service.model.AlarmEvent;
import com.ncf.demo.service.model.ParsedDeviceData;
import com.ncf.demo.web.dto.AlarmRuleUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AlarmService {
    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);
    private final AlarmRepository alarmRepository;
    private final AlarmRuleRepository alarmRuleRepository;
    private final DeviceRepository deviceRepository;
    private final KafkaTemplate<String, AlarmEvent> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public AlarmService(
            AlarmRepository alarmRepository,
            AlarmRuleRepository alarmRuleRepository,
            DeviceRepository deviceRepository,
            @Qualifier("alarmEventKafkaTemplate") ObjectProvider<KafkaTemplate<String, AlarmEvent>> kafkaTemplateProvider,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.alarmRepository = alarmRepository;
        this.alarmRuleRepository = alarmRuleRepository;
        this.deviceRepository = deviceRepository;
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Map<AlarmType, AlarmRule> getAllRules() {
        return alarmRuleRepository.findAll().stream()
                .collect(Collectors.toMap(AlarmRule::getAlarmType, r -> r));
    }

    @Transactional
    public AlarmRule updateRule(AlarmRuleUpdateRequest request) {
        AlarmRule rule = alarmRuleRepository.findByAlarmType(request.alarmType())
                .orElseThrow(() -> new BizException(404, "告警规则不存在"));
        if (request.minValue() != null) {
            rule.setMinValue(request.minValue());
        }
        if (request.maxValue() != null) {
            rule.setMaxValue(request.maxValue());
        }
        if (request.continuousTimes() != null) {
            rule.setContinuousTimes(request.continuousTimes());
        }
        if (request.offlineMinutes() != null) {
            rule.setOfflineMinutes(request.offlineMinutes());
        }
        if (request.alarmLevel() != null) {
            rule.setAlarmLevel(request.alarmLevel());
        }
        return alarmRuleRepository.save(rule);
    }

    public void evaluate(ParsedDeviceData data) {
        if (data.deviceType() == DeviceType.HEART_RATE) {
            safeEvaluate("heart_rate", () -> evaluateHeartRate(data));
        } else if (data.deviceType() == DeviceType.FALL_DETECTOR || data.deviceType() == DeviceType.HEALTH_MONITOR) {
            safeEvaluate("fall", () -> evaluateFall(data));
            if (data.payload().containsKey("heartRate")) {
                safeEvaluate("heart_rate", () -> evaluateHeartRate(data));
            }
            if (data.payload().containsKey("breathRate")) {
                safeEvaluate("breath_rate", () -> evaluateBreathRate(data));
            }
        }
    }

    @Transactional
    public Alarm createManualAlarm(Long targetId, String deviceId, AlarmType alarmType, AlarmLevel alarmLevel, String currentValue) {
        Alarm alarm = new Alarm();
        alarm.setTargetId(targetId);
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setOccurredAt(Instant.now());
        alarm.setCurrentValue(currentValue);
        alarm.setHandleStatus(AlarmHandleStatus.UNHANDLED);
        Alarm saved = alarmRepository.save(alarm);
        AlarmEvent event = new AlarmEvent(
                saved.getId(),
                saved.getTargetId(),
                saved.getDeviceId(),
                saved.getAlarmType(),
                saved.getAlarmLevel(),
                saved.getOccurredAt(),
                saved.getCurrentValue()
        );
        if (kafkaTemplate != null) {
            kafkaTemplate.send("health_alarm", saved.getDeviceId(), event);
        }
        return saved;
    }

    private void safeEvaluate(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("alarm evaluate [{}] failed: {}", name, e.getMessage(), e);
        }
    }

    @Transactional
    public void evaluateHeartRate(ParsedDeviceData data) {
        Optional<AlarmRule> optionalRule = alarmRuleRepository.findByAlarmType(AlarmType.HEART_RATE);
        if (optionalRule.isEmpty()) {
            return;
        }
        AlarmRule rule = optionalRule.get();
        int heartRate = Integer.parseInt(String.valueOf(data.payload().get("heartRate")));
        boolean abnormal = heartRate < rule.getMinValue() || heartRate > rule.getMaxValue();
        String key = "alarm:hr:abnormal:" + data.deviceId();
        long times = abnormal ? stringRedisTemplate.opsForValue().increment(key) : 0L;
        stringRedisTemplate.expire(key, Duration.ofMinutes(15));
        if (!abnormal) {
            stringRedisTemplate.delete(key);
            return;
        }
        int requiredTimes = rule.getContinuousTimes() == null ? 3 : rule.getContinuousTimes();
        if (times >= requiredTimes) {
            createAndPublish(data, AlarmType.HEART_RATE, rule.getAlarmLevel(), String.valueOf(heartRate));
            stringRedisTemplate.delete(key);
        }
    }

    @Transactional
    public void evaluateBreathRate(ParsedDeviceData data) {
        Optional<AlarmRule> optionalRule = alarmRuleRepository.findByAlarmType(AlarmType.BREATH_RATE);
        if (optionalRule.isEmpty()) {
            return;
        }
        AlarmRule rule = optionalRule.get();
        int breathRate = Integer.parseInt(String.valueOf(data.payload().get("breathRate")));
        boolean abnormal = breathRate < rule.getMinValue() || breathRate > rule.getMaxValue();
        String key = "alarm:br:abnormal:" + data.deviceId();
        long times = abnormal ? stringRedisTemplate.opsForValue().increment(key) : 0L;
        stringRedisTemplate.expire(key, Duration.ofMinutes(15));
        if (!abnormal) {
            stringRedisTemplate.delete(key);
            return;
        }
        int requiredTimes = rule.getContinuousTimes() == null ? 3 : rule.getContinuousTimes();
        if (times >= requiredTimes) {
            createAndPublish(data, AlarmType.BREATH_RATE, rule.getAlarmLevel(), String.valueOf(breathRate));
            stringRedisTemplate.delete(key);
        }
    }

    @Transactional
    public void evaluateFall(ParsedDeviceData data) {
        int state = Integer.parseInt(String.valueOf(data.payload().get("fallState")));
        String redisKey = "device:status:fall:" + data.deviceId();
        boolean wasAbnormal = Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey));
        if (state == 1) {
            stringRedisTemplate.opsForValue().set(redisKey, "true", Duration.ofMinutes(30));
        } else {
            stringRedisTemplate.delete(redisKey);
            return;
        }

        if (wasAbnormal) {
            return;
        }
        AlarmLevel level = alarmRuleRepository.findByAlarmType(AlarmType.FALL)
                .map(AlarmRule::getAlarmLevel)
                .orElse(AlarmLevel.EMERGENCY);
        createAndPublish(data, AlarmType.FALL, level, "1");
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void scanOfflineDeviceAlarm() {
        AlarmRule rule = alarmRuleRepository.findByAlarmType(AlarmType.DEVICE_OFFLINE).orElse(null);
        int offlineMinutes = rule == null || rule.getOfflineMinutes() == null ? 30 : rule.getOfflineMinutes();
        AlarmLevel level = rule == null ? AlarmLevel.NORMAL : rule.getAlarmLevel();
        List<Device> offlineDevices = deviceRepository.findByStatus(DeviceStatus.OFFLINE);
        Instant threshold = Instant.now().minus(Duration.ofMinutes(offlineMinutes));
        for (Device device : offlineDevices) {
            if (device.getLastOfflineAt() != null && device.getLastOfflineAt().isBefore(threshold)) {
                // 去重：同一设备的离线告警在 offlineMinutes*2 时长内只触发一次
                String dedupKey = "alarm:offline:sent:" + device.getDeviceId();
                Boolean alreadySent = stringRedisTemplate.hasKey(dedupKey);
                if (Boolean.TRUE.equals(alreadySent)) {
                    continue;
                }
                stringRedisTemplate.opsForValue().set(dedupKey, "1", Duration.ofMinutes(offlineMinutes * 2L));
                ParsedDeviceData data = new ParsedDeviceData(
                        device.getDeviceId(), device.getDeviceType(), Instant.now(), java.util.Map.of()
                );
                createAndPublish(data, AlarmType.DEVICE_OFFLINE, level, "OFFLINE");
            }
        }
    }

    private void createAndPublish(ParsedDeviceData data, AlarmType alarmType, AlarmLevel alarmLevel, String currentValue) {
        Device device = deviceRepository.findById(data.deviceId()).orElse(null);
        if (device == null) {
            return;
        }
        createManualAlarm(device.getTargetId(), data.deviceId(), alarmType, alarmLevel, currentValue);
    }
}
