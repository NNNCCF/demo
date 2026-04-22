package com.ncf.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.common.BizException;
import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.WardRepository;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.web.dto.DeviceControlRequest;
import com.ncf.demo.web.dto.DeviceRegisterRequest;
import com.ncf.demo.web.dto.DeviceRegisterResponse;
import com.ncf.demo.web.dto.DeviceUpdateRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
public class DeviceService {
    private static final String DEVICE_LAST_SEEN_KEY_PREFIX = "device:last-seen:";
    private static final String DEVICE_TYPE_CACHE_PREFIX = "device:type:";
    private static final long OFFLINE_TIMEOUT_MILLIS = 20_000L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceRepository deviceRepository;
    private final ClientUserRepository clientUserRepository;
    private final WardRepository wardRepository;
    private final OrganizationRepository organizationRepository;
    private final EmqxAuthSyncService emqxAuthSyncService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MqttConfig.MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final CommandLogService commandLogService;
    private final PasswordEncoder passwordEncoder;

    public DeviceService(
            DeviceRepository deviceRepository,
            ClientUserRepository clientUserRepository,
            WardRepository wardRepository,
            OrganizationRepository organizationRepository,
            EmqxAuthSyncService emqxAuthSyncService,
            StringRedisTemplate stringRedisTemplate,
            MqttConfig.MqttGateway mqttGateway,
            ObjectMapper objectMapper,
            CommandLogService commandLogService,
            PasswordEncoder passwordEncoder
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
        this.passwordEncoder = passwordEncoder;
    }

    private record DeviceCredentials(String username, String password) {
    }

    @Transactional
    public DeviceRegisterResponse register(DeviceRegisterRequest request) {
        if (deviceRepository.existsById(request.deviceId())) {
            throw new BizException(4001, "Device already exists");
        }

        Device device = new Device();
        device.setDeviceId(request.deviceId());
        device.setDeviceType(request.deviceType());

        if (request.targetId() != null) {
            device.setTargetId(resolveTargetId(request.targetId()));
        }

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
                    .orElseThrow(() -> new BizException(4003, "Guardian not found"));
            if (guardian.getRole() != ClientUserRole.GUARDIAN) {
                throw new BizException(4003, "Guardian not found");
            }
            device.setGuardian(guardian);
        }

        device.setStatus(DeviceStatus.OFFLINE);
        device.setCreatedAt(Instant.now());
        DeviceCredentials credentials = issueMqttCredentials(device);
        device = deviceRepository.save(device);

        if (request.wardIds() != null && !request.wardIds().isEmpty()) {
            List<Ward> wards = wardRepository.findAllById(request.wardIds());
            for (Ward ward : wards) {
                ward.setDevice(device);
            }
            wardRepository.saveAll(wards);
        }

