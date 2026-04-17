package com.ncf.demo.web.dto;

public record EmqxAuthResponse(
        String result,
        boolean is_superuser
) {
}
