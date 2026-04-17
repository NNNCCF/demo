package com.ncf.demo.service;

import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.NotificationChannel;
import com.ncf.demo.entity.NotificationLog;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.AlarmRepository;
import com.ncf.demo.repository.NotificationLogRepository;
import com.ncf.demo.repository.WardRepository;
import com.ncf.demo.service.model.AlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    /** 同一设备+告警类型的通知冷却时间：10 分钟内不重复发送 */
    private static final Duration NOTIFY_COOLDOWN = Duration.ofMinutes(10);

    private final NotificationLogRepository notificationLogRepository;
    private final AlarmRepository alarmRepository;
    private final WardRepository wardRepository;
    private final SystemConfigService systemConfigService;
    private final StringRedisTemplate stringRedisTemplate;

    public NotificationService(
            NotificationLogRepository notificationLogRepository,
            AlarmRepository alarmRepository,
            WardRepository wardRepository,
            SystemConfigService systemConfigService,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.notificationLogRepository = notificationLogRepository;
        this.alarmRepository = alarmRepository;
        this.wardRepository = wardRepository;
        this.systemConfigService = systemConfigService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @KafkaListener(topics = "health_alarm", groupId = "health-notifier")
    public void onAlarm(AlarmEvent event) {
        // 冷却去重：同设备+告警类型 10 分钟内只通知一次
        String cooldownKey = "notify:cooldown:" + event.deviceId() + ":" + event.alarmType();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            log.debug("[通知] 冷却中，跳过 — deviceId={}, type={}", event.deviceId(), event.alarmType());
            return;
        }
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", NOTIFY_COOLDOWN);

        Ward ward = wardRepository.findById(event.targetId() != null ? event.targetId() : -1L).orElse(null);
        if (ward == null) {
            return;
        }
        if (event.alarmLevel() == AlarmLevel.EMERGENCY) {
            if (ward.getEmergencyPhone() != null && !ward.getEmergencyPhone().isBlank()) {
                sendWithRetry(event, NotificationChannel.SMS, ward.getEmergencyPhone());
            }
        }
        if (ward.getDevice() != null && ward.getDevice().getGuardian() != null) {
            Long guardianId = ward.getDevice().getGuardian().getId();
            sendWithRetry(event, NotificationChannel.MINI_PROGRAM, guardianId.toString());
        }
    }

    /** 每天凌晨 3 点清理过期通知日志和已处理的告警。*/
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredLogs() {
        int retentionDays = systemConfigService.getInt("dataRetentionDays", 30);
        Instant before = Instant.now().minus(Duration.ofDays(retentionDays));

        int notifDeleted = notificationLogRepository.deleteBysentAtBefore(before);
        log.info("[清理] 删除 {} 条过期通知日志（{}天前）", notifDeleted, retentionDays);

        // 已处理的告警保留同样天数；未处理的告警保留两倍时间
        int alarmDeleted = alarmRepository.deleteHandledBefore(before);
        int oldUnhandledDeleted = alarmRepository.deleteAllBefore(
                Instant.now().minus(Duration.ofDays(retentionDays * 2L)));
        log.info("[清理] 删除 {} 条已处理告警，{} 条超期未处理告警", alarmDeleted, oldUnhandledDeleted);
    }

    private void sendWithRetry(AlarmEvent event, NotificationChannel channel, String receiver) {
        int maxRetries = systemConfigService.getInt("notifyRetryTimes", 3);
        NotificationLog notifLog = saveLog(event.alarmId(), channel, receiver, "PENDING", 0);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                doSend(event, channel, receiver);
                notifLog.setStatus("SENT");
                notifLog.setRetryCount(attempt - 1);
                notificationLogRepository.save(notifLog);
                return;
            } catch (Exception e) {
                log.warn("[通知] 第 {}/{} 次发送失败 — channel={}, receiver={}, error={}",
                        attempt, maxRetries, channel, receiver, e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep(1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        notifLog.setStatus("FAILED");
        notifLog.setRetryCount(maxRetries);
        notificationLogRepository.save(notifLog);
    }

    private void doSend(AlarmEvent event, NotificationChannel channel, String receiver) {
        if (channel == NotificationChannel.SMS) {
            log.warn("[SMS STUB] 需接入短信网关 — alarmId={}, phone={}, level={}", event.alarmId(), receiver, event.alarmLevel());
        } else {
            log.warn("[MINI-PROGRAM STUB] 需接入微信推送 — alarmId={}, guardianId={}", event.alarmId(), receiver);
        }
    }

    private NotificationLog saveLog(Long alarmId, NotificationChannel channel, String receiver, String status, int retryCount) {
        NotificationLog notifLog = new NotificationLog();
        notifLog.setAlarmId(alarmId);
        notifLog.setChannel(channel);
        notifLog.setReceiver(receiver);
        notifLog.setSentAt(Instant.now());
        notifLog.setStatus(status);
        notifLog.setRetryCount(retryCount);
        return notificationLogRepository.save(notifLog);
    }
}
