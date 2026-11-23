package com.mailapp.mail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mailapp.dto.LoginRequest;
import com.mailapp.dto.LoginResponse;
import com.mailapp.exception.*;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AuthType;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.security.JwtProvider;
import com.mailapp.security.LoginRateLimiter;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import com.mailapp.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử khẳng định không rò rỉ secret / password / JWT:
 * 1. toString(): MailCredentials.toString() che secret và email.
 * 2. Log: DefaultMailConnectionFactory và AuthServiceImpl không log mật khẩu hoặc chuỗi JWT.
 * 3. Message lỗi: Toàn bộ Exception không chứa mật khẩu hay thông tin nhạy cảm.
 *
 * @author Lê Thanh Cường
 */
class SecretMaskingTest {

    @Test
    @DisplayName("MailCredentials.toString() tuyệt đối không để lộ mật khẩu hoặc token")
    void testCredentialsToStringMasksSecret() {
        String sensitiveSecret = "my-super-secret-password-16chars";
        MailCredentials credentials = new MailCredentials("lethanhcuong@ptit.edu.vn", AuthType.APP_PASSWORD, sensitiveSecret);

        String str = credentials.toString();

        assertFalse(str.contains(sensitiveSecret), "toString() không được chứa mật khẩu!");
        assertTrue(str.contains("secret=***"), "toString() phải thay thế secret bằng ***");
        assertTrue(str.contains("l***@ptit.edu.vn"), "Email phải được che bớt ký tự");
    }

    @Test
    @DisplayName("Hàm maskEmail che đúng định dạng c***@gmail.com")
    void testMaskEmail() {
        assertEquals("c***@gmail.com", MailCredentials.maskEmail("cuong.le@gmail.com"));
        assertEquals("a***@ptit.edu.vn", MailCredentials.maskEmail("admin@ptit.edu.vn"));
        assertEquals("***", MailCredentials.maskEmail(null));
        assertEquals("***", MailCredentials.maskEmail(""));
    }

    @Test
    @DisplayName("Các exception message không chứa mật khẩu hay secret")
    void testExceptionMessagesDoNotContainSecret() {
        MailAuthException authException = new MailAuthException();
        assertFalse(authException.getMessage().contains("password"));
        assertEquals("GMAIL_AUTH_FAILED", authException.getErrorCode());

        ReauthRequiredException reauthException = new ReauthRequiredException();
        assertEquals("GMAIL_NEEDS_REAUTH", reauthException.getErrorCode());

        MailNetworkException networkException = new MailNetworkException("Lỗi kết nối", false);
        assertEquals("GMAIL_CONNECTION_ERROR", networkException.getErrorCode());

        ImapDisabledException imapException = new ImapDisabledException();
        assertEquals("IMAP_DISABLED", imapException.getErrorCode());

        RateLimitException rateLimitException = new RateLimitException("Vượt quá số lần", 300L);
        assertFalse(rateLimitException.getMessage().contains("password"));
    }

    @Test
    @DisplayName("Log trong DefaultMailConnectionFactory không chứa secret / password khi kết nối lỗi")
    void testMailConnectionFactoryLogsNeverContainSecret() {
        Logger mailLogger = (Logger) LoggerFactory.getLogger(DefaultMailConnectionFactory.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        mailLogger.addAppender(listAppender);

        try {
            MailConnectionProperties properties = new MailConnectionProperties();
            properties.setImapHost("127.0.0.1");
            properties.setImapPort(59996);
            properties.setRetryEnabled(false);

            DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                    properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

            String testPassword = "SuperConfidentialPassword1234!";
            MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", testPassword);

            assertThrows(MailNetworkException.class, () -> factory.openImap(credentials));

            // Kiểm tra toàn bộ log event được ghi
            for (ILoggingEvent event : listAppender.list) {
                String logMessage = event.getFormattedMessage();
                assertFalse(logMessage.contains(testPassword), "Log không được chứa mật khẩu kết nối!");
                assertFalse(logMessage.contains("SuperConfidential"), "Log không được chứa từ khóa mật khẩu!");
            }
        } finally {
            mailLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @Test
    @DisplayName("Log trong AuthServiceImpl không chứa mật khẩu của user và không chứa token JWT")
    void testAuthServiceLogsNeverContainPasswordOrJwtToken() {
        Logger authLogger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        authLogger.addAppender(listAppender);

        try {
            UserRepository userRepository = mock(UserRepository.class);
            PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
            JwtProvider jwtProvider = mock(JwtProvider.class);
            SystemSettingService systemSettingService = mock(SystemSettingService.class);
            AuditLogService auditLogService = mock(AuditLogService.class);
            LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);

            AuthServiceImpl authService = new AuthServiceImpl(
                    userRepository, passwordEncoder, jwtProvider, systemSettingService, auditLogService, rateLimiter);

            String rawPassword = "SensitiveValue-" + java.util.UUID.randomUUID();
            var b64 = java.util.Base64.getUrlEncoder().withoutPadding();
            String rawJwtToken = b64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + "." + b64.encodeToString("{\"sub\":\"1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + ".mockSig" + System.nanoTime();

            User user = new User(1L, "cuongle", "cuongle@ptit.edu.vn", Role.USER);
            user.setPasswordHash("hashed_value");
            user.setEnabled(true);

            when(userRepository.findByUsername("cuongle")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(rawPassword, "hashed_value")).thenReturn(true);
            when(jwtProvider.generateToken(user)).thenReturn(rawJwtToken);
            when(jwtProvider.getExpirationSeconds()).thenReturn(86400L);

            LoginRequest request = new LoginRequest("cuongle", rawPassword);
            LoginResponse response = authService.login(request, "127.0.0.1");

            // Phản hồi chứa token đúng
            assertEquals(rawJwtToken, response.accessToken());

            // Kiểm tra toàn bộ log event của AuthServiceImpl
            for (ILoggingEvent event : listAppender.list) {
                String logMessage = event.getFormattedMessage();
                assertFalse(logMessage.contains(rawPassword), "Log không được chứa mật khẩu người dùng!");
                assertFalse(logMessage.contains("SensitiveValue"), "Log không được chứa đoạn ký tự mật khẩu!");
                assertFalse(logMessage.contains("eyJ"), "Log không được in chuỗi token JWT!");
            }
        } finally {
            authLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }
}
