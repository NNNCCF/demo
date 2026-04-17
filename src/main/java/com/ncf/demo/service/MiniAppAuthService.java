package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.UserRole;
import com.ncf.demo.domain.UserStatus;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.UserAccount;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.UserRepository;
import com.ncf.demo.security.JwtService;
import com.ncf.demo.web.dto.mini.*;
import com.ncf.demo.web.dto.mini.CreateInstitutionAdminRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MiniAppAuthService {

    private static final Logger log = LoggerFactory.getLogger(MiniAppAuthService.class);
    private static final long OTP_TTL_SECONDS = 300;

    private record OtpEntry(String code, Instant expiresAt) {}
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    private final ClientUserRepository clientUserRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository orgRepository;
    private final FamilyRepository familyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public MiniAppAuthService(ClientUserRepository clientUserRepository,
                               UserRepository userRepository,
                               OrganizationRepository orgRepository,
                               FamilyRepository familyRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService) {
        this.clientUserRepository = clientUserRepository;
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.familyRepository = familyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // ─── 统一登录（自动识别角色）────────────────────────────────────────────────

    public UnifiedLoginResponse unifiedLogin(UnifiedLoginRequest req) {
        ClientUser user = clientUserRepository.findByMobile(req.phone())
                .orElseThrow(() -> new BizException(1001, "手机号未注册"));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BizException(1004, "密码错误");
        }

        String roleStr;
        String jwtRole;
        if (user.getRole() == ClientUserRole.CAREGIVER) {
            roleStr = "caregiver";
            jwtRole = "CAREGIVER";
        } else if (user.getRole() == ClientUserRole.INSTITUTION) {
            roleStr = "institution";
            jwtRole = "INSTITUTION";
        } else {
            roleStr = "guardian";
            jwtRole = "GUARDIAN";
        }
        String token = jwtService.generate(user.getId(), jwtRole, user.getOrgId());
        String orgName = (user.getRole() == ClientUserRole.CAREGIVER || user.getRole() == ClientUserRole.INSTITUTION)
                ? resolveOrgName(user.getOrgId()) : null;

        UnifiedUserInfo userInfo = new UnifiedUserInfo(
                user.getId(),
                user.getName(),
                user.getMobile(),
                roleStr,
                user.getOrgId(),
                orgName
        );
        return new UnifiedLoginResponse(token, roleStr, userInfo);
    }

    // ─── 医护人员登录（查 client_user，role = CAREGIVER）────────────────────────

    public NurseLoginResponse nurseLogin(NurseLoginRequest req) {
        ClientUser user = clientUserRepository.findByMobile(req.phone())
                .orElseThrow(() -> new BizException(1001, "手机号未注册"));

        if (user.getRole() != ClientUserRole.CAREGIVER) {
            throw new BizException(1002, "该账号不是医护人员账号");
        }
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BizException(1004, "密码错误");
        }

        String token = jwtService.generate(user.getId(), "CAREGIVER", user.getOrgId());
        String institutionName = resolveOrgName(user.getOrgId());

        NurseUserInfo userInfo = new NurseUserInfo(
                user.getId(),
                user.getName(),
                user.getMobile(),
                "doctor",
                institutionName,
                user.getOrgId(),
                null,
                true
        );
        return new NurseLoginResponse(token, userInfo);
    }

    // ─── 监护人登录（查 client_user，role = GUARDIAN）────────────────────────────

    public GuardianLoginResponse guardianLogin(GuardianLoginRequest req) {
        ClientUser user = clientUserRepository.findByMobile(req.phone())
                .orElseThrow(() -> new BizException(1001, "手机号未注册"));

        if (user.getRole() != ClientUserRole.GUARDIAN) {
            throw new BizException(1002, "该账号不是监护人账号");
        }
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BizException(1004, "密码错误");
        }

        String token = jwtService.generate(user.getId(), "GUARDIAN", null);
        GuardianUserInfo userInfo = new GuardianUserInfo(
                user.getId(),
                user.getName(),
                user.getMobile(),
                "guardian",
                null,
                null
        );

        List<Object> devices = user.getDevices().stream()
                .map(d -> (Object) d.getDeviceId())
                .collect(Collectors.toList());

        return new GuardianLoginResponse(token, userInfo, devices);
    }

    // ─── 医护人员注册（存 client_user，role = CAREGIVER）────────────────────────

    @Transactional
    public NurseLoginResponse nurseRegister(NurseRegisterRequest req) {
        // verifyOtp(req.phone(), req.smsCode()); // TODO: 接入真实短信后取消注释

        if (clientUserRepository.existsByMobile(req.phone())) {
            throw new BizException(1005, "手机号已注册");
        }

        Long orgId = req.institutionId();
        if (orgId == null && req.institutionName() != null && !req.institutionName().isBlank()) {
            orgId = orgRepository.findAll().stream()
                    .filter(o -> o.getName().equals(req.institutionName()))
                    .map(Organization::getId)
                    .findFirst()
                    .orElse(null);
        }

        ClientUser user = new ClientUser();
        user.setName(req.name());
        user.setMobile(req.phone());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(ClientUserRole.CAREGIVER);
        user.setOrgId(orgId);
        clientUserRepository.save(user);

        String token = jwtService.generate(user.getId(), "CAREGIVER", orgId);
        String institutionName = resolveOrgName(orgId);

        NurseUserInfo userInfo = new NurseUserInfo(
                user.getId(), user.getName(), user.getMobile(),
                "doctor", institutionName, orgId, null, true
        );
        return new NurseLoginResponse(token, userInfo);
    }

    // ─── 监护人注册（存 client_user，role = GUARDIAN）────────────────────────────

    @Transactional
    public GuardianLoginResponse guardianRegister(GuardianRegisterRequest req) {
        // verifyOtp(req.phone(), req.smsCode()); // TODO: 接入真实短信后取消注释

        if (clientUserRepository.existsByMobile(req.phone())) {
            throw new BizException(1005, "手机号已注册");
        }

        ClientUser user = new ClientUser();
        user.setName(req.name());
        user.setMobile(req.phone());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(ClientUserRole.GUARDIAN);
        clientUserRepository.save(user);

        // 自动创建家庭
        Family family = new Family();
        family.setName(req.name());
        family.getGuardians().add(user);
        familyRepository.save(family);

        String token = jwtService.generate(user.getId(), "GUARDIAN", null);
        GuardianUserInfo userInfo = new GuardianUserInfo(
                user.getId(), user.getName(), user.getMobile(),
                "guardian", null, null
        );
        return new GuardianLoginResponse(token, userInfo, Collections.emptyList());
    }

    // ─── 后端管理员创建机构管理员账号 ──────────────────────────────────────────────

    @Transactional
    public ClientUser createInstitutionAdmin(CreateInstitutionAdminRequest req) {
        if (clientUserRepository.existsByMobile(req.phone())) {
            throw new BizException(1005, "手机号已注册");
        }
        ClientUser user = new ClientUser();
        user.setName(req.name());
        user.setMobile(req.phone());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(ClientUserRole.INSTITUTION);
        user.setOrgId(req.orgId());
        clientUserRepository.save(user);
        return user;
    }

    // ─── SMS OTP ──────────────────────────────────────────────────────────────

    public void sendSmsCode(SmsRequest req) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        otpStore.put(req.phone(), new OtpEntry(code, Instant.now().plusSeconds(OTP_TTL_SECONDS)));
        log.info("[SMS OTP] phone={} scene={} code={}", req.phone(), req.scene(), code);
    }

    private void verifyOtp(String phone, String inputCode) {
        OtpEntry entry = otpStore.get(phone);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            throw new BizException(1006, "验证码已过期，请重新获取");
        }
        if (!entry.code().equals(inputCode)) {
            throw new BizException(1007, "验证码错误");
        }
        otpStore.remove(phone);
    }

    // ─── 手机号检查 ────────────────────────────────────────────────────────────

    public boolean isPhoneRegistered(String phone) {
        return clientUserRepository.existsByMobile(phone)
                || userRepository.existsByPhone(phone);
    }

    // ─── 机构列表 ──────────────────────────────────────────────────────────────

    public List<InstitutionListItem> listInstitutions() {
        return orgRepository.findAll().stream()
                .filter(o -> o.getStatus() == UserStatus.ENABLED)
                .map(o -> new InstitutionListItem(
                        o.getId(),
                        o.getName(),
                        o.getRegion(),
                        o.getType() != null ? o.getType().name() : null
                ))
                .collect(Collectors.toList());
    }

    // ─── 医护人员列表 ──────────────────────────────────────────────────────────

    public List<NurseListItem> listNurses(Long orgId) {
        return clientUserRepository.findAll().stream()
                .filter(u -> u.getRole() == ClientUserRole.CAREGIVER
                        && (orgId == null || orgId.equals(u.getOrgId())))
                .map(u -> new NurseListItem(
                        u.getId(),
                        u.getName(),
                        u.getMobile(),
                        "doctor",
                        true
                ))
                .collect(Collectors.toList());
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────────

    private String resolveOrgName(Long orgId) {
        if (orgId == null) return null;
        return orgRepository.findById(orgId).map(Organization::getName).orElse(null);
    }
}
