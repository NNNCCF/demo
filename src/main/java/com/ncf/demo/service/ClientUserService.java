package com.ncf.demo.service;

import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.Gender;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.WardRepository;
import com.ncf.demo.web.dto.ClientUserRequest;
import com.ncf.demo.web.dto.ClientUserDeviceResponse;
import com.ncf.demo.web.dto.ClientUserListItemResponse;
import com.ncf.demo.common.BizException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ClientUserService {

    private final ClientUserRepository clientUserRepository;
    private final DeviceRepository deviceRepository;
    private final WardRepository wardRepository;
    private final FamilyRepository familyRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public ClientUserService(
        ClientUserRepository clientUserRepository,
        DeviceRepository deviceRepository,
        WardRepository wardRepository,
        FamilyRepository familyRepository,
        OrganizationRepository organizationRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.clientUserRepository = clientUserRepository;
        this.deviceRepository = deviceRepository;
        this.wardRepository = wardRepository;
        this.familyRepository = familyRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<ClientUserListItemResponse> findAll() {
        Long orgId = com.ncf.demo.security.SecurityUtil.currentOrgId();
        List<ClientUserListItemResponse> items = new ArrayList<>();

        Iterable<ClientUser> users = orgId != null
                ? clientUserRepository.findByOrgId(orgId)
                : clientUserRepository.findAll();
        for (ClientUser user : users) {
            List<ClientUserDeviceResponse> devices = deviceRepository.findByGuardianId(user.getId()).stream()
                    .map(d -> new ClientUserDeviceResponse(d.getDeviceId()))
                    .collect(Collectors.toList());
            items.add(new ClientUserListItemResponse(
                user.getId(),
                user.getMobile(),
                user.getName(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                devices,
                "CLIENT_USER",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                user.getOrgId()
            ));
        }

        String orgName = orgId != null
                ? organizationRepository.findById(orgId).map(Organization::getName).orElse("")
                : null;
        Iterable<Ward> wards = orgId != null
                ? wardRepository.findByOrgIdOrOrgName(orgId, orgName)
                : wardRepository.findAll();
        for (Ward ward : wards) {
            List<ClientUserDeviceResponse> devices = ward.getDevice() == null
                    ? List.of()
                    : List.of(new ClientUserDeviceResponse(ward.getDevice().getDeviceId()));
            items.add(new ClientUserListItemResponse(
                ward.getMemberId(),
                ward.getMobile(),
                ward.getName(),
                ClientUserRole.WARD,
                null,
                null,
                devices,
                "WARD",
                ward.getGender(),
                ward.getBirthday(),
                ward.getHeight(),
                ward.getWeight(),
                ward.getRole(),
                ward.getChronicDisease(),
                ward.getRemark(),
                null
            ));
        }

        items.sort(Comparator.comparing(ClientUserListItemResponse::id));
        return items;
    }

    public ClientUser findById(Long id) {
        return clientUserRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
    }

    // Bug 3 fix: 统一返回 ClientUserListItemResponse，支持 Ward 详情查询
    public ClientUserListItemResponse findByIdAsResponse(Long id) {
        if (clientUserRepository.existsById(id)) {
            ClientUser u = clientUserRepository.findById(id).get();
            List<ClientUserDeviceResponse> devices = deviceRepository.findByGuardianId(u.getId()).stream()
                    .map(d -> new ClientUserDeviceResponse(d.getDeviceId()))
                    .collect(Collectors.toList());
            return new ClientUserListItemResponse(
                u.getId(), u.getMobile(), u.getName(), u.getRole(),
                u.getCreatedAt(), u.getUpdatedAt(), devices,
                "CLIENT_USER", null, null, null, null, null, null, null,
                u.getOrgId()
            );
        }
        Ward ward = wardRepository.findById(id)
            .orElseThrow(() -> new BizException(404, "用户不存在"));
        List<ClientUserDeviceResponse> devices = ward.getDevice() == null
            ? List.of()
            : List.of(new ClientUserDeviceResponse(ward.getDevice().getDeviceId()));
        return new ClientUserListItemResponse(
            ward.getMemberId(), ward.getMobile(), ward.getName(),
            ClientUserRole.WARD, null, null, devices, "WARD",
            ward.getGender(), ward.getBirthday(), ward.getHeight(),
            ward.getWeight(), ward.getRole(), ward.getChronicDisease(), ward.getRemark(),
            null
        );
    }

    @Transactional
    public Long create(ClientUserRequest request) {
        if (request.role() == ClientUserRole.WARD) {
            if (request.mobile() == null || request.mobile().isBlank()) {
                throw new BizException(400, "手机号不能为空");
            }
            Ward ward = new Ward();
            Long nextMemberId = wardRepository.findTopByOrderByMemberIdDesc()
                .map(existing -> existing.getMemberId() + 1)
                .orElse(2001L);
            ward.setMemberId(nextMemberId);
            ward.setName(request.name());
            ward.setMobile(request.mobile());
            ward.setGender(Objects.requireNonNullElse(request.gender(), Gender.MALE));
            ward.setBirthday(request.birthday());
            ward.setHeight(request.height());
            ward.setWeight(request.weight());
            ward.setRole(request.wardRole());
            ward.setChronicDisease(request.chronicDisease());
            ward.setRemark(request.remark());
            wardRepository.save(ward);
            return ward.getMemberId();
        }

        if (request.mobile() == null || request.mobile().isBlank()) {
            throw new BizException(400, "手机号不能为空");
        }
        if (clientUserRepository.existsByMobile(request.mobile())) {
            throw new BizException(400, "手机号已存在");
        }
        if (request.password() == null || request.password().isEmpty()) {
            throw new BizException(400, "密码不能为空");
        }

        ClientUser user = new ClientUser();
        user.setMobile(request.mobile());
        user.setName(request.name());
        user.setRole(request.role());
        user.setPassword(passwordEncoder.encode(request.password()));

        clientUserRepository.save(user);

        // 监护人注册后自动创建家庭
        if (request.role() == ClientUserRole.GUARDIAN) {
            Family family = new Family();
            family.setName(request.name());
            family.getGuardians().add(user);
            familyRepository.save(family);
        }
        return user.getId();
    }

    @Transactional
    public void update(Long id, ClientUserRequest request) {
        if (request.mobile() == null || request.mobile().isBlank()) {
            throw new BizException(400, "手机号不能为空");
        }

        // Bug 1 fix: 用 role 字段决定操作哪张表，避免 ID 重叠时误判
        if (request.role() == ClientUserRole.WARD) {
            Ward ward = wardRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
            ward.setName(request.name());
            ward.setMobile(request.mobile());
            ward.setGender(Objects.requireNonNullElse(request.gender(), Gender.MALE));
            ward.setBirthday(request.birthday());
            ward.setHeight(request.height());
            ward.setWeight(request.weight());
            ward.setRole(request.wardRole());
            ward.setChronicDisease(request.chronicDisease());
            ward.setRemark(request.remark());
            wardRepository.save(ward);
            return;
        }

        ClientUser user = clientUserRepository.findById(id)
            .orElseThrow(() -> new BizException(404, "用户不存在"));

        if (!user.getMobile().equals(request.mobile()) && clientUserRepository.existsByMobile(request.mobile())) {
            throw new BizException(400, "手机号已存在");
        }

        user.setMobile(request.mobile());
        user.setName(request.name());
        user.setRole(request.role());

        if (request.password() != null && !request.password().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        clientUserRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        if (clientUserRepository.existsById(id)) {
            // 1. 清除 device.guardian_id 引用
            deviceRepository.clearGuardianByGuardianId(id);
            // 2. 清除 family_guardian 关联表
            List<Family> families = familyRepository.findByGuardiansId(id);
            for (Family family : families) {
                family.getGuardians().removeIf(g -> g.getId().equals(id));
                familyRepository.save(family);
            }
            clientUserRepository.deleteById(id);
            return;
        }
        Ward ward = wardRepository.findById(id)
            .orElseThrow(() -> new BizException(404, "用户不存在"));
        wardRepository.delete(ward);
    }

    @Transactional
    public void bindDevice(Long userId, String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "设备不存在"));

        // Bug 2 fix: Ward 走独立的绑定字段
        if (wardRepository.existsById(userId)) {
            Ward ward = wardRepository.findById(userId).get();
            ward.setDevice(device);
            wardRepository.save(ward);
            return;
        }

        ClientUser user = clientUserRepository.findById(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        device.setGuardian(user);
        deviceRepository.save(device);
    }

    @Transactional
    public void unbindDevice(Long userId, String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "设备不存在"));

        // Bug 2 fix: Ward 走独立的解绑字段
        if (wardRepository.existsById(userId)) {
            Ward ward = wardRepository.findById(userId).get();
            if (ward.getDevice() != null && ward.getDevice().getDeviceId().equals(deviceId)) {
                ward.setDevice(null);
                wardRepository.save(ward);
            }
            return;
        }

        if (device.getGuardian() != null && device.getGuardian().getId().equals(userId)) {
            device.setGuardian(null);
            deviceRepository.save(device);
        }
    }
}
