package com.ncf.demo.web;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.service.ClientUserService;
import com.ncf.demo.web.dto.ClientUserListItemResponse;
import com.ncf.demo.web.dto.ClientUserRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/client-users")
public class ClientUserController {

    private final ClientUserService clientUserService;
    private final ClientUserRepository clientUserRepository;

    public ClientUserController(ClientUserService clientUserService,
                                ClientUserRepository clientUserRepository) {
        this.clientUserService = clientUserService;
        this.clientUserRepository = clientUserRepository;
    }

    @GetMapping
    public ApiResponse<List<ClientUserListItemResponse>> list() {
        return ApiResponse.ok(clientUserService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ClientUserListItemResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(clientUserService.findByIdAsResponse(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ClientUserRequest request) {
        Long id = clientUserService.create(request);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ClientUserRequest request) {
        clientUserService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        clientUserService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/devices")
    public ApiResponse<Void> bindDevice(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        if (deviceId == null) {
            return ApiResponse.error(400, "Device ID is required");
        }
        clientUserService.bindDevice(id, deviceId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/devices/{deviceId}")
    public ApiResponse<Void> unbindDevice(@PathVariable Long id, @PathVariable String deviceId) {
        clientUserService.unbindDevice(id, deviceId);
        return ApiResponse.ok(null);
    }

    /**
     * PATCH /api/client-users/{id}/assign-org
     * 将护工（CAREGIVER）分配到指定医疗机构（设置 orgId）。
     * 由后台管理员在机构管理界面操作。
     */
    @PatchMapping("/{id}/assign-org")
    public ApiResponse<Void> assignOrg(@PathVariable Long id,
                                       @RequestBody Map<String, Long> body) {
        Long orgId = body.get("orgId");
        var user = clientUserRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        if (user.getRole() != ClientUserRole.CAREGIVER) {
            throw new BizException(400, "只能为护工角色分配机构");
        }
        user.setOrgId(orgId);
        clientUserRepository.save(user);
        return ApiResponse.ok(null);
    }
}
