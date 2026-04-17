package com.ncf.demo.web;

import com.ncf.demo.service.SystemConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/system/config")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> get() {
        return ApiResponse.ok(systemConfigService.getAll());
    }

    @PutMapping
    public ApiResponse<Void> save(@RequestBody Map<String, String> params) {
        systemConfigService.setAll(params);
        return ApiResponse.ok(null);
    }
}
