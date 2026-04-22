package com.ncf.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.entity.UserAccount;
import com.ncf.demo.repository.UserRepository;
import com.ncf.demo.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ForcePasswordChangeFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ForcePasswordChangeFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Long currentUserId = SecurityUtil.currentUserId();
        if (currentUserId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/api/account/change-password")) {
            filterChain.doFilter(request, response);
            return;
        }

        UserAccount user = userRepository.findById(currentUserId).orElse(null);
        if (user != null && user.isForcePasswordChange()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.fail(4031, "Please change the initial password before continuing")
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
