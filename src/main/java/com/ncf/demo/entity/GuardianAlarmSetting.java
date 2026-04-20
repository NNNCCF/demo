package com.ncf.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "guardian_alarm_setting")
public class GuardianAlarmSetting {
    @Id
    @Column(name = "guardian_id")
    private Long guardianId;

    private boolean hrAlert = true;
    private boolean bpAlert = true;
    private boolean fallAlert = true;
    private boolean bedAlert = false;
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = Instant.now();
    }

    public Long getGuardianId() {
        return guardianId;
    }

    public void setGuardianId(Long guardianId) {
        this.guardianId = guardianId;
    }

    public boolean isHrAlert() {
        return hrAlert;
    }

    public void setHrAlert(boolean hrAlert) {
        this.hrAlert = hrAlert;
    }

    public boolean isBpAlert() {
        return bpAlert;
    }

    public void setBpAlert(boolean bpAlert) {
        this.bpAlert = bpAlert;
    }

    public boolean isFallAlert() {
        return fallAlert;
    }

    public void setFallAlert(boolean fallAlert) {
        this.fallAlert = fallAlert;
    }

    public boolean isBedAlert() {
        return bedAlert;
    }

    public void setBedAlert(boolean bedAlert) {
        this.bedAlert = bedAlert;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
