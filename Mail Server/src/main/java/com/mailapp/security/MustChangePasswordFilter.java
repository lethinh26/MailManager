package com.mailapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bộ lọc kiểm soát người dùng có cờ mustChangePassword=true.
 * Khi mustChangePassword=true, người dùng chỉ được phép gọi:
 * - POST /api/auth/change-password
 * - GET /api/auth/me
 * - Các tài nguyên public hoặc actuator
 * Mọi yêu cầu tới các endpoint khác sẽ bị chặn với mã 403 PASSWORD_CHANGE_REQUIRED.
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final boolean enforce;

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/api/auth/change-password",
            "/api/auth/me",
            "/api/auth/login",
            "/api/auth/register",
            "/api/public/",
            "/actuator/",
            "/v3/api-docs",
            "/swagger-ui"
    );

    public MustChangePasswordFilter(
            ObjectMapper objectMapper,
            @Value("${mailapp.auth.must-change-password-enforce:true}") boolean enforce) {
        this.objectMapper = objectMapper;
        this.enforce = enforce;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enforce) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            if (user.isMustChangePassword()) {
                String path = request.getRequestURI();
                boolean isAllowed = ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
                if (!isAllowed) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");

                    Map<String, Object> errorBody = new LinkedHashMap<>();
                    errorBody.put("timestamp", LocalDateTime.now().toString());
                    errorBody.put("status", HttpStatus.FORBIDDEN.value());
                    errorBody.put("error", "FORBIDDEN");
                    errorBody.put("code", "PASSWORD_CHANGE_REQUIRED");
                    errorBody.put("errorCode", "PASSWORD_CHANGE_REQUIRED");
                    errorBody.put("message", "Tài khoản yêu cầu bắt buộc phải đổi mật khẩu lần đầu trước khi truy cập các tính năng khác.");

                    response.getWriter().write(objectMapper.writeValueAsString(errorBody));
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

