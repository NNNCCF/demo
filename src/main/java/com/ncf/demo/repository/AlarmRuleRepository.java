package com.ncf.demo.repository;

import com.ncf.demo.domain.AlarmType;
import com.ncf.demo.entity.AlarmRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlarmRuleRepository extends JpaRepository<AlarmRule, Long> {
    Optional<AlarmRule> findByAlarmType(AlarmType alarmType);
}
