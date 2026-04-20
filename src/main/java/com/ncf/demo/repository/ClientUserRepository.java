package com.ncf.demo.repository;

import com.ncf.demo.entity.ClientUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {
    Optional<ClientUser> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    List<ClientUser> findByOrgId(Long orgId);
}
