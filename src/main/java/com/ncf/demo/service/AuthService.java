package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.config.AppProperties;
import com.ncf.demo.domain.UserRole;
import com.ncf.demo.domain.UserStatus;
import com.ncf.demo.entity.UserAccount;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.UserRepository;
import com.ncf.demo.security.JwtService;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.web.dto.AdminCreateUserRequest;
import com.ncf.demo.web.dto.ChangePasswordRequest;
import com.ncf.demo.web.dto.LoginRequest;
import com.ncf.demo.web.dto.LoginResponse;
import com.ncf.demo.web.dto.PublicRegisterRequest;
import com.ncf.demo.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final AuthCaptchaService authCaptchaService;

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties appProperties,
            AuthCaptchaService authCaptchaService
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.authCaptchaService = authCaptchaService;
    }

    public LoginResponse login(LoginRequest request, String clientKey) {
        authCaptchaService.verifyCaptcha("LOGIN", clientKey, request.captchaToken(), request.captchaCode());
        List<UserAccount> matchedUsers = userRepository.findAllByUsernameOrderByIdAsc(request.username());
        if (matchedUsers.isEmpty()) {
            throw new BizException(4001, "User does not exist");
        }
        if (matchedUsers.size() > 1) {
            throw new BizException(4006, "Username is duplicated, please contact an administrator");
        }
        UserAccount user = matchedUsers.get(0);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException(4002, "Account is disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(4003, "Username or password is invalid");
        }
        String token = jwtService.generate(user.getId(), user.getRole().name(), user.getOrgId());
        String orgType = null;
        if (user.getOrgId() != null) {
            orgType = organizationRepository.findById(user.getOrgId())
                    .map(org -> org.getType() != null ? org.getType().name() : null)
                    .orElse(null);
        }
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getOrgId(),
                orgType,
                token,
                appProperties.getJwt().getExpireSeconds(),
                user.isForcePasswordChange()
        );
    }

    public UserResponse registerPublic(PublicRegisterRequest request, String clientKey) {
        authCaptchaService.verifyCaptcha("REGISTER", clientKey, request.captchaToken(), request.captchaCode());
        return createUser(
                request.username(),
                request.password(),
                UserRole.GUARDIAN,
                request.region(),
                request.phone(),
                null,
                false
        );
    }

    public UserResponse createUser(AdminCreateUserRequest request) {
        return createUser(
                request.username(),
                request.password(),
                request.role(),
                request.region(),
                request.phone(),
                request.orgId(),
                true
        );
    }

    private UserResponse createUser(
            String username,
            String password,
            UserRole role,
            String region,
            String phone,
            Long orgId,
            boolean forcePasswordChange
    ) {
        if (userRepository.existsByUsername(username)) {
            throw new BizException(4005, "Username already exists");
        }
        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
            throw new BizException(4009, "Phone number already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setRegion(region);
        user.setOrgId(orgId);
        user.setPhone(phone);
        user.setStatus(UserStatus.ENABLED);
        user.setForcePasswordChange(forcePasswordChange);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        return toUserResponse(user);
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toUserResponse)
                .toList();
    }

    public void updateStatus(Long userId, UserStatus status) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(4001, "User does not exist"));
        user.setStatus(status);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public void resetPassword(Long userId, String newPassword) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(4001, "User does not exist"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public void changePassword(ChangePasswordRequest request) {
        UserAccount user = userRepository.findById(SecurityUtil.requireCurrentUserId())
                .orElseThrow(() -> new BizException(4001, "User does not exist"));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(4010, "Current password is invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setForcePasswordChange(false);
        Instant now = Instant.now();
        user.setPasswordChangedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    private UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getRegion(),
                user.getPhone(),
                user.getStatus(),
                user.getOrgId()
        );
    }
}
