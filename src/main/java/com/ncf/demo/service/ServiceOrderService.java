package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.domain.ServiceOrderType;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.repository.ServiceOrderRepository;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.web.dto.ServiceOrderCreateRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ServiceOrderService {
    private final ServiceOrderRepository serviceOrderRepository;

    public ServiceOrderService(ServiceOrderRepository serviceOrderRepository) {
        this.serviceOrderRepository = serviceOrderRepository;
    }

    public ServiceOrder create(ServiceOrderCreateRequest request) {
        ServiceOrder order = new ServiceOrder();
        applyRequest(order, request, true);
        return serviceOrderRepository.save(order);
    }

    public List<ServiceOrder> listOrders(Long targetId) {
        return listOrders(targetId, null, null, null, null);
    }

    public List<ServiceOrder> listOrders(
            Long targetId,
            Long orgId,
            ServiceOrderStatus status,
            ServiceOrderType orderType,
            String keyword
    ) {
        List<ServiceOrder> orders = serviceOrderRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
        if (targetId != null) {
            orders = orders.stream()
                    .filter(order -> targetId.equals(order.getTargetId()))
                    .toList();
        }
        if (orgId != null) {
            orders = orders.stream()
                    .filter(order -> orgId.equals(order.getOrgId()))
                    .toList();
        }
        if (status != null) {
            orders = orders.stream()
                    .filter(order -> status == order.getStatus())
                    .toList();
        }
        if (orderType != null) {
            orders = orders.stream()
                    .filter(order -> orderType == order.getOrderType())
                    .toList();
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            orders = orders.stream()
                    .filter(order -> matchesKeyword(order, normalizedKeyword))
                    .toList();
        }
        return orders;
    }

    public ServiceOrder getOrder(Long id) {
        return serviceOrderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(404, "棰勭害璁㈠崟涓嶅瓨鍦?"));
    }

    public ServiceOrder update(Long id, ServiceOrderCreateRequest request) {
        ServiceOrder order = getOrder(id);
        applyRequest(order, request, false);
        return serviceOrderRepository.save(order);
    }

    public void delete(Long id) {
        ServiceOrder order = getOrder(id);
        order.setDeleted(true);
        order.setDeletedAt(Instant.now());
        order.setDeletedBy(SecurityUtil.currentUserId());
        serviceOrderRepository.save(order);
    }

    public void updateStatus(Long id, ServiceOrderStatus status) {
        ServiceOrder order = getOrder(id);
        order.setStatus(status);
        serviceOrderRepository.save(order);
    }

    public ServiceOrder save(ServiceOrder order) {
        return serviceOrderRepository.save(order);
    }

    private void applyRequest(ServiceOrder order, ServiceOrderCreateRequest request, boolean create) {
        order.setOrderType(request.orderType());
        order.setTargetId(request.targetId());
        order.setAppointmentTime(request.appointmentTime());
        order.setStatus(request.status() != null ? request.status() : (create ? ServiceOrderStatus.PENDING : order.getStatus()));
        order.setOrgId(request.orgId());
        order.setFamilyId(request.familyId());
        order.setMemberId(request.memberId());
        order.setGuardianId(request.guardianId());
        order.setCreatedById(request.createdById() != null ? request.createdById() : (create ? SecurityUtil.currentUserId() : order.getCreatedById()));
        order.setNurseId(request.nurseId());
        order.setNurseName(normalizeText(request.nurseName()));
        order.setNursePhone(normalizeText(request.nursePhone()));
        order.setDisplayType(normalizeText(request.displayType()));
        order.setContactName(normalizeText(request.contactName()));
        order.setContactPhone(normalizeText(request.contactPhone()));
        order.setServiceAddress(normalizeText(request.serviceAddress()));
        order.setMedicineList(normalizeText(request.medicineList()));
        order.setRequirement(normalizeText(request.requirement()));
        order.setAcceptTime(request.acceptTime());
        order.setDispatchedBy(normalizeText(request.dispatchedBy()));
        order.setVisitTime(request.visitTime());
        order.setPayAmount(normalizeText(request.payAmount()));
        order.setPayStatus(normalizeText(request.payStatus()));
        order.setVisitRemark(normalizeText(request.visitRemark()));
        if (create && order.getCreatedAt() == null) {
            order.setCreatedAt(Instant.now());
        }
        order.setDeleted(false);
        order.setDeletedAt(null);
        order.setDeletedBy(null);
    }

    private boolean matchesKeyword(ServiceOrder order, String keyword) {
        return String.valueOf(order.getId()).contains(keyword)
                || contains(order.getDisplayType(), keyword)
                || contains(order.getContactName(), keyword)
                || contains(order.getContactPhone(), keyword)
                || contains(order.getServiceAddress(), keyword)
                || contains(order.getMedicineList(), keyword)
                || contains(order.getRequirement(), keyword)
                || contains(order.getNurseName(), keyword)
                || contains(order.getDispatchedBy(), keyword)
                || contains(order.getPayStatus(), keyword)
                || contains(order.getPayAmount(), keyword)
                || contains(order.getStatus() != null ? order.getStatus().name() : null, keyword)
                || contains(order.getOrderType() != null ? order.getOrderType().name() : null, keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
