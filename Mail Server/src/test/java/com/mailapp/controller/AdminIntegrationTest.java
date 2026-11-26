package com.mailapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.model.*;
import com.mailapp.oauth2.OAuth2Service;
import com.mailapp.repository.AuditLogRepository;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.SystemSettingRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.JwtProvider;
import com.mailapp.service.SystemSettingService;
import com.mailapp.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Kiểm thử tích hợp toàn diện Module Admin (EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2)")
class AdminIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private GmailAccountRepository gmailAccountRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private SystemSettingRepository systemSettingRepository;
    @Autowired private SystemSettingService systemSettingService;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private com.mailapp.service.AuthService authServiceDirect;

    @SpyBean
    private OAuth2Service oauth2Service;

    private User primaryAdmin;
    private User regularUser;
    private String adminToken;
    private String userToken;

    @Test
    void maintenanceRejectsExistingUserJwtButNotAdminOrPublic() throws Exception {
        systemSettingService.setMaintenanceMode(true);
        try {
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("MAINTENANCE_MODE"))
                    .andExpect(jsonPath("$.path").value("/api/auth/me"));
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/public/config").header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maintenanceMode").value(true));
        } finally {
            systemSettingService.setMaintenanceMode(false);
        }
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotCreateDuplicateEmailOrCaseVariantUsername() throws Exception {
        String duplicateEmail = objectMapper.writeValueAsString(Map.of(
                "username", "another_user", "fullName", "Another User", "email", "USER@ptit.edu.vn"));
        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicateEmail))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        String duplicateUsername = objectMapper.writeValueAsString(Map.of(
                "username", "NORMAL_USER", "fullName", "Another User", "email", "another@ptit.edu.vn"));
        mockMvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicateUsername))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void stalePrincipalCannotUndoAdminResetOrLock() {
        String oldPassword = "OldPassword123!";
        regularUser.setPasswordHash(passwordEncoder.encode(oldPassword));
        userRepository.save(regularUser);
        User stalePrincipal = userRepository.findById(regularUser.getId()).orElseThrow();
        var reset = adminServiceDirect.resetPassword(primaryAdmin, regularUser.getId(), "127.0.0.1");
        var request = new com.mailapp.dto.ChangePasswordRequest(oldPassword, "ChangedPassword123!");
        var ex = assertThrows(com.mailapp.exception.AppException.class,
                () -> authServiceDirect.changePassword(request, stalePrincipal, "127.0.0.1"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        User afterReset = userRepository.findById(regularUser.getId()).orElseThrow();
        assertTrue(afterReset.isMustChangePassword());
        assertTrue(passwordEncoder.matches(reset.temporaryPassword(), afterReset.getPasswordHash()));

        adminServiceDirect.lockUser(primaryAdmin, regularUser.getId(), "127.0.0.1");
        var resetPasswordRequest = new com.mailapp.dto.ChangePasswordRequest(reset.temporaryPassword(), "ChangedPassword123!");
        var locked = assertThrows(com.mailapp.exception.AppException.class,
                () -> authServiceDirect.changePassword(resetPasswordRequest, afterReset, "127.0.0.1"));
        assertEquals("UNAUTHORIZED", locked.getCode());
        User afterLock = userRepository.findById(regularUser.getId()).orElseThrow();
        assertFalse(afterLock.isEnabled());
        assertTrue(afterLock.isMustChangePassword());
        assertTrue(passwordEncoder.matches(reset.temporaryPassword(), afterLock.getPasswordHash()));
    }

    @Test
    void staleAdminPrincipalPasswordChangePreservesDemotion() {
        String password = "OriginalAdmin123!";
        regularUser.setRole(Role.ADMIN);
        regularUser.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(regularUser);
        User staleAdmin = userRepository.findById(regularUser.getId()).orElseThrow();
        adminServiceDirect.updateUserRole(primaryAdmin, regularUser.getId(), "USER", "127.0.0.1");
        var changed = authServiceDirect.changePassword(
                new com.mailapp.dto.ChangePasswordRequest(password, "NewAdminPassword123!"), staleAdmin, "127.0.0.1");
        assertEquals(Role.USER, changed.user().role());
        User persisted = userRepository.findById(regularUser.getId()).orElseThrow();
        assertEquals(Role.USER, persisted.getRole());
        assertTrue(passwordEncoder.matches("NewAdminPassword123!", persisted.getPasswordHash()));
    }

    @Test
    void simultaneousAdminLocksCannotDisableEveryAdmin() throws Exception {
        User otherAdmin = new User();
        otherAdmin.setUsername("other_admin");
        otherAdmin.setEmail("other_admin@ptit.edu.vn");
        otherAdmin.setRole(Role.ADMIN);
        otherAdmin.setPasswordHash(passwordEncoder.encode("OtherAdmin123!"));
        otherAdmin = userRepository.save(otherAdmin);
        User second = otherAdmin;
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                start.await();
                try {
                    adminServiceDirect.lockUser(primaryAdmin, second.getId(), "127.0.0.1");
                    return "OK";
                } catch (com.mailapp.exception.AppException ex) {
                    return ex.getCode();
                }
            });
            var secondAttempt = pool.submit(() -> {
                start.await();
                try {
                    adminServiceDirect.lockUser(second, primaryAdmin.getId(), "127.0.0.1");
                    return "OK";
                } catch (com.mailapp.exception.AppException ex) {
                    return ex.getCode();
                }
            });
            start.countDown();
            List<String> results = List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    secondAttempt.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(results.contains("OK"));
            assertTrue(results.contains("FORBIDDEN")); // waiting actor has been locked
            assertEquals(1, userRepository.countByRoleAndEnabledTrue(Role.ADMIN));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void overlappingTransactionsRejectDemotedActorBeforeUnrelatedMutation() throws Exception {
        User other = new User();
        other.setUsername("waiting_admin");
        other.setEmail("waiting@test.local");
        other.setRole(Role.ADMIN);
        other.setEnabled(true);
        other.setMustChangePassword(false);
        other.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        User actor = userRepository.saveAndFlush(other);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var demoted = new java.util.concurrent.CountDownLatch(1);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> tx.executeWithoutResult(status -> {
                adminServiceDirect.updateUserRole(primaryAdmin, actor.getId(), "USER", "127.0.0.1");
                demoted.countDown();
                try {
                    assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertTrue(demoted.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var second = pool.submit(() -> {
                try {
                    tx.executeWithoutResult(status -> {
                        entered.countDown();
                        adminServiceDirect.lockUser(actor, regularUser.getId(), "127.0.0.1");
                    });
                    return "OK";
                } catch (com.mailapp.exception.AppException e) {
                    return e.getCode();
                }
            });
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> second.get(250, java.util.concurrent.TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("FORBIDDEN", second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(userRepository.findById(regularUser.getId()).orElseThrow().isEnabled());
            assertEquals(1, userRepository.countByRoleAndEnabledTrue(Role.ADMIN));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void simultaneousSettingUpdatesCannotDisableBothLinkMethods() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<String, java.util.concurrent.Callable<String>> disable = key -> () -> {
                start.await();
                try {
                    adminServiceDirect.updateSettings(primaryAdmin,
                            new com.mailapp.dto.admin.AdminDtos.AdminUpdateSettingsRequest(Map.of(key, "false")),
                            "127.0.0.1");
                    return "OK";
                } catch (com.mailapp.exception.AppException ex) {
                    return ex.getCode();
                }
            };
            var first = pool.submit(disable.apply("ALLOW_OAUTH2_LINK"));
            var second = pool.submit(disable.apply("ALLOW_APP_PASSWORD_LINK"));
            start.countDown();
            List<String> results = List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(results.contains("OK"));
            assertTrue(results.contains("VALIDATION_ERROR"));
            assertTrue(systemSettingService.isOAuth2LinkEnabled() || systemSettingService.isAppPasswordLinkEnabled());
        } finally {
            pool.shutdownNow();
        }
    }

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        gmailAccountRepository.deleteAll();
        systemSettingRepository.findAll().forEach(s -> {
            s.setUpdatedBy(null);
            systemSettingRepository.save(s);
        });
        userRepository.deleteAll();

        String generatedAdminSeed = java.util.UUID.randomUUID().toString();
        primaryAdmin = new User();
        primaryAdmin.setUsername("root_admin");
        primaryAdmin.setFullName("Quản Trị Viên Chính");
        primaryAdmin.setEmail("root_admin@ptit.edu.vn");
        primaryAdmin.setRole(Role.ADMIN);
        primaryAdmin.setEnabled(true);
        primaryAdmin.setMustChangePassword(false);
        primaryAdmin.setPasswordHash(passwordEncoder.encode(generatedAdminSeed));
        primaryAdmin = userRepository.save(primaryAdmin);

        String generatedUserSeed = java.util.UUID.randomUUID().toString();
        regularUser = new User();
        regularUser.setUsername("normal_user");
        regularUser.setFullName("Người Dùng Thường");
        regularUser.setEmail("user@ptit.edu.vn");
        regularUser.setRole(Role.USER);
        regularUser.setEnabled(true);
        regularUser.setMustChangePassword(false);
        regularUser.setPasswordHash(passwordEncoder.encode(generatedUserSeed));
        regularUser = userRepository.save(regularUser);

        adminToken = jwtProvider.generateToken(primaryAdmin);
        userToken = jwtProvider.generateToken(regularUser);

        // Reset settings mặc định trước mỗi test
        systemSettingService.updateSetting("ALLOW_REGISTRATION", "true");
        systemSettingService.updateSetting("ALLOW_OAUTH2_LINK", "true");
        systemSettingService.updateSetting("ALLOW_APP_PASSWORD_LINK", "true");
        systemSettingService.updateSetting("ALLOW_SEND_MAIL", "true");
        systemSettingService.updateSetting("MAINTENANCE_MODE", "false");
        systemSettingService.evictCache();
    }

    @Test
    @DisplayName("Phân quyền: Không có token -> 401; USER gọi mọi endpoint Admin -> 403 FORBIDDEN")
    void testRbac_Unauthenticated401AndRegularUser403() throws Exception {
        for (String path : List.of("/api/admin/users", "/api/admin/settings", "/api/admin/stats", "/api/admin/logs")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            mockMvc.perform(get(path).header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    @DisplayName("EP-AD1: Admin tạo user thủ công ngay cả khi ALLOW_REGISTRATION=false, mustChangePassword=true & audit không lộ mật khẩu")
    void testCreateUser_WorksWhenRegistrationDisabled() throws Exception {
        systemSettingService.updateSetting("ALLOW_REGISTRATION", "false");

        String body = """
                {
                  "username": "manual_student",
                  "fullName": "Sinh Viên Mới",
                  "email": "sv_moi@ptit.edu.vn",
                  "role": "USER"
                }
                """;

        MvcResult res = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.username").value("manual_student"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword", not(emptyOrNullString())))
                .andReturn();

        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        String tempPass = node.get("temporaryPassword").asText();
        assertTrue(tempPass.length() >= 12);

        // Kiểm tra audit log đã ghi và KHÔNG chứa mật khẩu tạm
        List<AuditLog> logs = auditLogRepository.findByActionOrderByCreatedAtDesc("ADMIN_CREATE_USER");
        assertEquals(1, logs.size());
        assertFalse(logs.get(0).getDetail().contains(tempPass), "Audit log tuyệt đối không được chứa mật khẩu!");
    }

    @Test
    @DisplayName("EP-AD2 & EP-AD3: Lọc/phân trang/sắp xếp và chi tiết user KHÔNG lộ token/mật khẩu Gmail")
    void testListUsersFilteringAndUserDetailNoSecrets() throws Exception {
        GmailAccount g = new GmailAccount();
        g.setUser(regularUser);
        g.setEmail("secret.owner@gmail.com");
        g.setDisplayName("Tài khoản chính");
        g.setAuthType(AuthType.OAUTH2);
        g.setStatus(AccountStatus.ACTIVE);
        g.setRefreshTokenEncrypted("ENC_SUPER_SECRET_TOKEN_DO_NOT_LEAK");
        g.setAppPasswordEncrypted("ENC_APP_PASS_DO_NOT_LEAK");
        gmailAccountRepository.save(g);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("username", "normal")
                        .param("role", "USER")
                        .param("enabled", "true")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "username")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("normal_user"))
                .andExpect(jsonPath("$.content[0].gmailCount").value(1));

        MvcResult detailRes = mockMvc.perform(get("/api/admin/users/" + regularUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gmailCount").value(1))
                .andExpect(jsonPath("$.gmailAccounts[0].email").value("secret.owner@gmail.com"))
                .andExpect(jsonPath("$.gmailAccounts[0].authType").value("OAUTH2"))
                .andReturn();

        String rawJson = detailRes.getResponse().getContentAsString();
        assertFalse(rawJson.contains("ENC_SUPER_SECRET_TOKEN"));
        assertFalse(rawJson.contains("ENC_APP_PASS"));
        assertFalse(rawJson.contains("passwordHash"));
    }

    @Test
    @DisplayName("Bảo vệ: Chặn tự khóa/tự hạ quyền/tự xóa và chặn khóa/hạ quyền/xóa ADMIN cuối cùng (400 LAST_ADMIN)")
    void testAdminSelfProtectionAndLastAdminProtection() throws Exception {
        // 1. Tự khóa / tự hạ quyền / tự xóa chính mình -> 400 SELF_ACTION_FORBIDDEN
        mockMvc.perform(put("/api/admin/users/" + primaryAdmin.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        mockMvc.perform(put("/api/admin/users/" + primaryAdmin.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        mockMvc.perform(put("/api/admin/users/" + primaryAdmin.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        mockMvc.perform(delete("/api/admin/users/" + primaryAdmin.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        // With two enabled administrators, one may lock the other. A request
        // that passed the filter before the lock must also be rejected in the service.
        User secondAdmin = new User();
        secondAdmin.setUsername("second_admin");
        secondAdmin.setFullName("Admin 2");
        secondAdmin.setEmail("a2@ptit.edu.vn");
        secondAdmin.setRole(Role.ADMIN);
        secondAdmin.setEnabled(true);
        secondAdmin.setPasswordHash(passwordEncoder.encode("AdminTwo2026!"));
        secondAdmin = userRepository.save(secondAdmin);
        String secondAdminToken = jwtProvider.generateToken(secondAdmin);

        // primaryAdmin khóa secondAdmin (còn 1 active admin là primaryAdmin) -> 200 OK
        mockMvc.perform(put("/api/admin/users/" + secondAdmin.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // The persisted second admin is now disabled; the detached pre-lock
        // snapshot represents a request which already passed the HTTP filter.
        // Never treat an invented/nonexistent principal as authorized just to
        // force LAST_ADMIN. Self-action and the fresh actor check protect the
        // sole enabled administrator before that defensive branch is reached.
        User disabledActor = secondAdmin;
        com.mailapp.exception.AppException lockEx = assertThrows(com.mailapp.exception.AppException.class,
                () -> adminServiceDirect.lockUser(disabledActor, primaryAdmin.getId(), "127.0.0.1"));
        assertEquals("FORBIDDEN", lockEx.getCode());
        assertEquals(403, lockEx.getStatus().value());

        com.mailapp.exception.AppException demoteEx = assertThrows(com.mailapp.exception.AppException.class,
                () -> adminServiceDirect.updateUserRole(disabledActor, primaryAdmin.getId(), "USER", "127.0.0.1"));
        assertEquals("FORBIDDEN", demoteEx.getCode());
        assertEquals(403, demoteEx.getStatus().value());

        com.mailapp.exception.AppException delEx = assertThrows(com.mailapp.exception.AppException.class,
                () -> adminServiceDirect.deleteUser(disabledActor, primaryAdmin.getId(), "127.0.0.1"));
        assertEquals("FORBIDDEN", delEx.getCode());
        assertEquals(403, delEx.getStatus().value());
        assertEquals(1, userRepository.countByRoleAndEnabledTrue(Role.ADMIN));

        // Và token của secondAdmin sau khi bị khóa lập tức bị từ chối 401!
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + secondAdminToken))
                .andExpect(status().isUnauthorized());
    }

    @Autowired
    private com.mailapp.service.AdminService adminServiceDirect;

    @Test
    @DisplayName("EP-AD4/AD5: Khóa user -> JWT hiện hành vô hiệu NGAY LẬP TỨC; mở khóa -> dùng lại bình thường")
    void testLockUserImmediatelyInvalidatesJwt() throws Exception {
        // Trước khi khóa: token dùng được 200 OK
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("normal_user"));

        // Admin khóa user
        mockMvc.perform(put("/api/admin/users/" + regularUser.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Ngay sau khi khóa: cùng JWT đó bị từ chối 401 UNAUTHORIZED tức thì!
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        // Mở khóa lại -> token hoạt động trở lại
        mockMvc.perform(put("/api/admin/users/" + regularUser.getId() + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void legacyResetBodyRespectsUtf8LimitAndNeverEchoesSuppliedPassword() throws Exception {
        String path = "/api/admin/users/" + regularUser.getId() + "/reset-password";
        String valid = "Aa12" + "\u00e9".repeat(34);
        String oversized = valid + "x";
        String oldHash = userRepository.findById(regularUser.getId()).orElseThrow().getPasswordHash();
        mockMvc.perform(put(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", oversized))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals(oldHash, userRepository.findById(regularUser.getId()).orElseThrow().getPasswordHash());
        mockMvc.perform(put(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", valid))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.message").value("Đặt lại mật khẩu thành công"))
                .andExpect(jsonPath("$.temporaryPassword").isEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(content().string(not(containsString(valid))));
        User current = userRepository.findById(regularUser.getId()).orElseThrow();
        assertTrue(current.isMustChangePassword());
        assertTrue(passwordEncoder.matches(valid, current.getPasswordHash()));
        List<AuditLog> logs = auditLogRepository.findByActionOrderByCreatedAtDesc("ADMIN_RESET_PASSWORD");
        assertEquals(1, logs.size());
        assertFalse(logs.get(0).getDetail().contains(valid));
    }

    @Test
    void auditAliasesFindOldAndNewRowsWithoutDuplicatingEvents() throws Exception {
        for (String[] row : new String[][]{
                {"LOGIN", "SUCCESS"}, {"LOGIN_SUCCESS", "SUCCESS"},
                {"OAUTH2_CALLBACK", "outcome=CREATED; email=x***@gmail.com"},
                {"LINK_GMAIL", "Liên kết Gmail OAuth2: x***@gmail.com"},
                {"LINK_GMAIL", "Liên kết Gmail App Password: y***@gmail.com"},
                {"ADMIN_CHANGE_SETTING", "MAX_GMAIL_PER_USER: 5 -> 6"},
                {"DELETE_MAIL", "mail removed"}}) {
            AuditLog log = new AuditLog();
            log.setUser(regularUser);
            log.setAction(row[0]);
            log.setDetail(row[1]);
            log.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(log);
        }
        for (String[] filter : new String[][]{
                {"LOGIN_SUCCESS", "2"}, {"OAUTH2_CALLBACK", "2"},
                {"LINK_GMAIL", "3"}, {"SETTING_CHANGE", "1"}, {"MAIL_DELETE", "1"}}) {
            mockMvc.perform(get("/api/admin/logs").header("Authorization", "Bearer " + adminToken)
                            .param("action", filter[0]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(Integer.parseInt(filter[1])));
        }
        assertEquals(7, auditLogRepository.count());
    }

    @Test
    @DisplayName("EP-AD7 & EP-AD8: Reset mật khẩu tạm một lần + Xóa user cascade Gmail & revoke OAuth2 best-effort")
    void testResetPasswordAndCascadeDeleteWithOAuthRevoke() throws Exception {
        // Reset password
        MvcResult resetRes = mockMvc.perform(put("/api/admin/users/" + regularUser.getId() + "/reset-password")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword", not(emptyOrNullString())))
                .andReturn();
        String tempPw = objectMapper.readTree(resetRes.getResponse().getContentAsString())
                .get("temporaryPassword").asText();

        User reloaded = userRepository.findById(regularUser.getId()).orElseThrow();
        assertTrue(reloaded.isMustChangePassword());
        assertTrue(passwordEncoder.matches(tempPw, reloaded.getPasswordHash()));

        // Audit reset password không được ghi mật khẩu
        List<AuditLog> resetLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("ADMIN_RESET_PASSWORD");
        assertEquals(1, resetLogs.size());
        assertFalse(resetLogs.get(0).getDetail().contains(tempPw));

        // A dual-method Gmail must revoke OAuth even with App Password preferred.
        GmailAccount oauthAcc = new GmailAccount();
        oauthAcc.setUser(regularUser);
        oauthAcc.setEmail("oauth.victim@gmail.com");
        oauthAcc.setAuthType(AuthType.APP_PASSWORD);
        oauthAcc.setStatus(AccountStatus.ACTIVE);
        oauthAcc.setRefreshTokenEncrypted("ENC_TOKEN");
        oauthAcc.setAppPasswordEncrypted("ENC_APP_PASSWORD");
        gmailAccountRepository.save(oauthAcc);

        doNothing().when(oauth2Service).revokeToken(any(GmailAccount.class));

        mockMvc.perform(delete("/api/admin/users/" + regularUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        verify(oauth2Service).revokeToken(any(GmailAccount.class));
        assertFalse(userRepository.existsById(regularUser.getId()));
        assertTrue(gmailAccountRepository.findByUserId(regularUser.getId()).isEmpty());
    }

    @Test
    @DisplayName("EP-S1/S2: Validate settings, chặn tắt cả OAuth2 & AppPassword (400), cache cập nhật tức thì & ghi audit cũ->mới")
    void testSettingsValidationCacheEvictionAndAudit() throws Exception {
        // Tắt cả 2 phương thức liên kết cùng lúc -> 400 VALIDATION_ERROR
        String bothOff = objectMapper.writeValueAsString(Map.of("settings", Map.of(
                "ALLOW_OAUTH2_LINK", "false",
                "ALLOW_APP_PASSWORD_LINK", "false"
        )));
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bothOff))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Vượt ngưỡng số nguyên -> 400
        String outOfRange = objectMapper.writeValueAsString(Map.of("settings", Map.of(
                "MAX_ATTACHMENT_SIZE_MB", "999"
        )));
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outOfRange))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Cập nhật hợp lệ -> hiệu lực tức thì trên /api/public/config + ghi updatedByUsername + audit cũ -> mới
        String validUpdate = objectMapper.writeValueAsString(Map.of("settings", Map.of(
                "ALLOW_REGISTRATION", "false",
                "MAX_GMAIL_PER_USER", "8",
                "MAIL_PAGE_SIZE", "45"
        )));
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='ALLOW_REGISTRATION')].value", hasItem("false")))
                .andExpect(jsonPath("$[?(@.key=='ALLOW_REGISTRATION')].updatedByUsername", hasItem("root_admin")));

        // Kiểm tra cache hết hạn tức thì
        mockMvc.perform(get("/api/public/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(false))
                .andExpect(jsonPath("$.maxGmailPerUser").value(8))
                .andExpect(jsonPath("$.mailPageSize").value(45));

        String settingsJson = mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode settings = objectMapper.readTree(settingsJson);
        String lastKey = "";
        for (JsonNode setting : settings) {
            String key = setting.get("key").asText();
            assertTrue(lastKey.compareTo(key) <= 0);
            lastKey = key;
        }

        List<AuditLog> settingLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("ADMIN_CHANGE_SETTING");
        assertTrue(settingLogs.stream().anyMatch(l -> l.getDetail().contains("ALLOW_REGISTRATION: true -> false")));
    }

    @Test
    void legacyUnlimitedQuotaIsPreservedButNewQuotaMustBePositive() throws Exception {
        systemSettingService.updateSetting("MAX_GMAIL_PER_USER", "5", null);
        String zero = objectMapper.writeValueAsString(Map.of("settings", Map.of("MAX_GMAIL_PER_USER", "0")));
        mockMvc.perform(put("/api/admin/settings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(zero))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals(5, systemSettingService.getMaxGmailPerUser());
        systemSettingService.updateSetting("MAX_GMAIL_PER_USER", "0", null);
        mockMvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='MAX_GMAIL_PER_USER')].minValue", hasItem(0)));
        mockMvc.perform(put("/api/admin/settings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(zero))
                .andExpect(status().isOk());
        assertEquals(0, systemSettingService.getMaxGmailPerUser());
        String positive = objectMapper.writeValueAsString(Map.of("settings", Map.of("MAX_GMAIL_PER_USER", "1")));
        mockMvc.perform(put("/api/admin/settings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(positive))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='MAX_GMAIL_PER_USER')].minValue", hasItem(1)));
        mockMvc.perform(put("/api/admin/settings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(zero))
                .andExpect(status().isBadRequest());
        assertEquals(1, systemSettingService.getMaxGmailPerUser());
    }

    @Test
    @DisplayName("EP-ST1/ST2: Thống kê tổng quan + Lọc nhật ký + Xuất CSV chặn CSV Injection (=, +, -, @)")
    void testStatsLogsAndCsvInjectionProtection() throws Exception {
        // Seed vài audit log: LOGIN_FAILED, SEND_MAIL, và 1 bản ghi chứa payload CSV Injection
        auditLogRepository.save(new AuditLog(regularUser, "LOGIN_FAILED", "Sai mật khẩu", "127.0.0.1"));
        auditLogRepository.save(new AuditLog(regularUser, "SEND_MAIL", "Gửi 1 thư", "127.0.0.1"));
        AuditLog evil = new AuditLog(regularUser, "LOGIN", "=CMD|' /C calc'!A0", "127.0.0.1");
        evil.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(evil);

        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.totalAdmins").value(1))
                .andExpect(jsonPath("$.failedLoginsLast24h").value(1))
                .andExpect(jsonPath("$.mailsSentLast7DaysByDay", hasSize(7)));

        mockMvc.perform(get("/api/admin/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("username", "normal_user")
                        .param("action", "SEND")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("SEND_MAIL"));

        MvcResult csvRes = mockMvc.perform(get("/api/admin/logs/export")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("audit-logs.csv")))
                .andReturn();

        String csv = csvRes.getResponse().getContentAsString();
        assertTrue(csv.contains("\"'=CMD|' /C calc'!A0\""), "Ô CSV bắt đầu bằng '=' phải được vô hiệu hóa bằng tiền tố '!");
        assertEquals("\"'=1+1\"", AdminServiceImpl.csvCell("=1+1"));
        assertEquals("\"'+1+1\"", AdminServiceImpl.csvCell("+1+1"));
        assertEquals("\"'-1+1\"", AdminServiceImpl.csvCell("-1+1"));
        assertEquals("\"'@SUM(A1)\"", AdminServiceImpl.csvCell("@SUM(A1)"));
    }
}
