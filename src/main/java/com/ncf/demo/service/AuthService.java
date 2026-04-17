package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.config.AppProperties;
import com.ncf.demo.domain.UserStatus;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.UserAccount;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.UserRepository;
import com.ncf.demo.security.JwtService;
import com.ncf.demo.web.dto.LoginRequest;
import com.ncf.demo.web.dto.LoginResponse;
import com.ncf.demo.web.dto.RegisterRequest;
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

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
    }

    public LoginResponse login(LoginRequest request) {
        List<UserAccount> matchedUsers = userRepository.findAllByUsernameOrderByIdAsc(request.username());
        if (matchedUsers.isEmpty()) {
            throw new BizException(4001, "用户不存在");
        }
        if (matchedUsers.size() > 1) {
            throw new BizException(4006, "用户名存在重复，请联系管理员处理");
        }
        UserAccount user = matchedUsers.get(0);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException(4002, "账号已禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(4003, "账号或密码错误");
        }
        String token = jwtService.generate(user.getId(), user.getRole().name(), user.getOrgId());
        String orgType = null;
        if (user.getOrgId() != null) {
            orgType = organizationRepository.findById(user.getOrgId())
                    .map(org -> org.getType() != null ? org.getType().name() : null)
                    .orElse(null);
        }
        return new LoginResponse(user.getId(), user.getUsername(), user.getRole().name(), user.getOrgId(), orgType, token, appProperties.getJwt().getExpireSeconds());
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BizException(4005, "用户名已存在");
        }
        UserAccount user = new UserAccount();
        // ID is auto-generated
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setRegion(request.region());
        user.setOrgId(request.orgId());
        user.setPhone(request.phone());
        user.setStatus(UserStatus.ENABLED);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
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

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),
                        user.getRegion(),
                        user.getPhone(),
                        user.getStatus(),
                        user.getOrgId()
                ))
                .toList();
    }

    public void updateStatus(Long userId, UserStatus status) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(4001, "用户不存在"));
        user.setStatus(status);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public void resetPassword(Long userId, String newPassword) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(4001, "用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }
}
