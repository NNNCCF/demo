package com.ncf.demo.web.dto.mini;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInstitutionAdminRequest(
        @NotBlank String phone,
        @NotBlank String password,
        @NotBlank String name,
        @NotNull  Long orgId
) {}
