package com.ncf.demo.web;

import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Doctor;
import com.ncf.demo.service.DoctorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public ApiResponse<List<Doctor>> list(@RequestParam(required = false) Long orgId) {
        List<Doctor> result = orgId != null ? doctorService.listByOrg(orgId) : doctorService.listAll();
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Doctor> get(@PathVariable Long id) {
        return ApiResponse.ok(doctorService.getById(id));
    }

    @PostMapping
    public ApiResponse<Doctor> create(@RequestBody Doctor doctor) {
        return ApiResponse.ok(doctorService.create(doctor));
    }

    @PutMapping("/{id}")
    public ApiResponse<Doctor> update(@PathVariable Long id, @RequestBody Doctor patch) {
        return ApiResponse.ok(doctorService.update(id, patch));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        doctorService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/devices")
    public ApiResponse<List<Device>> listDevices(@PathVariable Long id) {
        return ApiResponse.ok(doctorService.listDevices(id));
    }

    @PostMapping("/{id}/devices/{deviceId}")
    public ApiResponse<Void> bindDevice(@PathVariable Long id, @PathVariable String deviceId) {
        doctorService.bindDevice(id, deviceId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/devices/{deviceId}")
    public ApiResponse<Void> unbindDevice(@PathVariable Long id, @PathVariable String deviceId) {
        doctorService.unbindDevice(id, deviceId);
        return ApiResponse.ok(null);
    }
}
