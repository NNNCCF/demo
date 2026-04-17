package com.ncf.demo.config;

import com.ncf.demo.security.JwtAuthFilter;
import com.ncf.demo.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            RateLimitFilter rateLimitFilter
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未授权，请先登录\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login", "/api/register",
                                "/api/auth/**", "/api/institution/list", "/api/institution/nurses",
                                "/api/emqx/**", "/actuator/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/organizations").authenticated()
                        .requestMatchers("/api/mini/**").hasAnyRole("ADMIN", "NURSE", "DOCTOR", "GUARDIAN", "INSTITUTION", "CAREGIVER")
                        .requestMatchers("/api/families/**", "/api/devices/**", "/api/alarms/**", "/api/data/**",
                                "/api/guardian-targets/**", "/api/client-users/**", "/api/doctors/**").hasAnyRole("ADMIN", "GUARDIAN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
