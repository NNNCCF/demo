package com.ncf.demo.web.dto;

import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ClientUserRequest(
    String mobile,
    String password,
    @NotBlank String name,
    @NotNull ClientUserRole role,
    Gender gender,
    Instant birthday,
    Double height,
    Double weight,
    String wardRole,
    String chronicDisease,
    String remark
) {}
