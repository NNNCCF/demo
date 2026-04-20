package com.ncf.demo.entity;

import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.domain.ServiceOrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "service_order")
public class ServiceOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ServiceOrderType orderType;

    /** Legacy field kept for admin panel compatibility */
    private Long targetId;

    private Instant appointmentTime;

    @Enumerated(EnumType.STRING)
    private ServiceOrderStatus status;

    // ── Mini-app extended fields ──────────────────────────────────────────────

    @Column(name = "org_id")
    private Long orgId;   // 冗余字段，来自 family.orgId，便于机构端按 orgId 过滤

    @Column(name = "family_id")
    private Long familyId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "guardian_id")
    private Long guardianId;

    /** UserAccount.id of the nurse/doctor who created/requested this appointment */
    @Column(name = "created_by_id")
    private Long createdById;

    /** UserAccount.id of the nurse assigned to execute this appointment */
    @Column(name = "nurse_id")
    private Long nurseId;

    @Column(name = "nurse_name", length = 100)
    private String nurseName;

    @Column(name = "nurse_phone", length = 20)
    private String nursePhone;

    /** Chinese display label, e.g. "护理服务" */
    @Column(name = "display_type", length = 60)
    private String displayType;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "service_address", columnDefinition = "TEXT")
    private String serviceAddress;

    @Column(name = "medicine_list", columnDefinition = "TEXT")
    private String medicineList;

    @Column(columnDefinition = "TEXT")
    private String requirement;

    @Column(name = "accept_time")
    private Instant acceptTime;

    @Column(name = "dispatched_by", length = 100)
    private String dispatchedBy;

    @Column(name = "visit_time")
    private Instant visitTime;

    @Column(name = "pay_amount", length = 50)
    private String payAmount;

    @Column(name = "pay_status", length = 50)
    private String payStatus;

    @Column(name = "visit_remark", columnDefinition = "TEXT")
    private String visitRemark;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = ServiceOrderStatus.PENDING;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ServiceOrderType getOrderType() { return orderType; }
    public void setOrderType(ServiceOrderType orderType) { this.orderType = orderType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public Instant getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(Instant appointmentTime) { this.appointmentTime = appointmentTime; }

    public ServiceOrderStatus getStatus() { return status; }
    public void setStatus(ServiceOrderStatus status) { this.status = status; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public Long getGuardianId() { return guardianId; }
    public void setGuardianId(Long guardianId) { this.guardianId = guardianId; }

    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long createdById) { this.createdById = createdById; }

    public Long getNurseId() { return nurseId; }
    public void setNurseId(Long nurseId) { this.nurseId = nurseId; }

    public String getNurseName() { return nurseName; }
    public void setNurseName(String nurseName) { this.nurseName = nurseName; }

    public String getNursePhone() { return nursePhone; }
    public void setNursePhone(String nursePhone) { this.nursePhone = nursePhone; }

    public String getDisplayType() { return displayType; }
    public void setDisplayType(String displayType) { this.displayType = displayType; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getServiceAddress() { return serviceAddress; }
    public void setServiceAddress(String serviceAddress) { this.serviceAddress = serviceAddress; }

    public String getMedicineList() { return medicineList; }
    public void setMedicineList(String medicineList) { this.medicineList = medicineList; }

    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }

    public Instant getAcceptTime() { return acceptTime; }
    public void setAcceptTime(Instant acceptTime) { this.acceptTime = acceptTime; }

    public String getDispatchedBy() { return dispatchedBy; }
    public void setDispatchedBy(String dispatchedBy) { this.dispatchedBy = dispatchedBy; }

    public Instant getVisitTime() { return visitTime; }
    public void setVisitTime(Instant visitTime) { this.visitTime = visitTime; }

    public String getPayAmount() { return payAmount; }
    public void setPayAmount(String payAmount) { this.payAmount = payAmount; }

    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }

    public String getVisitRemark() { return visitRemark; }
    public void setVisitRemark(String visitRemark) { this.visitRemark = visitRemark; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
