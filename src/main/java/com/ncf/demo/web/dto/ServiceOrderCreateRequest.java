package com.ncf.demo.web.dto;

import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.domain.ServiceOrderType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ServiceOrderCreateRequest(
        @NotNull ServiceOrderType orderType,
        @NotNull Long targetId,
        @NotNull Instant appointmentTime,
        ServiceOrderStatus status,
        Long orgId,
        Long familyId,
        Long memberId,
        Long guardianId,
        Long createdById,
        Long nurseId,
        String nurseName,
        String nursePhone,
        String displayType,
        String contactName,
        String contactPhone,
        String serviceAddress,
        String medicineList,
        String requirement,
        Instant acceptTime,
        String dispatchedBy,
        Instant visitTime,
        String payAmount,
        String payStatus,
        String visitRemark
) {
}
