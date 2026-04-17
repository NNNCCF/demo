package com.ncf.demo.config;

import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;
import com.ncf.demo.entity.AlarmRule;
import com.ncf.demo.repository.AlarmRuleRepository;
import com.ncf.demo.service.TdengineService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapInitializer {
    @Bean
    public CommandLineRunner initTablesAndRules(TdengineService tdengineService, AlarmRuleRepository alarmRuleRepository) {
        return args -> {
            tdengineService.initTables();
            ensureHeartRateRule(alarmRuleRepository);
            ensureBreathRateRule(alarmRuleRepository);
            ensureFallRule(alarmRuleRepository);
            ensureOfflineRule(alarmRuleRepository);
        };
    }

    private void ensureHeartRateRule(AlarmRuleRepository repository) {
        repository.findByAlarmType(AlarmType.HEART_RATE).orElseGet(() -> {
            AlarmRule rule = new AlarmRule();
            rule.setAlarmType(AlarmType.HEART_RATE);
            rule.setMinValue(40);
            rule.setMaxValue(120);
            rule.setContinuousTimes(3);
            rule.setAlarmLevel(AlarmLevel.EMERGENCY);
            return repository.save(rule);
        });
    }

    private void ensureBreathRateRule(AlarmRuleRepository repository) {
        repository.findByAlarmType(AlarmType.BREATH_RATE).orElseGet(() -> {
            AlarmRule rule = new AlarmRule();
            rule.setAlarmType(AlarmType.BREATH_RATE);
            rule.setMinValue(10);
            rule.setMaxValue(30);
            rule.setContinuousTimes(3);
            rule.setAlarmLevel(AlarmLevel.EMERGENCY);
            return repository.save(rule);
        });
    }

    private void ensureFallRule(AlarmRuleRepository repository) {
        repository.findByAlarmType(AlarmType.FALL).orElseGet(() -> {
            AlarmRule rule = new AlarmRule();
            rule.setAlarmType(AlarmType.FALL);
            rule.setAlarmLevel(AlarmLevel.EMERGENCY);
            return repository.save(rule);
        });
    }

    private void ensureOfflineRule(AlarmRuleRepository repository) {
        repository.findByAlarmType(AlarmType.DEVICE_OFFLINE).orElseGet(() -> {
            AlarmRule rule = new AlarmRule();
            rule.setAlarmType(AlarmType.DEVICE_OFFLINE);
            rule.setOfflineMinutes(30);
            rule.setAlarmLevel(AlarmLevel.NORMAL);
            return repository.save(rule);
        });
    }
}
