package com.ncf.demo.web;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Family;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.security.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private final FamilyRepository familyRepository;
    private final ClientUserRepository clientUserRepository;
    private final DeviceRepository deviceRepository;

    public FamilyController(FamilyRepository familyRepository, ClientUserRepository clientUserRepository, DeviceRepository deviceRepository) {
        this.familyRepository = familyRepository;
        this.clientUserRepository = clientUserRepository;
        this.deviceRepository = deviceRepository;
    }

    @GetMapping
    public ApiResponse<List<Family>> list() {
        Long orgId = SecurityUtil.currentOrgId();
        if (orgId != null) {
            return ApiResponse.ok(familyRepository.findByOrgId(orgId));
        }
        return ApiResponse.ok(familyRepository.findAll());
    }

    @GetMapping("/{id}/detail")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Family family = familyRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "家庭不存在"));
        checkOrgAccess(family);
        List<Device> devices = deviceRepository.findByFamilyId(id);

        // 从设备中提取被监护人（Ward）信息
        List<Map<String, Object>> wardList = devices.stream()
                .filter(d -> d.getWards() != null)
                .flatMap(d -> d.getWards().stream().map(w -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("memberId", w.getMemberId());
                    m.put("name", w.getName());
                    m.put("mobile", w.getMobile());
                    m.put("gender", w.getGender() != null ? w.getGender().name() : null);
                    m.put("birthday", w.getBirthday() != null
                            ? w.getBirthday().atZone(java.time.ZoneId.of("Asia/Shanghai"))
                               .toLocalDate().toString() : null);
                    m.put("chronicDisease", w.getChronicDisease());
                    m.put("remark", w.getRemark());
                    m.put("emergencyPhone", w.getEmergencyPhone());
                    m.put("deviceId", d.getDeviceId());
                    return m;
                }))
                .collect(java.util.stream.Collectors.toList());

        // 设备简洁信息
        List<Map<String, Object>> deviceList = devices.stream().map(d -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("deviceId", d.getDeviceId());
            m.put("address", d.getAddress());
            m.put("homeLocation", d.getHomeLocation());
            m.put("status", d.getStatus() != null ? d.getStatus().name() : "UNKNOWN");
            m.put("bindTime", d.getBindTime() != null
                    ? d.getBindTime().atZone(java.time.ZoneId.of("Asia/Shanghai"))
                       .toLocalDateTime().toString() : null);
            m.put("latitude", d.getLatitude());
            m.put("longitude", d.getLongitude());
            return m;
        }).collect(java.util.stream.Collectors.toList());

        // 家属简洁信息
        List<Map<String, Object>> guardianList = family.getGuardians().stream().map(g -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("name", g.getName());
            m.put("mobile", g.getMobile());
            return m;
        }).collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", family.getId());
        result.put("name", family.getName());
        result.put("address", family.getAddress());
        result.put("orgId", family.getOrgId());
        result.put("createdAt", family.getCreatedAt());
        result.put("guardians", guardianList);
        result.put("wards", wardList);
        result.put("devices", deviceList);
        return ApiResponse.ok(result);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Family> create(@RequestBody @Valid FamilyRequest request) {
        Long orgId = SecurityUtil.currentOrgId();
        Family family = new Family();
        family.setName(request.name());
        family.setAddress(request.address());
        family.setOrgId(orgId != null ? orgId : request.orgId());
        return ApiResponse.ok(familyRepository.save(family));
    }

    @PutMapping("/{id}")
    public ApiResponse<Family> update(@PathVariable Long id, @RequestBody @Valid FamilyRequest request) {
        Family family = familyRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "家庭不存在"));
        checkOrgAccess(family);
        family.setName(request.name());
        family.setAddress(request.address());
        return ApiResponse.ok(familyRepository.save(family));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Family family = familyRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "家庭不存在"));
        checkOrgAccess(family);
        familyRepository.delete(family);
    }

    @PostMapping("/{id}/guardians")
    public ApiResponse<Void> addGuardian(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long clientUserId = body.get("clientUserId");
        if (clientUserId == null) throw new BizException(400, "clientUserId 不能为空");
        Family family = familyRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "家庭不存在"));
        checkOrgAccess(family);
        ClientUser user = clientUserRepository.findById(clientUserId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        if (!family.getGuardians().contains(user)) {
            family.getGuardians().add(user);
            familyRepository.save(family);
        }
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/guardians/{clientUserId}")
    public ApiResponse<Void> removeGuardian(@PathVariable Long id, @PathVariable Long clientUserId) {
        Family family = familyRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "家庭不存在"));
        checkOrgAccess(family);
        family.getGuardians().removeIf(u -> u.getId().equals(clientUserId));
        familyRepository.save(family);
        return ApiResponse.ok(null);
    }

    private void checkOrgAccess(Family family) {
        Long orgId = SecurityUtil.currentOrgId();
        if (orgId != null && !orgId.equals(family.getOrgId())) {
            throw new BizException(403, "无权操作该家庭");
        }
    }

    /**
     * POST /api/families/{id}/bind-caregiver
     * 将护工（CAREGIVER）的机构 orgId 写入家庭，实现护工→家庭归属绑定。
     * 同时让家属端"家庭医生"能通过 orgId 找到护工。
     */
    @PostMapping("/{id}/bind-caregiver")
    public ApiResponse<Void> bindCaregiver(@PathVariable Long id,
                                           @RequestBody Map<String, Long> body) {
        Long caregiverId = body.get("caregiverId");
        if (caregiverId == null) throw new BizException(400, "caregiverId 不能为空");

        Family family = familyRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "家庭不存在"));

        ClientUser caregiver = clientUserRepository.findById(caregiverId)
                .orElseThrow(() -> new BizException(404, "护工不存在"));
        if (caregiver.getRole() != ClientUserRole.CAREGIVER) {
            throw new BizException(400, "该用户不是护工角色");
        }
        if (caregiver.getOrgId() == null) {
            throw new BizException(400, "该护工未关联医疗机构");
        }

        // 把护工的机构 orgId 写入家庭，同时记录负责护工 id
        family.setOrgId(caregiver.getOrgId());
        family.setCaregiverId(caregiverId);
        familyRepository.save(family);
        return ApiResponse.ok(null);
    }

    record FamilyRequest(@NotBlank String name, String address, Long orgId) {}
}
