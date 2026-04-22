package com.ncf.demo.web;

import com.ncf.demo.entity.DeviceLog;
import com.ncf.demo.service.DeviceLogService;
import com.ncf.demo.service.DeviceService;
import com.ncf.demo.entity.Device;
import com.ncf.demo.web.dto.DeviceControlRequest;
import com.ncf.demo.web.dto.DeviceRegisterRequest;
import com.ncf.demo.web.dto.DeviceRegisterResponse;
import com.ncf.demo.web.dto.DeviceUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;
    private final DeviceLogService deviceLogService;

    public DeviceController(DeviceService deviceService, DeviceLogService deviceLogService) {
        this.deviceService = deviceService;
        this.deviceLogService = deviceLogService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceRegisterResponse> register(@RequestBody @Valid DeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request));
    }

    @PostMapping("/{deviceId}/mqtt-credentials")
    public ApiResponse<DeviceRegisterResponse> rotateMqttCredentials(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceService.rotateMqttCredentials(deviceId));
    }

    @PutMapping("/{deviceId}")
    public ApiResponse<Void> update(@PathVariable String deviceId, @RequestBody @Valid DeviceUpdateRequest request) {
        deviceService.update(deviceId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<Device>> list() {
        return ApiResponse.ok(deviceService.listAll());
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> delete(@PathVariable String deviceId) {
        deviceService.delete(deviceId);
        return ApiResponse.ok(null);
    }

    /** DELETE /api/devices/{deviceId}/binding — 解绑设备 */
    @DeleteMapping("/{deviceId}/binding")
    public ApiResponse<Void> unbind(@PathVariable String deviceId) {
        deviceService.unbind(deviceId);
        return ApiResponse.ok(null);
    }

    /** POST /api/devices/{deviceId}/reset — 重置设备 */
    @PostMapping("/{deviceId}/reset")
    public ApiResponse<Void> reset(@PathVariable String deviceId) {
        deviceService.sendReset(deviceId);
        return ApiResponse.ok(null);
    }

    /** PATCH /api/devices/{deviceId}/status — 启用/禁用设备，body: {"status":"ENABLED"|"DISABLED"} */
    @PatchMapping("/{deviceId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable String deviceId, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if ("DISABLED".equalsIgnoreCase(status)) {
            deviceService.disable(deviceId);
        } else if ("ENABLED".equalsIgnoreCase(status)) {
            deviceService.enable(deviceId);
        } else {
            throw new com.ncf.demo.common.BizException(400, "status 必须为 ENABLED 或 DISABLED");
        }
        return ApiResponse.ok(null);
    }

    /** POST /api/devices/{deviceId}/commands — 下发控制指令 */
    @PostMapping("/{deviceId}/commands")
    public ApiResponse<Void> control(@PathVariable String deviceId, @RequestBody @Valid DeviceControlRequest request) {
        deviceService.sendControl(deviceId, request);
        return ApiResponse.ok(null);
    }
    
    @GetMapping("/{deviceId}/logs")
    public ApiResponse<List<DeviceLog>> logs(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceLogService.getLogs(deviceId));
    }
}
