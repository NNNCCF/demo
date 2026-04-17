package com.ncf.demo.repository;

import com.ncf.demo.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByOrgId(Long orgId);
    Optional<Doctor> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
}
