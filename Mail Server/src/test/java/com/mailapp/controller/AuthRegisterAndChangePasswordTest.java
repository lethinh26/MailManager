package com.mailapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.dto.ChangePasswordRequest;
import com.mailapp.dto.RegisterRequest;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import com.mailapp.repository.AuditLogRepository;
import com.mailapp.security.JwtProvider;
import com.mailapp.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthRegisterAndChangePasswordTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private SystemSettingService systemSettingService;

    @Test
    void rejectedLoginAuditPersistsAndRateLimitTracksSettings() throws Exception {
        String name = "rate_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String body = objectMapper.writeValueAsString(java.util.Map.of("username", name, "password", "WrongPassword123"));
        long previous = auditLogRepository.findByActionOrderByCreatedAtDesc("LOGIN_FAILED").stream()
                .filter(l -> "UNKNOWN_USER".equals(l.getDetail())).count();
        systemSettingService.updateSetting("LOGIN_MAX_FAILED_ATTEMPTS", "3");
        try {
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            }
            assertEquals(previous + 3, auditLogRepository.findByActionOrderByCreatedAtDesc("LOGIN_FAILED").stream()
                    .filter(l -> "UNKNOWN_USER".equals(l.getDetail())).count());
            assertTrue(auditLogRepository.findByActionOrderByCreatedAtDesc("LOGIN_FAILED").stream()
                    .noneMatch(l -> l.getDetail() != null && l.getDetail().contains(name)),
                    "Untrusted submitted identity must not be persisted in audit detail");
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
            systemSettingService.updateSetting("LOGIN_MAX_FAILED_ATTEMPTS", "5");
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        } finally {
            systemSettingService.updateSetting("LOGIN_MAX_FAILED_ATTEMPTS", "5");
        }
    }

    @BeforeEach
    void setUp() {
        systemSettingService.updateSetting("ALLOW_REGISTRATION", "true");
        systemSettingService.updateSetting("MAX_GMAIL_PER_USER", "5");
        systemSettingService.updateSetting("MAX_ATTACHMENT_SIZE_MB", "25");
        systemSettingService.setMaintenanceMode(false);
        if (userRepository.findByUsername("auth_fixture_user").isEmpty()) {
            User u = new User();
            u.setUsername("auth_fixture_user");
            u.setPasswordHash(passwordEncoder.encode("UserPassword123!"));
            u.setFullName("Auth Fixture User");
            u.setEmail("auth_fixture_user@example.com");
            u.setRole(Role.USER);
            u.setEnabled(true);
            u.setMustChangePassword(false);
            userRepository.save(u);
        }
    }

    @Test
    @DisplayName("EP-A1: Đăng ký tài khoản thành công trả về 201 Created và UserDto")
    void testRegisterSuccess() throws Exception {
        String username = "newuser_" + java.util.UUID.randomUUID().toString().replace("-", "") + "x".repeat(24);
        RegisterRequest request = new RegisterRequest(
                username,
                username + "@test.com",
                "StrongPass123!",
                "Nguyen Van A",
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.createdAt").exists());

        assertTrue(userRepository.findByUsername(username).isPresent());
    }

    @Test
    @DisplayName("EP-A1: Client gửi role='ADMIN' bị bỏ qua, hệ thống luôn gán ROLE_USER")
    void testRegisterRoleSpoofingIgnored() throws Exception {
        String username = "spoof_admin_" + System.currentTimeMillis();
        RegisterRequest request = new RegisterRequest(
                username,
                username + "@test.com",
                "StrongPass123!",
                "Hacker Wannabe",
                "ADMIN" // Cố tình truyền role ADMIN
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        User savedUser = userRepository.findByUsername(username).orElseThrow();
        assertEquals(Role.USER, savedUser.getRole(), "Role trong DB bắt buộc phải là USER");
    }

    @Test
    @DisplayName("EP-A1: Đăng ký thất bại với 409 USERNAME_TAKEN khi username đã tồn tại")
    void testRegisterUsernameDuplicate() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "AUTH_FIXTURE_USER",
                "duplicate@test.com",
                "StrongPass123!",
                "Duplicate User",
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    @DisplayName("EP-A1: Bị chặn 403 REGISTRATION_DISABLED khi ALLOW_REGISTRATION=false")
    void testRegisterDisabled() throws Exception {
        systemSettingService.updateSetting("ALLOW_REGISTRATION", "false");

        RegisterRequest request = new RegisterRequest(
                "disabled_user",
                "disabled@test.com",
                "StrongPass123!",
                "Disabled User",
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTRATION_DISABLED"));
    }

    @Test
    @DisplayName("EP-A3: GET /api/auth/me trả thông tin tài khoản, không lộ password hash")
    void testGetAuthMe() throws Exception {
        User user = userRepository.findByUsername("auth_fixture_user").orElseThrow();
        String token = jwtProvider.generateToken(user);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("auth_fixture_user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("EP-A4: Đổi mật khẩu thành công, set mustChangePassword=false và cấp token mới")
    void testChangePasswordSuccess() throws Exception {
        String username = "changepass_user_" + System.currentTimeMillis();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("OldPassword123!"));
        user.setFullName("Change Pass User");
        user.setEmail(username + "@mailapp.local");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user = userRepository.save(user);

        String token = jwtProvider.generateToken(user);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123!",
                "NewPassword456!"
        );

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công."))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(updatedUser.isMustChangePassword());
        assertTrue(passwordEncoder.matches("NewPassword456!", updatedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("EP-A4: Đổi mật khẩu thất bại khi mật khẩu hiện tại sai")
    void testChangePasswordWrongCurrent() throws Exception {
        User user = userRepository.findByUsername("auth_fixture_user").orElseThrow();
        String token = jwtProvider.generateToken(user);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "TotallyWrongPass999!",
                "NewPassword456!"
        );

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void utf8BcryptLimitAppliesToRegisterLoginAndChangePassword() throws Exception {
        String username = "unicode_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String password = "Aa12" + "\u00e9".repeat(34); // exactly 72 UTF-8 bytes
        String oversized = password + "x";
        RegisterRequest bad = new RegisterRequest(username, username + "@test.com", oversized, "Unicode User", null);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertFalse(userRepository.findByUsername(username).isPresent());
        RegisterRequest valid = new RegisterRequest(username, username + "@test.com", password, "Unicode User", null);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.mailapp.dto.LoginRequest(username, oversized))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.mailapp.dto.LoginRequest(username, password))))
                .andExpect(status().isOk());
        User user = userRepository.findByUsername(username).orElseThrow();
        String token = jwtProvider.generateToken(user);
        mockMvc.perform(post("/api/auth/change-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest(password, oversized))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertTrue(passwordEncoder.matches(password, userRepository.findById(user.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    @DisplayName("EP-A4 & Filter: User có mustChangePassword=true bị chặn khi truy cập API nhạy cảm khác")
    void testMustChangePasswordFilterBlocksSensitiveEndpoints() throws Exception {
        String username = "mustchange_" + System.currentTimeMillis();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("InitPass123!"));
        user.setFullName("Must Change User");
        user.setEmail(username + "@mailapp.local");
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setMustChangePassword(true); // Bắt buộc đổi
        user = userRepository.save(user);

        String token = jwtProvider.generateToken(user);

        // Gọi /api/gmail-accounts -> Bị chặn 403 PASSWORD_CHANGE_REQUIRED
        mockMvc.perform(get("/api/gmail-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        // Nhưng được phép gọi /api/auth/me
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("EP-P1: GET /api/public/config trả về cấu hình an toàn, không có secret")
    void testGetPublicConfig() throws Exception {
        mockMvc.perform(get("/api/public/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").isBoolean())
                .andExpect(jsonPath("$.oauth2LinkEnabled").isBoolean())
                .andExpect(jsonPath("$.appPasswordLinkEnabled").isBoolean())
                .andExpect(jsonPath("$.maintenanceMode").isBoolean())
                .andExpect(jsonPath("$.maxGmailPerUser").value(5))
                .andExpect(jsonPath("$.maxAttachmentSizeMb").value(25))
                .andExpect(jsonPath("$.mailPageSize").isNumber())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.jwtSecret").doesNotExist());
    }

    @Test
    @DisplayName("Đăng nhập bằng Email + Mật khẩu thành công và chặn đăng ký trùng Email (409 EMAIL_TAKEN)")
    void testLoginByEmailAndRejectDuplicateEmailRegister() throws Exception {
        String uniqueEmail = "gmail_login_" + System.currentTimeMillis() + "@gmail.com";
        String username = "user_" + System.currentTimeMillis();

        RegisterRequest reg1 = new RegisterRequest(username, uniqueEmail, "StrongPass123!", "Gmail User", "USER");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isCreated());

        // 1. Đăng nhập trực tiếp bằng địa chỉ Gmail + mật khẩu -> 200 OK + JWT
        com.mailapp.dto.LoginRequest loginByEmail = new com.mailapp.dto.LoginRequest(uniqueEmail, "StrongPass123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginByEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.email").value(uniqueEmail));

        // 2. Đăng ký tài khoản khác trùng địa chỉ email -> 409 EMAIL_TAKEN
        RegisterRequest regDupEmail = new RegisterRequest("other_" + System.currentTimeMillis(), uniqueEmail, "StrongPass123!", "Other User", "USER");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regDupEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }
}

