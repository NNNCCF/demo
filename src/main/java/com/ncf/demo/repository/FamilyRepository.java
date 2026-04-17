package com.ncf.demo.repository;

import com.ncf.demo.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FamilyRepository extends JpaRepository<Family, Long> {
    List<Family> findByOrgId(Long orgId);
    List<Family> findByGuardiansId(Long guardianId);
    List<Family> findByCaregiverId(Long caregiverId);
}
