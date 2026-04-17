package com.ncf.demo.repository;

import com.ncf.demo.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findBySentAtBetween(Instant start, Instant end);

    @Modifying
    @Query("DELETE FROM NotificationLog n WHERE n.sentAt < :before")
    int deleteBysentAtBefore(@Param("before") Instant before);
}
