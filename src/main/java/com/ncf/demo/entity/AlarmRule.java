package com.ncf.demo.entity;

import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alarm_rule")
public class AlarmRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private AlarmType alarmType;
    private Integer minValue;
    private Integer maxValue;
    private Integer continuousTimes;
    private Integer offlineMinutes;
    @Enumerated(EnumType.STRING)
    private AlarmLevel alarmLevel;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AlarmType getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(AlarmType alarmType) {
        this.alarmType = alarmType;
    }

    public Integer getMinValue() {
        return minValue;
    }

    public void setMinValue(Integer minValue) {
        this.minValue = minValue;
    }

    public Integer getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Integer maxValue) {
        this.maxValue = maxValue;
    }

    public Integer getContinuousTimes() {
        return continuousTimes;
    }

    public void setContinuousTimes(Integer continuousTimes) {
        this.continuousTimes = continuousTimes;
    }

    public Integer getOfflineMinutes() {
        return offlineMinutes;
    }

    public void setOfflineMinutes(Integer offlineMinutes) {
        this.offlineMinutes = offlineMinutes;
    }

    public AlarmLevel getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(AlarmLevel alarmLevel) {
        this.alarmLevel = alarmLevel;
    }
}
