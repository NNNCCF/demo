package com.ncf.demo.repository;

import com.ncf.demo.entity.HealthData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HealthDataRepository extends JpaRepository<HealthData, Long> {

    Optional<HealthData> findTopByMemberIdOrderByRecordTimeDesc(Long memberId);

    List<HealthData> findByMemberIdAndRecordTimeBetweenOrderByRecordTimeAsc(
            Long memberId, Instant start, Instant end);

    List<HealthData> findByMemberIdOrderByRecordTimeDesc(Long memberId);
}
