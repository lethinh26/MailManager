package com.mailapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.exception.ErrorResponse;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import com.mailapp.service.SystemSettingService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filter xác thực JWT cho từng request.
 * Đọc header Bearer Token, kiểm tra denylist, nạp User từ DB và thiết lập SecurityContext.
 *
 * @author Lê Thanh Cường
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final JtiDenylistService denylistService;
    private final SystemSettingService settings;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   UserRepository userRepository,
                                   JtiDenylistService denylistService,
                                   SystemSettingService settings,
                                   ObjectMapper objectMapper) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
        this.denylistService = denylistService;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            Claims claims = null;
            Long userId = null;
            try {
                claims = jwtProvider.parseClaims(token);
                userId = jwtProvider.resolveUserId(claims);
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid signed claims are treated like an invalid token.
            }
            if (userId != null) {
                String jti = claims.getId();
                if (jti != null && denylistService.isRevoked(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Optional<User> userOpt = userRepository.findById(userId);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // A renamed account requires a new username-based JWT.
                    // Legacy numeric subjects still resolve solely by stable ID.
                    boolean subjectMatches = claims.get("identityVersion") == null
                            || user.getUsername().equals(claims.getSubject());
                    if (user.isEnabled() && subjectMatches) {
                        if (user.getRole() != Role.ADMIN && isProtectedPath(request)
                                && settings.isMaintenanceMode()) {
                            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                                    503, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                                    "Hệ thống đang bảo trì. Vui lòng thử lại sau.", "MAINTENANCE_MODE",
                                    request.getRequestURI()));
                            return;
                        }
                        String role = "ROLE_" + user.getRole().name();
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority(role)));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        return !(path.equals("/api/auth/login") || path.equals("/api/auth/register")
                || path.startsWith("/api/auth/oauth2/google/")
                || path.startsWith("/api/public/") || path.equals("/api/oauth2/callback")
                || path.equals("/actuator/health") || path.equals("/actuator/info")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui"));
    }
}

