package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.repository.ServiceOrderRepository;
import com.ncf.demo.web.dto.ServiceOrderCreateRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceOrderService {
    private final ServiceOrderRepository serviceOrderRepository;

    public ServiceOrderService(ServiceOrderRepository serviceOrderRepository) {
        this.serviceOrderRepository = serviceOrderRepository;
    }

    public ServiceOrder create(ServiceOrderCreateRequest request) {
        ServiceOrder order = new ServiceOrder();
        order.setOrderType(request.orderType());
        order.setTargetId(request.targetId());
        order.setAppointmentTime(request.appointmentTime());
        order.setStatus(ServiceOrderStatus.PENDING);
        return serviceOrderRepository.save(order);
    }

    public List<ServiceOrder> listOrders(Long targetId) {
        if (targetId != null) {
            return serviceOrderRepository.findByTargetId(targetId);
        }
        return serviceOrderRepository.findAll();
    }

    public void updateStatus(Long id, ServiceOrderStatus status) {
        ServiceOrder order = serviceOrderRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "预约订单不存在"));
        order.setStatus(status);
        serviceOrderRepository.save(order);
    }
}
