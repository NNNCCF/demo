package com.ncf.demo.entity;

import com.ncf.demo.domain.HealthStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "health_data")
public class HealthData {
    @Id
    private Long dataId;
    private Long memberId;
    private Double respRateAvg;
    private Double respRateMax;
    private Double respRateMin;
    private Double heartRateAvg;
    private Double heartRateMax;
    private Double heartRateMin;
    private Boolean fallStatus;
    private Boolean locationStatus;
    private Instant recordTime;
    @Enumerated(EnumType.STRING)
    private HealthStatus status;

    public Long getDataId() {
        return dataId;
    }

    public void setDataId(Long dataId) {
        this.dataId = dataId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Double getRespRateAvg() {
        return respRateAvg;
    }

    public void setRespRateAvg(Double respRateAvg) {
        this.respRateAvg = respRateAvg;
    }

    public Double getRespRateMax() {
        return respRateMax;
    }

    public void setRespRateMax(Double respRateMax) {
        this.respRateMax = respRateMax;
    }

    public Double getRespRateMin() {
        return respRateMin;
    }

    public void setRespRateMin(Double respRateMin) {
        this.respRateMin = respRateMin;
    }

    public Double getHeartRateAvg() {
        return heartRateAvg;
    }

    public void setHeartRateAvg(Double heartRateAvg) {
        this.heartRateAvg = heartRateAvg;
    }

    public Double getHeartRateMax() {
        return heartRateMax;
    }

    public void setHeartRateMax(Double heartRateMax) {
        this.heartRateMax = heartRateMax;
    }

    public Double getHeartRateMin() {
        return heartRateMin;
    }

    public void setHeartRateMin(Double heartRateMin) {
        this.heartRateMin = heartRateMin;
    }

    public Boolean getFallStatus() {
        return fallStatus;
    }

    public void setFallStatus(Boolean fallStatus) {
        this.fallStatus = fallStatus;
    }

    public Boolean getLocationStatus() {
        return locationStatus;
    }

    public void setLocationStatus(Boolean locationStatus) {
        this.locationStatus = locationStatus;
    }

    public Instant getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(Instant recordTime) {
        this.recordTime = recordTime;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }
}
