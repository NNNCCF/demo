package com.ncf.demo.repository;

import com.ncf.demo.entity.GuardianAlarmSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianAlarmSettingRepository extends JpaRepository<GuardianAlarmSetting, Long> {
}
