package com.ncf.demo.web;

import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.service.ServiceOrderService;
import com.ncf.demo.web.dto.ServiceOrderCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-orders")
public class ServiceOrderController {
    private final ServiceOrderService serviceOrderService;

    public ServiceOrderController(ServiceOrderService serviceOrderService) {
        this.serviceOrderService = serviceOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceOrder> create(@RequestBody @Valid ServiceOrderCreateRequest request) {
        return ApiResponse.ok(serviceOrderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ServiceOrder>> list(@RequestParam(required = false) Long targetId) {
        return ApiResponse.ok(serviceOrderService.listOrders(targetId));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ServiceOrderStatus status = ServiceOrderStatus.valueOf(body.get("status"));
        serviceOrderService.updateStatus(id, status);
        return ApiResponse.ok(null);
    }
}
