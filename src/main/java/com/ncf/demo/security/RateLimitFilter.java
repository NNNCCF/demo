package com.ncf.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.service.SystemConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    public RateLimitFilter(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                           SystemConfigService systemConfigService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();
        String deviceId = request.getParameter("deviceId");
        if (isHighFrequencyPath(path)) {
            int ipLimit = systemConfigService.getInt("rateLimitPerMinute", 100);
            int deviceLimit = Math.max(1, ipLimit / 2);
            String ipKey = "rl:ip:" + ip + ":" + currentMinute();
            Long ipCount = stringRedisTemplate.opsForValue().increment(ipKey);
            stringRedisTemplate.expire(ipKey, Duration.ofMinutes(2));
            if (ipCount != null && ipCount > ipLimit) {
                reject(response);
                return;
            }
            if (deviceId != null && !deviceId.isBlank()) {
                String deviceKey = "rl:device:" + deviceId + ":" + currentMinute();
                Long deviceCount = stringRedisTemplate.opsForValue().increment(deviceKey);
                stringRedisTemplate.expire(deviceKey, Duration.ofMinutes(2));
                if (deviceCount != null && deviceCount > deviceLimit) {
                    reject(response);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isHighFrequencyPath(String path) {
        return path.contains("/api/login")
                || path.contains("/api/register")
                || path.contains("/api/auth/")
                || path.contains("/api/devices")
                || path.contains("/api/data")
                || path.contains("/api/emqx/auth");
    }

    private String currentMinute() {
        return String.valueOf(System.currentTimeMillis() / 60000);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", 429, "message", "请求过于频繁，请稍后再试")
        ));
    }
}
