package com.ncf.demo.repository;

import com.ncf.demo.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findFirstByNameIgnoreCase(String name);
}
