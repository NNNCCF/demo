package com.ncf.demo.repository;

import com.ncf.demo.entity.DeviceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeviceLogRepository extends JpaRepository<DeviceLog, Long> {
    List<DeviceLog> findByDeviceIdOrderByOccurredAtDesc(String deviceId);
    void deleteByDeviceId(String deviceId);
}
