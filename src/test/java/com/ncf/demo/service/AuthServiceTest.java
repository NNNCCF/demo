package com.ncf.demo.service;

import com.ncf.demo.config.AppProperties;
import com.ncf.demo.domain.UserRole;
import com.ncf.demo.domain.UserStatus;
import com.ncf.demo.entity.UserAccount;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.UserRepository;
import com.ncf.demo.security.JwtService;
import com.ncf.demo.web.dto.AdminCreateUserRequest;
import com.ncf.demo.web.dto.LoginRequest;
import com.ncf.demo.web.dto.LoginResponse;
import com.ncf.demo.web.dto.PublicRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AuthCaptchaService authCaptchaService;

    private AuthService authService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret("health-iot-dev-jwt-secret-change-me-2026");
        appProperties.getJwt().setExpireSeconds(7200);
        authService = new AuthService(
                userRepository,
                organizationRepository,
                passwordEncoder,
                new JwtService(appProperties),
                appProperties,
                authCaptchaService
        );
    }

    @Test
    void loginReturnsForcePasswordChangeFlag() {
        UserAccount user = new UserAccount();
        user.setId(1000L);
        user.setUsername("admin");
        user.setPasswordHash(passwordEncoder.encode("200502"));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ENABLED);
        user.setForcePasswordChange(true);

        when(userRepository.findAllByUsernameOrderByIdAsc("admin")).thenReturn(List.of(user));

        LoginResponse response = authService.login(
                new LoginRequest("admin", "200502", "captcha-token", "abcd"),
                "client-key"
        );

        assertThat(response.forcePasswordChange()).isTrue();
        assertThat(response.username()).isEqualTo("admin");
    }

    @Test
    void adminCreatedUsersAreMarkedForPasswordChange() {
        when(userRepository.existsByUsername("ops-admin")).thenReturn(false);
        when(userRepository.existsByPhone("13800000000")).thenReturn(false);
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(2001L);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            return user;
        });

        var response = authService.createUser(
                new AdminCreateUserRequest(null, "ops-admin", "Secret123", UserRole.ADMIN, "HQ", "13800000000", null)
        );

        assertThat(response.username()).isEqualTo("ops-admin");
    }

    @Test
    void publicRegisterAlwaysCreatesGuardianUsers() {
        when(userRepository.existsByUsername("guardian01")).thenReturn(false);
        when(userRepository.existsByPhone("13811112222")).thenReturn(false);
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(3001L);
            return user;
        });

        var response = authService.registerPublic(
                new PublicRegisterRequest("guardian01", "Secret123", "East", "13811112222", "captcha-token", "abcd"),
                "client-key"
        );

        assertThat(response.role()).isEqualTo(UserRole.GUARDIAN);
    }
}
