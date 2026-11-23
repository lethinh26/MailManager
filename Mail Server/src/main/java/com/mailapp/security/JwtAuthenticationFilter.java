package com.mailapp.security;

import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
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

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   UserRepository userRepository,
                                   JtiDenylistService denylistService) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
        this.denylistService = denylistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            if (jwtProvider.validateToken(token)) {
                String jti = jwtProvider.getJti(token);
                if (jti != null && denylistService.isRevoked(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = jwtProvider.getUserId(token);
                Optional<User> userOpt = userRepository.findById(userId);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (user.isEnabled()) {
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
}

