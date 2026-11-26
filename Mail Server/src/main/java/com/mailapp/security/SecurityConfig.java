package com.mailapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cấu hình bảo mật Spring Security chính thức cho ứng dụng Mail Server.
 * Thiết lập cơ chế Stateless với JWT Bearer Authentication, phân quyền Role,
 * kiểm soát đổi mật khẩu bắt buộc và trả về lỗi chuẩn JSON 401/403.
 *
 * @author Lê Thanh Cường
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MustChangePasswordFilter mustChangePasswordFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            MustChangePasswordFilter mustChangePasswordFilter,
            ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.mustChangePasswordFilter = mustChangePasswordFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable) // CORS đóng mặc định theo Same-Origin Policy
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .permissionsPolicy(p -> p.policy("geolocation=(), microphone=(), camera=()"))
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");

                    Map<String, Object> errorBody = new LinkedHashMap<>();
                    errorBody.put("timestamp", LocalDateTime.now().toString());
                    errorBody.put("status", HttpServletResponse.SC_UNAUTHORIZED);
                    errorBody.put("error", "UNAUTHORIZED");
                    errorBody.put("code", "UNAUTHORIZED");
                    errorBody.put("errorCode", "UNAUTHORIZED");
                    errorBody.put("path", request.getRequestURI());
                    errorBody.put("message", "Yêu cầu xác thực. Vui lòng cung cấp Bearer Token hợp lệ.");

                    response.getWriter().write(objectMapper.writeValueAsString(errorBody));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");

                    Map<String, Object> errorBody = new LinkedHashMap<>();
                    errorBody.put("timestamp", LocalDateTime.now().toString());
                    errorBody.put("status", HttpServletResponse.SC_FORBIDDEN);
                    errorBody.put("error", "FORBIDDEN");
                    errorBody.put("code", "FORBIDDEN");
                    errorBody.put("errorCode", "FORBIDDEN");
                    errorBody.put("path", request.getRequestURI());
                    errorBody.put("message", "Bạn không có quyền truy cập tài nguyên này.");

                    response.getWriter().write(objectMapper.writeValueAsString(errorBody));
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/oauth2/google/**",
                    "/api/public/**",
                    "/api/oauth2/callback",
                    "/actuator/health",
                    "/actuator/info",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers("/api/admin/**", "/api/dev/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(mustChangePasswordFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
