package com.mailapp.security;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JtiDenylistService denylistService;

    @Test
    @DisplayName("Endpoint yêu cầu xác thực trả về 401 UNAUTHORIZED khi không có Bearer token")
    void testMissingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/gmail-accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Endpoint yêu cầu xác thực trả về 401 UNAUTHORIZED khi token giả hoặc sai chữ ký")
    void testInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/gmail-accounts")
                        .header("Authorization", "Bearer invalid-tampered-token-12345"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void expiredCorrectlySignedJwtCannotAccessMe() throws Exception {
        SecretKey signingKey = (SecretKey) ReflectionTestUtils.getField(jwtProvider, "signingKey");
        Instant now = Instant.now();
        String expired = Jwts.builder().subject("42").claim("username", "expired_user")
                .claim("role", "USER").issuedAt(Date.from(now.minusSeconds(3600)))
                .expiration(Date.from(now.minusSeconds(60))).signWith(signingKey, Jwts.SIG.HS256).compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
        verify(userRepository, never()).findById(42L);
    }

    @Test
    void legacyNumericSubjectUsesDatabaseIdentityAndRole() throws Exception {
        SecretKey signingKey = (SecretKey) ReflectionTestUtils.getField(jwtProvider, "signingKey");
        User user = new User(42L, "legacy_user", "legacy@example.com", Role.USER);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        String legacy = Jwts.builder().subject("42").claim("uid", 42L)
                .claim("username", "legacy_user").claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + legacy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + legacy))
                .andExpect(status().isForbidden());
    }

    @Test
    void usernameSubjectCannotAuthenticateAfterDatabaseIdentityChanges() throws Exception {
        User former = new User(42L, "former_name", "former@example.com", Role.USER);
        String token = jwtProvider.generateToken(former);
        User current = new User(42L, "current_name", "current@example.com", Role.USER);
        when(userRepository.findById(42L)).thenReturn(Optional.of(current));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token hợp lệ nhưng user trong DB bị khóa (enabled=false) thì bị từ chối 401")
    void testDisabledUserReturns401() throws Exception {
        User lockedUser = new User(50L, "locked", "locked@ptit.edu.vn", Role.USER);
        lockedUser.setEnabled(false); // Đã bị Admin khóa

        String token = jwtProvider.generateToken(lockedUser);
        when(userRepository.findById(50L)).thenReturn(Optional.of(lockedUser));

        mockMvc.perform(get("/api/gmail-accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("User thường (ROLE_USER) gọi endpoint /api/admin/** bị chặn 403 FORBIDDEN")
    void testUserRoleCannotAccessAdminEndpoints() throws Exception {
        User normalUser = new User(10L, "normal_user", "user@ptit.edu.vn", Role.USER);
        normalUser.setEnabled(true);

        String token = jwtProvider.generateToken(normalUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(normalUser));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Token có jti bị thu hồi trong JtiDenylistService bị từ chối 401")
    void testRevokedJtiReturns401() throws Exception {
        User user = new User(10L, "test_user", "test@ptit.edu.vn", Role.USER);
        user.setEnabled(true);

        String token = jwtProvider.generateToken(user);
        String jti = jwtProvider.getJti(token);

        when(denylistService.isRevoked(jti)).thenReturn(true); // JTI đã bị đưa vào denylist
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/gmail-accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint công khai /api/oauth2/callback truy cập bình thường không cần token")
    void testPublicEndpointsDoNotRequireToken() throws Exception {
        mockMvc.perform(get("/api/oauth2/callback"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}

