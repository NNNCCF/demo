package com.ncf.demo.web.dto.mini;

public record NurseLoginResponse(
        String token,
        NurseUserInfo userInfo
) {}
