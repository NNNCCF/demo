package com.ncf.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Jwt jwt = new Jwt();
    private final Emqx emqx = new Emqx();
    private final Tdengine tdengine = new Tdengine();
    private final Miniapp miniapp = new Miniapp();
    private final Exposure exposure = new Exposure();

    private boolean mockDataEnabled = false;
    private String adminPasswordHash;

    public Jwt getJwt() {
        return jwt;
    }

    public Emqx getEmqx() {
        return emqx;
    }

    public Tdengine getTdengine() {
        return tdengine;
    }

    public Miniapp getMiniapp() {
        return miniapp;
    }

    public Exposure getExposure() {
        return exposure;
    }

    public boolean isMockDataEnabled() {
        return mockDataEnabled;
    }

    public void setMockDataEnabled(boolean mockDataEnabled) {
        this.mockDataEnabled = mockDataEnabled;
    }

    public String getAdminPasswordHash() {
        return adminPasswordHash;
    }

    public void setAdminPasswordHash(String adminPasswordHash) {
        this.adminPasswordHash = adminPasswordHash;
    }

    public static class Jwt {
        private String secret;
        private long expireSeconds = 86400;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(long expireSeconds) {
            this.expireSeconds = expireSeconds;
        }
    }

    public static class Emqx {
        private String brokerUrl = "tcp://localhost:1883";
        private String username = "";
        private String password = "";
        private String clientId = "health-backend";
        private String managementUrl = "http://localhost:18083";
        private String apiKey;
        private String apiSecret;

        public String getBrokerUrl() {
            return brokerUrl;
        }

        public void setBrokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getManagementUrl() {
            return managementUrl;
        }

        public void setManagementUrl(String managementUrl) {
            this.managementUrl = managementUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }
    }

    public static class Tdengine {
        private String url = "jdbc:TAOS://localhost:6030";
        private String username = "root";
        private String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Miniapp {
        private boolean signatureEnabled = true;
        private String clientId = "zkab-miniapp";
        private String sharedSecret = "change-me-miniapp-shared-secret";
        private long allowedClockSkewSeconds = 300;
        private long nonceTtlSeconds = 300;

        public boolean isSignatureEnabled() {
            return signatureEnabled;
        }

        public void setSignatureEnabled(boolean signatureEnabled) {
            this.signatureEnabled = signatureEnabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
        }

        public long getAllowedClockSkewSeconds() {
            return allowedClockSkewSeconds;
        }

        public void setAllowedClockSkewSeconds(long allowedClockSkewSeconds) {
            this.allowedClockSkewSeconds = allowedClockSkewSeconds;
        }

        public long getNonceTtlSeconds() {
            return nonceTtlSeconds;
        }

        public void setNonceTtlSeconds(long nonceTtlSeconds) {
            this.nonceTtlSeconds = nonceTtlSeconds;
        }
    }

    public static class Exposure {
        private boolean swaggerPublic = false;
        private boolean actuatorPublic = false;

        public boolean isSwaggerPublic() {
            return swaggerPublic;
        }

        public void setSwaggerPublic(boolean swaggerPublic) {
            this.swaggerPublic = swaggerPublic;
        }

        public boolean isActuatorPublic() {
            return actuatorPublic;
        }

        public void setActuatorPublic(boolean actuatorPublic) {
            this.actuatorPublic = actuatorPublic;
        }
    }
}
