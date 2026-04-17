package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.web.dto.DeviceControlRequest;
import com.ncf.demo.web.dto.DeviceRegisterRequest;
import com.ncf.demo.web.dto.DeviceRegisterResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class DeviceService {
    private static final String DEVICE_LAST_SEEN_KEY_PREFIX = "device:last-seen:";
    private static final long OFFLINE_TIMEOUT_MILLIS = 20_000L;
    private final DeviceRepository deviceRepository;
    private final ClientUserRepository clientUserRepository;
    private final com.ncf.demo.repository.WardRepository wardRepository;
    private final OrganizationRepository organizationRepository;
    private final EmqxAuthSyncService emqxAuthSyncService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MqttConfig.MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final CommandLogService commandLogService;

    public DeviceService(
            DeviceRepository deviceRepository,
            ClientUserRepository clientUserRepository,
            com.ncf.demo.repository.WardRepository wardRepository,
            OrganizationRepository organizationRepository,
            EmqxAuthSyncService emqxAuthSyncService,
            StringRedisTemplate stringRedisTemplate,
            MqttConfig.MqttGateway mqttGateway,
            ObjectMapper objectMapper,
            CommandLogService commandLogService
    ) {
        this.deviceRepository = deviceRepository;
        this.clientUserRepository = clientUserRepository;
        this.wardRepository = wardRepository;
        this.organizationRepository = organizationRepository;
        this.emqxAuthSyncService = emqxAuthSyncService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mqttGateway = mqttGateway;
        this.objectMapper = objectMapper;
        this.commandLogService = commandLogService;
    }

    @Transactional
    public DeviceRegisterResponse register(DeviceRegisterRequest request) {
        if (deviceRepository.existsById(request.deviceId())) {
            throw new BizException(4001, "设备号已注册");
        }

        Device device = new Device();
        device.setDeviceId(request.deviceId());
        device.setDeviceType(request.deviceType());
        
        if (request.targetId() != null) {
            device.setTargetId(resolveTargetId(request.targetId()));
        }

        // Set new fields
        device.setAddress(request.address());
        device.setHomeLocation(request.homeLocation());
        device.setRoomNumber(request.roomNumber());
        device.setMedicalInstitution(request.medicalInstitution());
            device.setPropertyManagement(request.propertyManagement());
            device.setLatitude(request.latitude());
            device.setLongitude(request.longitude());
            device.setFamilyId(request.familyId());

            if (request.guardianId() != null) {
            ClientUser guardian = clientUserRepository.findById(request.guardianId())
                    .orElseThrow(() -> new BizException(4003, "监护人不存在"));
            if (guardian.getRole() != ClientUserRole.GUARDIAN) {
                throw new BizException(4003, "监护人不存在");
            }
            device.setGuardian(guardian);
        }

        device.setStatus(DeviceStatus.OFFLINE);
        device.setCreatedAt(Instant.now());
        device = deviceRepository.save(device);

        if (request.wardIds() != null && !request.wardIds().isEmpty()) {
            List<com.ncf.demo.entity.Ward> wards = wardRepository.findAllById(request.wardIds());
            for (com.ncf.demo.entity.Ward ward : wards) {
                ward.setDevice(device);
            }
            wardRepository.saveAll(wards);
        }

        emqxAuthSyncService.registerDevice(device.getDeviceId());
        evictDeviceTypeCache(device.getDeviceId());
        return new DeviceRegisterResponse(device.getDeviceId(), device.getDeviceType(), device.getStatus());
    }

    @Transactional
    public void update(String deviceId, com.ncf.demo.web.dto.DeviceUpdateRequest request) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(4004, "设备不存在"));

        device.setDeviceType(request.deviceType());
        device.setAddress(request.address());
        device.setHomeLocation(request.homeLocation());
        device.setRoomNumber(request.roomNumber());
        device.setMedicalInstitution(request.medicalInstitution());
        device.setPropertyManagement(request.propertyManagement());
        device.setLatitude(request.latitude());
        device.setLongitude(request.longitude());
        if (request.familyId() != null) {
            device.setFamilyId(request.familyId());
        }

        if (request.targetId() != null) {
            device.setTargetId(resolveTargetId(request.targetId()));
        }

        if (request.guardianId() != null) {
            ClientUser guardian = clientUserRepository.findById(request.guardianId())
                    .orElseThrow(() -> new BizException(4003, "监护人不存在"));
            if (guardian.getRole() != ClientUserRole.GUARDIAN) {
                throw new BizException(4003, "监护人不存在");
            }
            device.setGuardian(guardian);
        } else {
            device.setGuardian(null);
        }

        List<com.ncf.demo.entity.Ward> existingWards = device.getWards();
        if (existingWards != null && !existingWards.isEmpty()) {
            for (com.ncf.demo.entity.Ward w : existingWards) {
                w.setDevice(null);
            }
            wardRepository.saveAll(existingWards);
        }

        if (request.wardIds() != null && !request.wardIds().isEmpty()) {
            List<com.ncf.demo.entity.Ward> wards = wardRepository.findAllById(request.wardIds());
            for (com.ncf.demo.entity.Ward ward : wards) {
                ward.setDevice(device);
            }
            wardRepository.saveAll(wards);
        }

        deviceRepository.save(device);
        evictDeviceTypeCache(deviceId);
    }

    public List<Device> listAll() {
        Long orgId = com.ncf.demo.security.SecurityUtil.currentOrgId();
        List<Device> devices;
        if (orgId != null) {
            String orgName = organizationRepository.findById(orgId)
                    .map(Organization::getName).orElse("");
            devices = deviceRepository.findByOrgIdOrOrgName(orgId, orgName);
        } else {
            devices = deviceRepository.findAll();
        }
        for (Device device : devices) {
            String val = stringRedisTemplate.opsForValue().get("device:status:fall:" + device.getDeviceId());
            device.setIsFall("true".equals(val));
        }
        return devices;
    }

    private static final String DEVICE_TYPE_CACHE_PREFIX = "device:type:";

    /** 查设备类型，优先走 Redis 缓存（命中率极高，避免每条消息都查 MySQL）。*/
    public com.ncf.demo.domain.DeviceType getDeviceTypeCached(String deviceId) {
        String cached = stringRedisTemplate.opsForValue().get(DEVICE_TYPE_CACHE_PREFIX + deviceId);
        if (cached != null) {
            try { return com.ncf.demo.domain.DeviceType.valueOf(cached); } catch (Exception ignored) {}
        }
        return deviceRepository.findById(deviceId).map(d -> {
            stringRedisTemplate.opsForValue().set(
                DEVICE_TYPE_CACHE_PREFIX + deviceId,
                d.getDeviceType().name(),
                Duration.ofDays(7)
            );
            return d.getDeviceType();
        }).orElse(null);
    }

    /** 设备注册/更新/删除时使缓存失效。*/
    public void evictDeviceTypeCache(String deviceId) {
        stringRedisTemplate.delete(DEVICE_TYPE_CACHE_PREFIX + deviceId);
    }

    /** 检查设备是否存在（先查 Redis 缓存再查 MySQL）。*/
    public boolean existsCached(String deviceId) {
        if (stringRedisTemplate.hasKey(DEVICE_TYPE_CACHE_PREFIX + deviceId)) return true;
        return deviceRepository.existsById(deviceId);
    }

    private Long resolveTargetId(Long targetId) {
        if (wardRepository.existsById(targetId)) {
            return targetId;
        }
        throw new BizException(4002, "被监护人不存在");
    }

    @Transactional
    public void delete(String deviceId) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new BizException(404, "设备不存在"));
        
        // Remove ward associations
        List<com.ncf.demo.entity.Ward> wards = device.getWards();
        if (wards != null && !wards.isEmpty()) {
            for (com.ncf.demo.entity.Ward ward : wards) {
                ward.setDevice(null);
            }
            wardRepository.saveAll(wards);
        }

        deviceRepository.delete(device);
        updateOnlineCache(deviceId, false);
        evictDeviceTypeCache(deviceId);
        emqxAuthSyncService.unregisterDevice(deviceId);
    }

    @Transactional
    public void unbind(String deviceId) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new BizException(404, "设备不存在"));
        device.setTargetId(null);
        device.setStatus(DeviceStatus.DISABLED);
        deviceRepository.save(device);
        emqxAuthSyncService.unregisterDevice(deviceId);
        updateOnlineCache(deviceId, false);
    }

    @Transactional
    public void disable(String deviceId) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new BizException(404, "设备不存在"));
        device.setStatus(DeviceStatus.DISABLED);
        device.setLastOfflineAt(Instant.now());
        deviceRepository.save(device);
        updateOnlineCache(deviceId, false);
        emqxAuthSyncService.kickDevice(deviceId);
    }

    @Transactional
    public void enable(String deviceId) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new BizException(404, "设备不存在"));
        device.setStatus(DeviceStatus.OFFLINE);
        deviceRepository.save(device);
        updateOnlineCache(deviceId, false);
    }

    public void sendReset(String deviceId) {
        mqttGateway.sendToMqtt("{\"action\":\"factory_reset\"}", "/device/" + deviceId + "/reset");
        commandLogService.record(deviceId, "{\"action\":\"factory_reset\"}", "SENT");
    }

    public void sendControl(String deviceId, DeviceControlRequest request) {
        if (request.payload().isEmpty()) {
            throw new BizException(400, "控制指令为空");
        }
        try {
            String json = objectMapper.writeValueAsString(request.payload());
            mqttGateway.sendToMqtt(json, "/device/" + deviceId + "/control");
            commandLogService.record(deviceId, json, "SENT");
        } catch (JsonProcessingException e) {
            throw new BizException(500, "指令序列化失败");
        }
    }

    @Transactional
    public void markOnline(String deviceId) {
        // 先刷 Redis，保证 last-seen 不超时
        touchLastSeen(deviceId);
        updateOnlineCache(deviceId, true);
        // 已是 ONLINE 状态则跳过 DB 写入，避免每条消息都触发事务
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new BizException(404, "设备不存在"));
        if (device.getStatus() == DeviceStatus.ONLINE) {
            return;
        }
        device.setStatus(DeviceStatus.ONLINE);
        device.setLastOnlineAt(Instant.now());
        deviceRepository.save(device);
    }

    @Transactional
    public void markOffline(String deviceId) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new BizException(404, "设备不存在"));
        device.setStatus(DeviceStatus.OFFLINE);
        device.setLastOfflineAt(Instant.now());
        deviceRepository.save(device);
        updateOnlineCache(deviceId, false);
    }

    public boolean canAuth(String deviceId) {
        return deviceRepository.findById(deviceId)
                .filter(device -> device.getStatus() != DeviceStatus.DISABLED && device.getTargetId() != null)
                .isPresent();
    }

    public void updateOnlineCache(String deviceId, boolean online) {
        stringRedisTemplate.opsForValue()
                .set("device:online:" + deviceId, online ? "1" : "0", Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue()
                .set("device:status:sync:" + deviceId, online ? "1" : "0", Duration.ofHours(24));
    }

    public void touchLastSeen(String deviceId) {
        stringRedisTemplate.opsForValue()
                .set(DEVICE_LAST_SEEN_KEY_PREFIX + deviceId, String.valueOf(System.currentTimeMillis()), Duration.ofMinutes(2));
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void markOfflineIfNoRecentData() {
        long now = System.currentTimeMillis();
        List<Device> onlineDevices = deviceRepository.findByStatus(DeviceStatus.ONLINE);
        List<String> toOffline = new java.util.ArrayList<>();
        for (Device device : onlineDevices) {
            String value = stringRedisTemplate.opsForValue().get(DEVICE_LAST_SEEN_KEY_PREFIX + device.getDeviceId());
            long lastSeenAt = 0L;
            if (value != null) {
                try { lastSeenAt = Long.parseLong(value); } catch (NumberFormatException ignored) {}
            }
            if (lastSeenAt == 0L || now - lastSeenAt > OFFLINE_TIMEOUT_MILLIS) {
                toOffline.add(device.getDeviceId());
                updateOnlineCache(device.getDeviceId(), false);
            }
        }
        if (!toOffline.isEmpty()) {
            deviceRepository.markOfflineBatch(toOffline, Instant.now());
        }
    }
}
