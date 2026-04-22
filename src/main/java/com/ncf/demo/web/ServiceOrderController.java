package com.ncf.demo.web;

import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.domain.ServiceOrderType;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.service.ServiceOrderService;
import com.ncf.demo.web.dto.ServiceOrderCreateRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-orders")
public class ServiceOrderController {
    private final ServiceOrderService serviceOrderService;
    private final ClientUserRepository clientUserRepository;

    public ServiceOrderController(
            ServiceOrderService serviceOrderService,
            ClientUserRepository clientUserRepository
    ) {
        this.serviceOrderService = serviceOrderService;
        this.clientUserRepository = clientUserRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceOrder> create(@RequestBody @Valid ServiceOrderCreateRequest request) {
        return ApiResponse.ok(serviceOrderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ServiceOrder>> list(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(required = false) ServiceOrderType orderType,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(serviceOrderService.listOrders(targetId, orgId, status, orderType, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceOrder> detail(@PathVariable Long id) {
        return ApiResponse.ok(serviceOrderService.getOrder(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ServiceOrder> update(@PathVariable Long id, @RequestBody @Valid ServiceOrderCreateRequest request) {
        return ApiResponse.ok(serviceOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        serviceOrderService.delete(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ServiceOrderStatus status = ServiceOrderStatus.valueOf(body.get("status"));
        serviceOrderService.updateStatus(id, status);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/dispatch")
    public ApiResponse<Void> dispatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ServiceOrder order = serviceOrderService.getOrder(id);
        Long nurseId = body.get("nurseId") != null ? Long.valueOf(body.get("nurseId").toString()) : null;
        String nurseName = body.get("nurseName") != null ? body.get("nurseName").toString() : null;
        if (nurseId != null) {
            ClientUser nurse = clientUserRepository.findById(nurseId).orElse(null);
            if (nurse != null) {
                order.setNurseId(nurseId);
                order.setNurseName(nurse.getName());
                order.setNursePhone(nurse.getMobile());
                order.setAcceptTime(Instant.now());
            }
        } else {
            order.setNurseId(null);
            order.setNursePhone(null);
            order.setAcceptTime(null);
        }
        order.setNurseName(nurseName != null && !nurseName.isBlank() ? nurseName.trim() : order.getNurseName());
        order.setDispatchedBy(resolveDispatcherName());
        serviceOrderService.save(order);
        return ApiResponse.ok(null);
    }

    @GetMapping("/nurses")
    public ApiResponse<List<Map<String, Object>>> listNurses(@RequestParam(required = false) Long orgId) {
        List<ClientUser> users = orgId != null
                ? clientUserRepository.findByOrgId(orgId)
                : clientUserRepository.findAll();
        List<Map<String, Object>> result = users.stream()
                .filter(u -> u.getRole() == ClientUserRole.CAREGIVER)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "name", u.getName() != null ? u.getName() : "",
                        "phone", u.getMobile() != null ? u.getMobile() : ""))
                .toList();
        return ApiResponse.ok(result);
    }

    private String resolveDispatcherName() {
        Long currentUserId = SecurityUtil.currentUserId();
        if (currentUserId == null) {
            return "system";
        }
        return clientUserRepository.findById(currentUserId)
                .map(ClientUser::getName)
                .filter(name -> !name.isBlank())
                .orElse("admin-" + currentUserId);
    }
}
