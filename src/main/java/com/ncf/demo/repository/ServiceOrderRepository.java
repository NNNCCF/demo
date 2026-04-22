package com.ncf.demo.repository;

import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.entity.ServiceOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {
    List<ServiceOrder> findByTargetId(Long targetId);

    List<ServiceOrder> findAllByOrderByCreatedAtDesc();

    List<ServiceOrder> findByCreatedByIdOrderByCreatedAtDesc(Long createdById);

    List<ServiceOrder> findByNurseIdOrderByCreatedAtDesc(Long nurseId);

    List<ServiceOrder> findByStatusOrderByCreatedAtDesc(ServiceOrderStatus status);

    List<ServiceOrder> findByOrgIdOrderByCreatedAtDesc(Long orgId);

    List<ServiceOrder> findByOrgIdAndNurseIdIsNullOrderByCreatedAtDesc(Long orgId);

    Optional<ServiceOrder> findByIdAndDeletedFalse(Long id);

    List<ServiceOrder> findByTargetIdAndDeletedFalse(Long targetId);

    List<ServiceOrder> findAllByDeletedFalseOrderByCreatedAtDesc();

    List<ServiceOrder> findByCreatedByIdAndDeletedFalseOrderByCreatedAtDesc(Long createdById);

    List<ServiceOrder> findByNurseIdAndDeletedFalseOrderByCreatedAtDesc(Long nurseId);

    List<ServiceOrder> findByStatusAndDeletedFalseOrderByCreatedAtDesc(ServiceOrderStatus status);

    List<ServiceOrder> findByOrgIdAndDeletedFalseOrderByCreatedAtDesc(Long orgId);

    List<ServiceOrder> findByOrgIdAndNurseIdIsNullAndDeletedFalseOrderByCreatedAtDesc(Long orgId);
}
