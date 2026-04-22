package com.ncf.demo.web;

import com.ncf.demo.service.DeviceService;
import com.ncf.demo.web.dto.EmqxAuthRequest;
import com.ncf.demo.web.dto.EmqxAuthResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emqx")
public class EmqxAuthController {
    private final DeviceService deviceService;

    public EmqxAuthController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/auth")
    public EmqxAuthResponse auth(@RequestBody EmqxAuthRequest request) {
        String username = StringUtils.hasText(request.username()) ? request.username() : request.clientid();
        boolean passed = request.clientid() != null
                && deviceService.canAuth(request.clientid(), username, request.password());
        return new EmqxAuthResponse(passed ? "allow" : "deny", false);
    }
}
