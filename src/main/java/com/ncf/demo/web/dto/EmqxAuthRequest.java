package com.ncf.demo.web.dto;

public record EmqxAuthRequest(
        String clientid,
        String username
) {
}
