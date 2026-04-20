package com.ncf.demo.web;

import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.ServiceOrderRepository;
import com.ncf.demo.service.ServiceOrderService;
import com.ncf.demo.web.dto.ServiceOrderCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-orders")
public class ServiceOrderController {
    private final ServiceOrderService serviceOrderService;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ClientUserRepository clientUserRepository;

    public ServiceOrderController(ServiceOrderService serviceOrderService,
                                  ServiceOrderRepository serviceOrderRepository,
                                  ClientUserRepository clientUserRepository) {
        this.serviceOrderService = serviceOrderService;
        this.serviceOrderRepository = serviceOrderRepository;
        this.clientUserRepository = clientUserRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceOrder> create(@RequestBody @Valid ServiceOrderCreateRequest request) {
        return ApiResponse.ok(serviceOrderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ServiceOrder>> list(@RequestParam(required = false) Long targetId,
                                                @RequestParam(required = false) Long orgId) {
        if (orgId != null) {
            return ApiResponse.ok(serviceOrderRepository.findByOrgIdOrderByCreatedAtDesc(orgId));
        }
        return ApiResponse.ok(serviceOrderService.listOrders(targetId));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ServiceOrderStatus status = ServiceOrderStatus.valueOf(body.get("status"));
        serviceOrderService.updateStatus(id, status);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/dispatch")
    public ApiResponse<Void> dispatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ServiceOrder order = serviceOrderRepository.findById(id)
                .orElseThrow(() -> new com.ncf.demo.common.BizException(404, "订单不存在"));
        Long nurseId = body.get("nurseId") != null ? Long.valueOf(body.get("nurseId").toString()) : null;
        String nurseName = (String) body.get("nurseName");
        if (nurseId != null) {
            ClientUser nurse = clientUserRepository.findById(nurseId).orElse(null);
            if (nurse != null) {
                order.setNurseId(nurseId);
                order.setNurseName(nurse.getName());
                order.setNursePhone(nurse.getMobile());
            }
        } else if (nurseName != null) {
            order.setNurseName(nurseName);
        }
        serviceOrderRepository.save(order);
        return ApiResponse.ok(null);
    }

    /** 返回指定机构下的医护人员列表（供派单用） */
    @GetMapping("/nurses")
    public ApiResponse<List<Map<String, Object>>> listNurses(@RequestParam Long orgId) {
        List<ClientUser> users = clientUserRepository.findByOrgId(orgId);
        List<Map<String, Object>> result = users.stream()
                .filter(u -> u.getRole() == ClientUserRole.CAREGIVER)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "name", u.getName() != null ? u.getName() : u.getUsername(),
                        "phone", u.getMobile() != null ? u.getMobile() : ""))
                .toList();
        return ApiResponse.ok(result);
    }
}
