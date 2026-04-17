package com.ncf.demo.repository;

import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;

public interface DeviceRepository extends JpaRepository<Device, String> {
    List<Device> findByStatus(DeviceStatus status);

    List<Device> findByTargetId(Long targetId);

    List<Device> findByFamilyId(Long familyId);

    List<Device> findByGuardianId(Long guardianId);

    List<Device> findByDoctorId(Long doctorId);

    @Query("SELECT d FROM Device d LEFT JOIN Family f ON d.familyId = f.id WHERE f.orgId = :orgId OR d.medicalInstitution = :orgName OR d.propertyManagement = :orgName")
    List<Device> findByOrgIdOrOrgName(@Param("orgId") Long orgId, @Param("orgName") String orgName);

    @Modifying
    @Query("UPDATE Device d SET d.guardian = null WHERE d.guardian.id = :guardianId")
    void clearGuardianByGuardianId(@Param("guardianId") Long guardianId);

    @Modifying
    @Query("UPDATE Device d SET d.status = com.ncf.demo.domain.DeviceStatus.OFFLINE, d.lastOfflineAt = :now WHERE d.deviceId IN :ids")
    void markOfflineBatch(@Param("ids") List<String> ids, @Param("now") java.time.Instant now);
}
