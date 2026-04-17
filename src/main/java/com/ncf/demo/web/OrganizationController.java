package com.ncf.demo.web;

import com.ncf.demo.domain.UserStatus;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.repository.OrganizationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/organizations")
public class OrganizationController {

    private final OrganizationRepository organizationRepository;

    public OrganizationController(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @GetMapping
    public ApiResponse<List<Organization>> list() {
        return ApiResponse.ok(organizationRepository.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Organization> create(@RequestBody @Valid OrgRequest request) {
        Organization org = new Organization();
        org.setName(request.name());
        org.setType(request.type());
        org.setRegion(request.region());
        org.setContactPhone(request.contactPhone());
        return ApiResponse.ok(organizationRepository.save(org));
    }

    @PutMapping("/{id}")
    public ApiResponse<Organization> update(@PathVariable Long id, @RequestBody @Valid OrgRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new com.ncf.demo.common.BizException(404, "机构不存在"));
        org.setName(request.name());
        org.setType(request.type());
        org.setRegion(request.region());
        org.setContactPhone(request.contactPhone());
        return ApiResponse.ok(organizationRepository.save(org));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new com.ncf.demo.common.BizException(404, "机构不存在"));
        String status = body.get("status");
        if ("ENABLED".equalsIgnoreCase(status)) {
            org.setStatus(UserStatus.ENABLED);
        } else if ("DISABLED".equalsIgnoreCase(status)) {
            org.setStatus(UserStatus.DISABLED);
        } else {
            throw new com.ncf.demo.common.BizException(400, "status 必须为 ENABLED 或 DISABLED");
        }
        organizationRepository.save(org);
        return ApiResponse.ok(null);
    }

    record OrgRequest(
            @NotBlank String name,
            com.ncf.demo.domain.OrgType type,
            String region,
            String contactPhone
    ) {}
}

/** 所有已登录用户可读取机构列表（用于下拉选择） */
@RestController
@RequestMapping("/api/organizations")
class OrganizationPublicController {
    private final OrganizationRepository organizationRepository;
    OrganizationPublicController(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }
    @GetMapping
    public ApiResponse<List<Organization>> list() {
        return ApiResponse.ok(organizationRepository.findAll());
    }
}
