package com.ncf.demo.repository;

import com.ncf.demo.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, Long> {
    Optional<Ward> findTopByOrderByMemberIdDesc();

    @Query("SELECT w FROM Ward w WHERE w.device IS NOT NULL AND w.device.guardian IS NOT NULL AND w.device.guardian.id = :guardianId")
    List<Ward> findByDeviceGuardianId(@Param("guardianId") Long guardianId);

    @Query(value = "SELECT w.* FROM ward w JOIN device d ON w.device_id = d.device_id JOIN family f ON d.family_id = f.id WHERE f.org_id = :orgId", nativeQuery = true)
    List<Ward> findByDeviceFamilyOrgId(@Param("orgId") Long orgId);

    @Query("SELECT w FROM Ward w WHERE w.device IS NOT NULL AND (" +
           "w.device.familyId IN (SELECT f.id FROM Family f WHERE f.orgId = :orgId)" +
           " OR w.device.medicalInstitution = :orgName" +
           " OR w.device.propertyManagement = :orgName)")
    List<Ward> findByOrgIdOrOrgName(@Param("orgId") Long orgId, @Param("orgName") String orgName);
}
