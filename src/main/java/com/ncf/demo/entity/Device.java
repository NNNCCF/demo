package com.ncf.demo.entity;

import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "device")
public class Device {
    @Id
    private String deviceId;
    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;
    private String address;
    private String roomNumber;
    private String homeLocation;
    private String medicalInstitution;
    private String propertyManagement;
    private Boolean bindStatus;
    private Instant bindTime;
    
    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "family_id")
    private Long familyId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @ManyToOne
    @JoinColumn(name = "guardian_id")
    @JsonIgnoreProperties({"devices"})
    private ClientUser guardian;

    @OneToMany(mappedBy = "device")
    private List<Ward> wards;

    @Enumerated(EnumType.STRING)
    private DeviceStatus status;
    private Instant createdAt;
    private Instant lastOnlineAt;
    private Instant lastOfflineAt;
    private Double latitude;
    private Double longitude;

    @jakarta.persistence.Transient
    private Boolean isFall;

    @jakarta.persistence.Transient
    public Boolean getIsFall() {
        return isFall;
    }

    public void setIsFall(Boolean isFall) {
        this.isFall = isFall;
    }

    @jakarta.persistence.Transient
    public String getTargetName() {
        return wards != null && !wards.isEmpty() ? wards.get(0).getName() : null;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(String homeLocation) {
        this.homeLocation = homeLocation;
    }

    public String getMedicalInstitution() {
        return medicalInstitution;
    }

    public void setMedicalInstitution(String medicalInstitution) {
        this.medicalInstitution = medicalInstitution;
    }

    public String getPropertyManagement() {
        return propertyManagement;
    }

    public void setPropertyManagement(String propertyManagement) {
        this.propertyManagement = propertyManagement;
    }

    public Boolean getBindStatus() {
        return bindStatus;
    }

    public void setBindStatus(Boolean bindStatus) {
        this.bindStatus = bindStatus;
    }

    public Instant getBindTime() {
        return bindTime;
    }

    public void setBindTime(Instant bindTime) {
        this.bindTime = bindTime;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }

    public ClientUser getGuardian() {
        return guardian;
    }

    public void setGuardian(ClientUser guardian) {
        this.guardian = guardian;
    }

    public List<Ward> getWards() {
        return wards;
    }

    public void setWards(List<Ward> wards) {
        this.wards = wards;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastOnlineAt() {
        return lastOnlineAt;
    }

    public void setLastOnlineAt(Instant lastOnlineAt) {
        this.lastOnlineAt = lastOnlineAt;
    }

    public Instant getLastOfflineAt() {
        return lastOfflineAt;
    }

    public void setLastOfflineAt(Instant lastOfflineAt) {
        this.lastOfflineAt = lastOfflineAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
