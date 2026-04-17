package com.ncf.demo.web.dto.mini;

public record NurseListItem(
        Long id,
        String name,
        String phone,
        String role,
        boolean active
) {}
