package com.ncf.demo.repository;

import com.ncf.demo.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findAllByUsernameOrderByIdAsc(String username);
    boolean existsByUsername(String username);
    Optional<UserAccount> findByPhone(String phone);
    boolean existsByPhone(String phone);
}
