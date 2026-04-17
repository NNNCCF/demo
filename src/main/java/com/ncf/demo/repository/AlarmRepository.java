package com.ncf.demo.repository;

import com.ncf.demo.domain.AlarmHandleStatus;
import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.entity.Alarm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {
    List<Alarm> findByTargetIdAndOccurredAtBetween(Long targetId, Instant start, Instant end);

    List<Alarm> findByTargetIdAndAlarmLevelAndHandleStatusAndOccurredAtBetween(
            Long targetId, AlarmLevel alarmLevel, AlarmHandleStatus handleStatus, Instant start, Instant end
    );

    List<Alarm> findByTargetIdInAndOccurredAtBetween(List<Long> targetIds, Instant start, Instant end);

    List<Alarm> findByOccurredAtBetween(Instant start, Instant end);

    List<Alarm> findByHandleStatus(AlarmHandleStatus handleStatus);

    List<Alarm> findByTargetId(Long targetId);

    List<Alarm> findByTargetIdIn(List<Long> targetIds);

    List<Alarm> findByHandleStatusAndTargetIdIn(AlarmHandleStatus handleStatus, List<Long> targetIds);

    List<Alarm> findAllByOrderByOccurredAtDesc();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Alarm a SET a.handleStatus = com.ncf.demo.domain.AlarmHandleStatus.HANDLED, a.handledBy = :handlerId, a.handledAt = :now, a.handleRemark = :remark WHERE a.handleStatus = com.ncf.demo.domain.AlarmHandleStatus.UNHANDLED")
    int handleAllUnhandled(@Param("handlerId") Long handlerId, @Param("now") Instant now, @Param("remark") String remark);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Alarm a WHERE a.occurredAt < :before AND a.handleStatus = com.ncf.demo.domain.AlarmHandleStatus.HANDLED")
    int deleteHandledBefore(@Param("before") Instant before);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Alarm a WHERE a.occurredAt < :before")
    int deleteAllBefore(@Param("before") Instant before);
}