        emqxAuthSyncService.registerDevice(device.getDeviceId());
        evictDeviceTypeCache(device.getDeviceId());
        return new DeviceRegisterResponse(
                device.getDeviceId(),
                device.getDeviceType(),
                device.getStatus(),
                credentials.username(),
                credentials.password()
        );
    }

    @Transactional
    public DeviceRegisterResponse rotateMqttCredentials(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "Device not found"));
        DeviceCredentials credentials = issueMqttCredentials(device);
        deviceRepository.save(device);
        emqxAuthSyncService.kickDevice(deviceId);
        return new DeviceRegisterResponse(
                device.getDeviceId(),
                device.getDeviceType(),
                device.getStatus(),
                credentials.username(),
                credentials.password()
        );
    }

    @Transactional
    public void update(String deviceId, DeviceUpdateRequest request) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(4004, "Device not found"));

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
                    .orElseThrow(() -> new BizException(4003, "Guardian not found"));
            if (guardian.getRole() != ClientUserRole.GUARDIAN) {
                throw new BizException(4003, "Guardian not found");
            }
            device.setGuardian(guardian);
        } else {
            device.setGuardian(null);
        }

        List<Ward> existingWards = device.getWards();
        if (existingWards != null && !existingWards.isEmpty()) {
            for (Ward ward : existingWards) {
                ward.setDevice(null);
            }
            wardRepository.saveAll(existingWards);
        }

        if (request.wardIds() != null && !request.wardIds().isEmpty()) {
            List<Ward> wards = wardRepository.findAllById(request.wardIds());
            for (Ward ward : wards) {
                ward.setDevice(device);
            }
            wardRepository.saveAll(wards);
        }

        deviceRepository.save(device);
        evictDeviceTypeCache(deviceId);
    }

    public List<Device> listAll() {
        Long orgId = SecurityUtil.currentOrgId();
        List<Device> devices;
        if (orgId != null) {
            String orgName = organizationRepository.findById(orgId)
                    .map(Organization::getName)
                    .orElse("");
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

    public DeviceType getDeviceTypeCached(String deviceId) {
        String cached = stringRedisTemplate.opsForValue().get(DEVICE_TYPE_CACHE_PREFIX + deviceId);
        if (cached != null) {
            try {
                return DeviceType.valueOf(cached);
            } catch (Exception ignored) {
            }
        }
        return deviceRepository.findById(deviceId).map(device -> {
            stringRedisTemplate.opsForValue().set(
                    DEVICE_TYPE_CACHE_PREFIX + deviceId,
                    device.getDeviceType().name(),
                    Duration.ofDays(7)
            );
            return device.getDeviceType();
        }).orElse(null);
    }

    public void evictDeviceTypeCache(String deviceId) {
        stringRedisTemplate.delete(DEVICE_TYPE_CACHE_PREFIX + deviceId);
    }

    public boolean existsCached(String deviceId) {
        if (stringRedisTemplate.hasKey(DEVICE_TYPE_CACHE_PREFIX + deviceId)) {
            return true;
        }
        return deviceRepository.existsById(deviceId);
    }

    private Long resolveTargetId(Long targetId) {
        if (wardRepository.existsById(targetId)) {
            return targetId;
        }
        throw new BizException(4002, "Target not found");
    }

    @Transactional
    public void delete(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "Device not found"));

        List<Ward> wards = device.getWards();
        if (wards != null && !wards.isEmpty()) {
            for (Ward ward : wards) {
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
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "Device not found"));
        device.setTargetId(null);
        device.setStatus(DeviceStatus.DISABLED);
        deviceRepository.save(device);
        emqxAuthSyncService.unregisterDevice(deviceId);
        updateOnlineCache(deviceId, false);
    }

    @Transactional
    public void disable(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "Device not found"));
        device.setStatus(DeviceStatus.DISABLED);
        device.setLastOfflineAt(Instant.now());
        deviceRepository.save(device);
        updateOnlineCache(deviceId, false);
        emqxAuthSyncService.kickDevice(deviceId);
    }

    @Transactional
    public void enable(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "Device not found"));
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
            throw new BizException(400, "Control payload is empty");
        }
        try {
            String json = objectMapper.writeValueAsString(request.payload());
            mqttGateway.sendToMqtt(json, "/device/" + deviceId + "/control");
            commandLogService.record(deviceId, json, "SENT");
        } catch (JsonProcessingException e) {
            throw new BizException(500, "Failed to serialize control payload");
        }
    }

    @Transactional
    public void markOnline(String deviceId) {
        touchLastSeen(deviceId);
        updateOnlineCache(deviceId, true);

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "Device not found"));
        if (device.getStatus() == DeviceStatus.ONLINE) {
            return;
        }
        device.setStatus(DeviceStatus.ONLINE);
        device.setLastOnlineAt(Instant.now());
        deviceRepository.save(device);
    }

    @Transactional
    public void markOffline(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "Device not found"));
        device.setStatus(DeviceStatus.OFFLINE);
        device.setLastOfflineAt(Instant.now());
        deviceRepository.save(device);
        updateOnlineCache(deviceId, false);
    }

    public boolean canAuth(String deviceId, String username, String password) {
        return deviceRepository.findById(deviceId)
                .filter(device -> device.getStatus() != DeviceStatus.DISABLED)
                .filter(device -> device.getTargetId() != null)
                .filter(device -> StringUtils.hasText(device.getMqttUsername()))
                .filter(device -> StringUtils.hasText(device.getMqttPasswordHash()))
                .filter(device -> Objects.equals(device.getMqttUsername(), username))
                .filter(device -> StringUtils.hasText(password)
                        && passwordEncoder.matches(password, device.getMqttPasswordHash()))
                .isPresent();
    }

    private DeviceCredentials issueMqttCredentials(Device device) {
        String username = device.getDeviceId();
        String password = generateDevicePassword();
        device.setMqttUsername(username);
        device.setMqttPasswordHash(passwordEncoder.encode(password));
        return new DeviceCredentials(username, password);
    }

    private String generateDevicePassword() {
        byte[] buffer = new byte[24];
        SECURE_RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
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
        List<String> toOffline = new ArrayList<>();
        for (Device device : onlineDevices) {
            String value = stringRedisTemplate.opsForValue().get(DEVICE_LAST_SEEN_KEY_PREFIX + device.getDeviceId());
            long lastSeenAt = 0L;
            if (value != null) {
                try {
                    lastSeenAt = Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                }
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
