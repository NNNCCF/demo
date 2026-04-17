package com.ncf.demo.web.dto.mini;

import java.util.List;

public record GuardianLoginResponse(
        String token,
        GuardianUserInfo userInfo,
        List<Object> devices
) {}
