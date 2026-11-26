package com.mailapp.service;

import com.mailapp.dto.LoginRequest;
import com.mailapp.dto.LoginResponse;
import com.mailapp.exception.AppException;
import com.mailapp.exception.RateLimitException;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.JwtProvider;
import com.mailapp.security.LoginRateLimiter;
import com.mailapp.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    private AuthServiceImpl authService;

    private static final String CLIENT_IP = "192.168.1.50";

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                jwtProvider,
                systemSettingService,
                auditLogService,
                loginRateLimiter
        );
    }

    @Test
    @DisplayName("Đăng nhập thành công: Trả JWT, đúng role, reset rate limiter, audit log LOGIN")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("cuongle", "ValidPassword123!");
        User user = new User(1L, "cuongle", "cuongle@ptit.edu.vn", Role.USER);
        user.setPasswordHash("hashed_password");
        user.setEnabled(true);

        when(userRepository.findByUsername("cuongle")).thenReturn(Optional.of(user));
        when(userRepository.lockUserById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", "hashed_password")).thenReturn(true);
        when(systemSettingService.isMaintenanceMode()).thenReturn(false);
        when(jwtProvider.generateToken(user)).thenReturn("mock.jwt.token");
        when(jwtProvider.getExpirationSeconds()).thenReturn(86400L);

        LoginResponse response = authService.login(request, CLIENT_IP);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(86400L, response.expiresIn());
        assertEquals("cuongle", response.user().username());
        assertEquals(Role.USER, response.user().role());

        verify(loginRateLimiter).checkLimit("cuongle", CLIENT_IP);
        verify(loginRateLimiter).recordSuccess("cuongle", CLIENT_IP);
        verify(auditLogService).log(1L, "LOGIN", "SUCCESS", CLIENT_IP);
    }

    @Test
    @DisplayName("User không tồn tại: Chạy phép BCrypt với hash giả để bảo đảm thời gian phản hồi tương đương (constant time)")
    void testLoginUnknownUserRunsDummyBcrypt() {
        LoginRequest request = new LoginRequest("unknown_user", "AnyPassword123!");
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, CLIENT_IP));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("Sai tài khoản hoặc mật khẩu", ex.getMessage());

        // Khẳng định passwordEncoder.matches ĐÃ ĐƯỢC GỌI với hash giả
        verify(passwordEncoder).matches(eq("AnyPassword123!"), anyString());
        verify(loginRateLimiter).recordFailedAttempt("unknown_user", CLIENT_IP);
        verify(auditLogService).log(isNull(), eq("LOGIN_FAILED"), eq("UNKNOWN_USER"), eq(CLIENT_IP));
    }

    @Test
    @DisplayName("Sai mật khẩu: Ném UNAUTHORIZED, ghi nhận rate limit và audit log")
    void testLoginWrongPassword() {
        LoginRequest request = new LoginRequest("cuongle", "WrongPassword!");
        User user = new User(1L, "cuongle", "cuongle@ptit.edu.vn", Role.USER);
        user.setPasswordHash("hashed_password");

        when(userRepository.findByUsername("cuongle")).thenReturn(Optional.of(user));
        when(userRepository.lockUserById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "hashed_password")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, CLIENT_IP));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());

        verify(loginRateLimiter).recordFailedAttempt("cuongle", CLIENT_IP);
        verify(auditLogService).log(1L, "LOGIN_FAILED", "BAD_PASSWORD", CLIENT_IP);
    }

    @Test
    @DisplayName("Tài khoản bị khóa (enabled=false): Chỉ ném ACCOUNT_DISABLED sau khi mật khẩu đã đúng")
    void testLoginAccountLockedOnlyAfterPasswordMatches() {
        LoginRequest request = new LoginRequest("locked_user", "CorrectPassword123!");
        User user = new User(2L, "locked_user", "locked@ptit.edu.vn", Role.USER);
        user.setPasswordHash("hashed_password");
        user.setEnabled(false); // Bị khóa

        when(userRepository.findByUsername("locked_user")).thenReturn(Optional.of(user));
        when(userRepository.lockUserById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CorrectPassword123!", "hashed_password")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, CLIENT_IP));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("ACCOUNT_DISABLED", ex.getCode());
        assertTrue(ex.getMessage().contains("khóa"));

        verify(auditLogService).log(2L, "LOGIN_FAILED", "ACCOUNT_LOCKED", CLIENT_IP);
    }

    @Test
    @DisplayName("Chế độ bảo trì (MAINTENANCE_MODE=true): Chặn tài khoản USER")
    void testLoginMaintenanceModeBlocksUser() {
        LoginRequest request = new LoginRequest("normal_user", "ValidPassword123!");
        User user = new User(3L, "normal_user", "user@ptit.edu.vn", Role.USER);
        user.setPasswordHash("hashed_password");
        user.setEnabled(true);

        when(userRepository.findByUsername("normal_user")).thenReturn(Optional.of(user));
        when(userRepository.lockUserById(3L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", "hashed_password")).thenReturn(true);
        when(systemSettingService.isMaintenanceMode()).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, CLIENT_IP));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("MAINTENANCE_MODE", ex.getCode());
        assertTrue(ex.getMessage().contains("bảo trì"));

        verify(auditLogService).log(3L, "LOGIN_FAILED", "MAINTENANCE_MODE", CLIENT_IP);
    }

    @Test
    @DisplayName("Chế độ bảo trì (MAINTENANCE_MODE=true): Tài khoản ADMIN vẫn đăng nhập thành công")
    void testLoginMaintenanceModeAllowsAdmin() {
        LoginRequest request = new LoginRequest("admin", "AdminPassword123!");
        User admin = new User(1L, "admin", "admin@ptit.edu.vn", Role.ADMIN);
        admin.setPasswordHash("hashed_admin");
        admin.setEnabled(true);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.lockUserById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("AdminPassword123!", "hashed_admin")).thenReturn(true);
        when(systemSettingService.isMaintenanceMode()).thenReturn(true);
        when(jwtProvider.generateToken(admin)).thenReturn("admin.jwt.token");
        when(jwtProvider.getExpirationSeconds()).thenReturn(86400L);

        LoginResponse response = authService.login(request, CLIENT_IP);

        assertNotNull(response);
        assertEquals(Role.ADMIN, response.user().role());
        verify(auditLogService).log(1L, "LOGIN", "SUCCESS", CLIENT_IP);
    }

    @Test
    @DisplayName("Tự động nâng cấp mã hóa mật khẩu khi upgradeEncoding trả true")
    void testLoginUpgradesEncoding() {
        String sampleInput = "Sample#" + java.util.UUID.randomUUID().toString().substring(0, 8);
        LoginRequest request = new LoginRequest("cuongle", sampleInput);
        User user = new User(1L, "cuongle", "cuongle@ptit.edu.vn", Role.USER);
        user.setPasswordHash("old_hash");
        user.setEnabled(true);

        when(userRepository.findByUsername("cuongle")).thenReturn(Optional.of(user));
        when(userRepository.lockUserById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(sampleInput, "old_hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("old_hash")).thenReturn(true);
        when(passwordEncoder.encode(sampleInput)).thenReturn("new_stronger_hash");
        when(jwtProvider.generateToken(user)).thenReturn("token");

        authService.login(request, CLIENT_IP);

        assertEquals("new_stronger_hash", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Bị chặn ngay từ đầu khi checkLimit ném RateLimitException")
    void testLoginRateLimitExceeded() {
        LoginRequest request = new LoginRequest("spammer", "any");
        doThrow(new RateLimitException("Vượt quá số lần", 300L))
                .when(loginRateLimiter).checkLimit("spammer", CLIENT_IP);

        RateLimitException ex = assertThrows(RateLimitException.class, () -> authService.login(request, CLIENT_IP));

        assertEquals(300L, ex.getRetryAfterSeconds());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void overlongUnicodePasswordIsRejectedBeforeBcryptOrLookup() {
        String raw = "Aa1" + "\u00e9".repeat(35);
        AppException ex = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("known_user", raw), CLIENT_IP));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());
        verifyNoInteractions(userRepository, passwordEncoder);
        verify(loginRateLimiter).recordFailedAttempt("known_user", CLIENT_IP);
        verify(auditLogService).log(null, "LOGIN_FAILED", "BAD_PASSWORD_LENGTH", CLIENT_IP);
    }
}

