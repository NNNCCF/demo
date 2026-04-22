package com.ncf.demo.web;

import com.ncf.demo.common.BizException;
import com.ncf.demo.service.AuthService;
import com.ncf.demo.web.dto.AdminCreateUserRequest;
import com.ncf.demo.web.dto.UserResponse;
import com.ncf.demo.web.dto.UserStatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.ok(authService.listUsers());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@RequestBody @Valid AdminCreateUserRequest request) {
        return ApiResponse.ok(authService.createUser(request));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId, @RequestBody @Valid UserStatusUpdateRequest request) {
        authService.updateStatus(userId, request.status());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "New password must contain at least 6 characters");
        }
        authService.resetPassword(userId, newPassword);
        return ApiResponse.ok(null);
    }
}
