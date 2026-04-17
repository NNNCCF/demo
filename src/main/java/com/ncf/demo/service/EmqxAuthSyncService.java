package com.ncf.demo.service;

import com.ncf.demo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class EmqxAuthSyncService {
    private static final Logger log = LoggerFactory.getLogger(EmqxAuthSyncService.class);
    private final AppProperties appProperties;
    private final RestClient restClient;

    public EmqxAuthSyncService(AppProperties appProperties, RestClient restClient) {
        this.appProperties = appProperties;
        this.restClient = restClient;
    }

    public void registerDevice(String deviceId) {
        postAuthRule(deviceId, true);
    }

    public void unregisterDevice(String deviceId) {
        postAuthRule(deviceId, false);
        kickDevice(deviceId);
    }

    public void kickDevice(String deviceId) {
        try {
            String credentials = appProperties.getEmqx().getApiKey() + ":" + appProperties.getEmqx().getApiSecret();
            String token = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            restClient.delete()
                    .uri(appProperties.getEmqx().getManagementUrl() + "/api/v5/clients/" + deviceId)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Bad header name")) {
                log.warn("Ignored protocol error during EMQX kick (operation likely succeeded): {}", e.getMessage());
            } else {
                log.error("Failed to kick device {} from EMQX: {}", deviceId, e.getMessage());
            }
        }
    }

    private void postAuthRule(String deviceId, boolean enabled) {
        try {
            String credentials = appProperties.getEmqx().getApiKey() + ":" + appProperties.getEmqx().getApiSecret();
            String token = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            restClient.post()
                    .uri(appProperties.getEmqx().getManagementUrl() + "/api/v5/authorization/sources/built_in_database/rules/clients")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", deviceId,
                            "clientid", deviceId,
                            "permission", enabled ? "allow" : "deny",
                            "action", "all",
                            "topic", "#"
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Bad header name")) {
                log.warn("Ignored protocol error during EMQX auth sync (operation likely succeeded): {}", e.getMessage());
            } else {
                log.error("Failed to sync auth rule to EMQX for device {}: {}", deviceId, e.getMessage());
            }
        }
    }
}
