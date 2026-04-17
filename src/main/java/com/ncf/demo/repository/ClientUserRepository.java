package com.ncf.demo.repository;

import com.ncf.demo.entity.ClientUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {
    Optional<ClientUser> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    List<ClientUser> findByOrgId(Long orgId);

    @Modifying
    @Query(value = "DELETE FROM client_user_device WHERE client_user_id = :userId", nativeQuery = true)
    void deleteDeviceLinks(@Param("userId") Long userId);
}
