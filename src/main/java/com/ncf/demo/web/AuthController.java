package com.ncf.demo.web;

import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.service.AuthCaptchaService;
import com.ncf.demo.service.AuthService;
import com.ncf.demo.web.dto.AuthCaptchaResponse;
import com.ncf.demo.web.dto.ChangePasswordRequest;
import com.ncf.demo.web.dto.LoginRequest;
import com.ncf.demo.web.dto.LoginResponse;
import com.ncf.demo.web.dto.PublicRegisterRequest;
import com.ncf.demo.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;
    private final AuthCaptchaService authCaptchaService;

    public AuthController(AuthService authService, AuthCaptchaService authCaptchaService) {
        this.authService = authService;
        this.authCaptchaService = authCaptchaService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(authService.login(request, authCaptchaService.buildClientKey(httpServletRequest)));
    }

    @GetMapping("/auth/captcha")
    public ApiResponse<AuthCaptchaResponse> getCaptcha(@RequestParam String scene, HttpServletRequest request) {
        return ApiResponse.ok(authCaptchaService.issueCaptcha(scene, authCaptchaService.buildClientKey(request)));
    }

    @GetMapping("/auth/captcha/{captchaToken}/image")
    public ResponseEntity<byte[]> getCaptchaImage(@PathVariable String captchaToken, HttpServletRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ZERO).mustRevalidate().noStore())
                .body(authCaptchaService.fetchCaptchaImage(captchaToken, authCaptchaService.buildClientKey(request)));
    }

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@RequestBody @Valid PublicRegisterRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(authService.registerPublic(request, authCaptchaService.buildClientKey(httpServletRequest)));
    }

    @PostMapping("/account/change-password")
    public ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        SecurityUtil.requireCurrentUserId();
        authService.changePassword(request);
        return ApiResponse.ok(null);
    }
}
