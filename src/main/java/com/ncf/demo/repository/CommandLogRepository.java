package com.ncf.demo.repository;

import com.ncf.demo.entity.CommandLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CommandLogRepository extends JpaRepository<CommandLog, Long> {
    List<CommandLog> findBySentAtBetween(Instant start, Instant end);
}
