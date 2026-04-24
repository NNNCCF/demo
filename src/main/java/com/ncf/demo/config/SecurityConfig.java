package com.ncf.demo.config;

import com.ncf.demo.security.ForcePasswordChangeFilter;
import com.ncf.demo.security.JwtAuthFilter;
import com.ncf.demo.security.MiniAppSignatureFilter;
import com.ncf.demo.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            RateLimitFilter rateLimitFilter,
            ForcePasswordChangeFilter forcePasswordChangeFilter,
            MiniAppSignatureFilter miniAppSignatureFilter,
            AppProperties appProperties
    ) throws Exception {
        List<String> publicEndpoints = new ArrayList<>(List.of(
                "/api/login", "/api/register",
                "/api/auth/**", "/api/institution/list", "/api/institution/nurses",
                "/api/emqx/auth",
                "/actuator/health", "/actuator/health/**"
        ));
        if (appProperties.getExposure().isSwaggerPublic()) {
            publicEndpoints.add("/swagger-ui/**");
            publicEndpoints.add("/swagger-ui.html");
            publicEndpoints.add("/v3/api-docs/**");
        }
        if (appProperties.getExposure().isActuatorPublic()) {
            publicEndpoints.add("/actuator/**");
        }

        http.csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .cacheControl(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized, please login first\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicEndpoints.toArray(String[]::new)).permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/news/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/service-orders/**").hasAnyRole("ADMIN", "GUARDIAN")
                        .requestMatchers("/api/service-orders/**", "/api/news/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/organizations").authenticated()
                        .requestMatchers("/api/mini/**").hasAnyRole("ADMIN", "NURSE", "DOCTOR", "GUARDIAN", "INSTITUTION", "CAREGIVER")
                        .requestMatchers("/api/families/**", "/api/devices/**", "/api/alarms/**", "/api/data/**",
                                "/api/guardian-targets/**", "/api/client-users/**", "/api/doctors/**").hasAnyRole("ADMIN", "GUARDIAN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(miniAppSignatureFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, MiniAppSignatureFilter.class)
                .addFilterAfter(jwtAuthFilter, RateLimitFilter.class)
                .addFilterAfter(forcePasswordChangeFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
