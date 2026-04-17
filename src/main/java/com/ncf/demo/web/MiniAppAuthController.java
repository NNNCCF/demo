package com.ncf.demo.web;

import com.ncf.demo.service.MiniAppAuthService;
import com.ncf.demo.web.dto.mini.*;
import com.ncf.demo.web.dto.mini.CreateInstitutionAdminRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mini-program authentication endpoints.
 * All routes are publicly accessible (no JWT required).
 */
@RestController
@RequestMapping("/api")
public class MiniAppAuthController {

    private final MiniAppAuthService service;

    public MiniAppAuthController(MiniAppAuthService service) {
        this.service = service;
    }

    // POST /api/auth/login  (统一登录，自动识别角色)
    @PostMapping("/auth/login")
    public ApiResponse<UnifiedLoginResponse> unifiedLogin(@Valid @RequestBody UnifiedLoginRequest req) {
        return ApiResponse.ok(service.unifiedLogin(req));
    }

    // POST /api/auth/nurse/login
    @PostMapping("/auth/nurse/login")
    public ApiResponse<NurseLoginResponse> nurseLogin(@Valid @RequestBody NurseLoginRequest req) {
        return ApiResponse.ok(service.nurseLogin(req));
    }

    // POST /api/auth/nurse/register
    @PostMapping("/auth/nurse/register")
    public ApiResponse<NurseLoginResponse> nurseRegister(@Valid @RequestBody NurseRegisterRequest req) {
        return ApiResponse.ok(service.nurseRegister(req));
    }

    // POST /api/auth/guardian/login
    @PostMapping("/auth/guardian/login")
    public ApiResponse<GuardianLoginResponse> guardianLogin(@Valid @RequestBody GuardianLoginRequest req) {
        return ApiResponse.ok(service.guardianLogin(req));
    }

    // POST /api/auth/guardian/register
    @PostMapping("/auth/guardian/register")
    public ApiResponse<GuardianLoginResponse> guardianRegister(@Valid @RequestBody GuardianRegisterRequest req) {
        return ApiResponse.ok(service.guardianRegister(req));
    }

    // POST /api/auth/sms/send
    @PostMapping("/auth/sms/send")
    public ApiResponse<Void> sendSms(@Valid @RequestBody SmsRequest req) {
        service.sendSmsCode(req);
        return ApiResponse.ok(null);
    }

    // GET /api/auth/check-phone?phone=xxx
    @GetMapping("/auth/check-phone")
    public ApiResponse<Map<String, Boolean>> checkPhone(@RequestParam String phone) {
        boolean registered = service.isPhoneRegistered(phone);
        return ApiResponse.ok(Map.of("registered", registered));
    }

    // GET /api/institution/list
    @GetMapping("/institution/list")
    public ApiResponse<List<InstitutionListItem>> listInstitutions() {
        return ApiResponse.ok(service.listInstitutions());
    }

    // GET /api/institution/nurses?orgId=xxx
    @GetMapping("/institution/nurses")
    public ApiResponse<List<NurseListItem>> listNurses(@RequestParam(required = false) Long orgId) {
        return ApiResponse.ok(service.listNurses(orgId));
    }

    /**
     * POST /api/admin/institution-admin/create
     * 后端管理员预创建机构管理员账号（机构签约后由运维调用）。
     * 生产环境应加 ADMIN 角色鉴权；此处简单保留接口供内部使用。
     */
    @PostMapping("/admin/institution-admin/create")
    public ApiResponse<Map<String, Object>> createInstitutionAdmin(
            @Valid @RequestBody CreateInstitutionAdminRequest req) {
        var user = service.createInstitutionAdmin(req);
        return ApiResponse.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "phone", user.getMobile(),
                "orgId", user.getOrgId() != null ? user.getOrgId() : ""
        ));
    }
}
