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

    @SpyBean
    private OAuth2Service oauth2Service;

    private User primaryAdmin;
    private User regularUser;
    private String adminToken;
    private String userToken;

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
    @DisplayName("Bảo vệ: Chặn tự khóa/tự hạ quyền/tự xóa (400) và chặn khóa/hạ quyền/xóa ADMIN cuối cùng (409 LAST_ADMIN)")
    void testAdminSelfProtectionAndLastAdminProtection() throws Exception {
        // 1. Tự khóa / tự hạ quyền / tự xóa chính mình -> 400 SELF_ACTION_FORBIDDEN
        mockMvc.perform(put("/api/admin/users/" + primaryAdmin.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken))
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

        // 2. Tạo thêm 1 admin thứ hai nhưng đang bị khóa (disabled), sau đó một actor admin khác kiểm tra LAST_ADMIN
        // Hoặc: tạo admin thứ 2 (active) -> khóa admin thứ 2 được -> sau đó admin1 không thể bị admin2 khóa?
        // Để kiểm tra 409 LAST_ADMIN chính xác với 1 admin duy nhất: cho một token admin2 (đã bị vô hiệu hoặc tạo 2 admin, khóa 1, rồi không khóa được người còn lại)
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

        // Bây giờ mở khóa secondAdmin -> primaryAdmin hạ quyền secondAdmin -> ok; rồi nếu chỉ còn 1 active admin
        // Gọi trực tiếp AdminService test tình huống còn đúng 1 active admin bị một admin khác khóa/hạ quyền/xóa -> 409 LAST_ADMIN
        // Thực tế: nếu chỉ còn 1 active admin, ta mở khóa tạm bằng 1 user không phải target? Không cần:
        // Tạo tình huống thực tế: admin2 đang ở DB là role=ADMIN, enabled=true (nhưng admin1 lại enabled=false!)
        // Wait: nếu admin1 gọi lock admin2 khi chỉ có admin2 active? (không xảy ra vì admin1 phải active để gọi).
        // Vậy làm thế nào để test 409 LAST_ADMIN qua HTTP thật? Rất đơn giản:
        // Khi chỉ còn 1 admin active duy nhất (primaryAdmin), bất kỳ request khóa/hạ quyền/xóa lên primaryAdmin từ một actor khác:
        // (Trong AdminServiceImpl, check LAST_ADMIN chạy cho mọi target là active ADMIN duy nhất!)
        // Hãy kiểm thử trực tiếp 409 LAST_ADMIN bằng cách mock actor khác trong unit call VÀ xác nhận qua HTTP với 2 admin:
        // 2 admin active -> khóa admin2 OK -> xóa admin2 OK -> không ai có thể xóa/khóa/hạ quyền admin cuối!
        User disabledActor = new User();
        disabledActor.setId(999L);
        disabledActor.setUsername("other_admin");
        disabledActor.setRole(Role.ADMIN);

        // primaryAdmin đang là active ADMIN duy nhất vì secondAdmin đã bị lock!
        // Gọi trực tiếp bean AdminService (qua MockMvc không đăng nhập bằng disabledActor được vì filter chặn user khóa)
        // -> kiểm tra cả 3 lệnh lock, updateUserRole, deleteUser đều ném 409 LAST_ADMIN!
        com.mailapp.exception.AppException lockEx = assertThrows(com.mailapp.exception.AppException.class,
                () -> adminServiceDirect.lockUser(disabledActor, primaryAdmin.getId(), "127.0.0.1"));
        assertEquals("LAST_ADMIN", lockEx.getCode());
        assertEquals(409, lockEx.getStatus().value());

        com.mailapp.exception.AppException demoteEx = assertThrows(com.mailapp.exception.AppException.class,
                () -> adminServiceDirect.updateUserRole(disabledActor, primaryAdmin.getId(), "USER", "127.0.0.1"));
        assertEquals("LAST_ADMIN", demoteEx.getCode());
        assertEquals(409, demoteEx.getStatus().value());

        com.mailapp.exception.AppException delEx = assertThrows(com.mailapp.exception.AppException.class,
                () -> adminServiceDirect.deleteUser(disabledActor, primaryAdmin.getId(), "127.0.0.1"));
        assertEquals("LAST_ADMIN", delEx.getCode());
        assertEquals(409, delEx.getStatus().value());

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

        // Gắn 1 tài khoản OAuth2 cho regularUser rồi DELETE
        GmailAccount oauthAcc = new GmailAccount();
        oauthAcc.setUser(regularUser);
        oauthAcc.setEmail("oauth.victim@gmail.com");
        oauthAcc.setAuthType(AuthType.OAUTH2);
        oauthAcc.setStatus(AccountStatus.ACTIVE);
        oauthAcc.setRefreshTokenEncrypted("ENC_TOKEN");
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
                "MAX_GMAIL_PER_USER", "8"
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
                .andExpect(jsonPath("$.maxGmailPerUser").value(8));

        List<AuditLog> settingLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("ADMIN_CHANGE_SETTING");
        assertTrue(settingLogs.stream().anyMatch(l -> l.getDetail().contains("ALLOW_REGISTRATION: true -> false")));
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
                        .param("action", "SEND_MAIL")
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
