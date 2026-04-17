package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.service.model.ParsedDeviceData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class MqttMessageService {
    private static final Logger log = LoggerFactory.getLogger(MqttMessageService.class);
    private final ThreadPoolTaskExecutor mqttMessageExecutor;
    private final DeviceDataParserService deviceDataParserService;
    private final HealthDataService healthDataService;
    private final DeviceService deviceService;
    private final MqttConfig.MqttGateway mqttGateway;
    private final DeviceLogService deviceLogService;
    private final ObjectMapper objectMapper;

    public MqttMessageService(
            ThreadPoolTaskExecutor mqttMessageExecutor,
            DeviceDataParserService deviceDataParserService,
            HealthDataService healthDataService,
            DeviceService deviceService,
            MqttConfig.MqttGateway mqttGateway,
            DeviceLogService deviceLogService,
            ObjectMapper objectMapper
    ) {
        this.mqttMessageExecutor = mqttMessageExecutor;
        this.deviceDataParserService = deviceDataParserService;
        this.healthDataService = healthDataService;
        this.deviceService = deviceService;
        this.mqttGateway = mqttGateway;
        this.deviceLogService = deviceLogService;
        this.objectMapper = objectMapper;
    }

    public void handleIncoming(Message<?> message) {
        mqttMessageExecutor.execute(() -> doHandle(message));
    }

    private void doHandle(Message<?> message) {
        String topic = String.valueOf(message.getHeaders().get("mqtt_receivedTopic"));
        byte[] payload = toPayloadBytes(message.getPayload());
        String payloadText = new String(payload, StandardCharsets.UTF_8);
        try {
            if (topic.startsWith("$SYS/brokers/") && topic.contains("/disconnected")) {
                handleDisconnectTopic(topic);
                return;
            }
            String topicDeviceId = resolveDeviceId(topic);
            String payloadDeviceId = parseDeviceIdFromPayload(payloadText);
            String deviceIdentifier = topicDeviceId;
            if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
                deviceIdentifier = payloadDeviceId;
            }
            if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
                throw new BizException(1001, "数据格式非法");
            }
            
            // 查设备类型（走 Redis 缓存，避免每条消息都查 MySQL）
            DeviceType deviceType = deviceService.getDeviceTypeCached(deviceIdentifier);
            if (deviceType == null && payloadDeviceId != null && !payloadDeviceId.isBlank() && !payloadDeviceId.equals(deviceIdentifier)) {
                DeviceType fallbackType = deviceService.getDeviceTypeCached(payloadDeviceId);
                if (fallbackType != null) {
                    log.warn("topic deviceId {} not found, fallback to payload device_id {}", deviceIdentifier, payloadDeviceId);
                    deviceType = fallbackType;
                    deviceIdentifier = payloadDeviceId;
                }
            }

            try {
                deviceLogService.log(deviceIdentifier, "属性上报", payloadText);
            } catch (Exception e) {
                log.error("Failed to save device log", e);
            }

            if (deviceType == null) {
                throw new BizException(404, "设备不存在");
            }

            deviceService.markOnline(deviceIdentifier);
            ParsedDeviceData data = deviceDataParserService.parse(deviceIdentifier, deviceType, payload);
            if (!healthDataService.shouldDropAsDuplicate(data)) {
                healthDataService.persistAndAnalyze(data);
            }
        } catch (BizException e) {
            String deviceIdentifier = tryResolveDeviceId(topic);
            if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
                deviceIdentifier = parseDeviceIdFromPayload(payloadText);
            }
            log.warn("invalid device message, topic={}, error={}", topic, e.getMessage());
            if (deviceIdentifier != null) {
                String msg = e.getMessage() == null || e.getMessage().isBlank() ? "数据格式非法" : e.getMessage();
                mqttGateway.sendToMqtt("{\"code\":1001,\"msg\":\"" + msg + "\"}", "/device/" + deviceIdentifier + "/error");
            }
        } catch (Exception e) {
            log.error("mqtt message consume failed, topic={}", topic, e);
        }
    }

    private byte[] toPayloadBytes(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        if (payload instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        return String.valueOf(payload).getBytes(StandardCharsets.UTF_8);
    }

    private void handleDisconnectTopic(String topic) {
        String[] arr = topic.split("/");
        String clientId = arr[arr.length - 2];
        deviceService.markOffline(clientId);
    }

    private String resolveDeviceId(String topic) {
        String[] arr = topic.split("/");
        if (arr.length < 3 || !"device".equals(arr[1])) {
            return null;
        }
        return arr[2];
    }

    private String tryResolveDeviceId(String topic) {
        try {
            return resolveDeviceId(topic);
        } catch (Exception e) {
            return null;
        }
    }

    private String parseDeviceIdFromPayload(String payloadText) {
        try {
            Map<String, Object> map = objectMapper.readValue(payloadText, new TypeReference<>() {
            });
            Object direct = map.get("device_id");
            if (direct != null && !String.valueOf(direct).isBlank()) {
                return String.valueOf(direct);
            }
            Object nestedObj = map.get("params");
            if (nestedObj instanceof Map<?, ?> nestedMap) {
                Object nestedId = nestedMap.get("device_id");
                if (nestedId != null && !String.valueOf(nestedId).isBlank()) {
                    return String.valueOf(nestedId);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
