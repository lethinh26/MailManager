package com.mailapp.client;

import com.mailapp.client.model.LoginResponse;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.RegisterRequest;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.model.AdminClientDtos.AdminUserSummary;
import com.mailapp.client.model.MailClientDtos.MailFolderDto;
import com.mailapp.client.model.MailClientDtos.MailDetailResponse;
import com.mailapp.client.model.MailClientDtos.MailSummaryDto;
import com.mailapp.client.model.MailClientDtos.SendMailPayload;
import com.mailapp.client.ui.frames.LoginFrame;
import com.mailapp.client.ui.frames.RegisterFrame;
import com.mailapp.client.ui.frames.MainFrame;
import com.mailapp.client.ui.frames.AdminFrame;
import com.mailapp.client.ui.dialogs.GmailManagerDialog;
import com.mailapp.client.ui.dialogs.ComposeDialog;
import com.mailapp.client.util.ClientConfig;
import com.mailapp.client.util.FileUtil;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.util.SessionStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử CHẠY THẬT với Server đang chạy (Task 1 & Task 6 - Live Server Verification)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
        named = "MAILAPP_E2E",
        matches = "1",
        disabledReason = "Bỏ qua Live Server E2E do biến môi trường MAILAPP_E2E!=1; chỉ kích hoạt khi kiểm thử tích hợp trực tiếp với Server đang chạy"
)
class ClientLiveServerE2ETest {

    private static String SERVER_URL;
    private static String initialAdminPass;
    private static String changedAdminPass;
    private static Path certTempDir;
    private static File validCer;
    private static File invalidCer;
    private static String registeredUsername;
    private static String registeredPassword;

    @BeforeAll
    static void initCertFiles() throws Exception {
        assertEquals("1", System.getenv("MAILAPP_E2E_DISPOSABLE"), "Chỉ chạy trên server E2E riêng, dùng database tạm.");
        SERVER_URL = System.getenv("MAILAPP_E2E_URL");
        assertNotNull(SERVER_URL, "Thiếu MAILAPP_E2E_URL");
        java.net.URI uri = java.net.URI.create(SERVER_URL);
        assertEquals("https", uri.getScheme());
        assertTrue("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        assertTrue(uri.getPort() > 0 && uri.getPort() != 8443, "Không dùng cổng server mặc định");
        assertNotNull(System.getenv("MAILAPP_E2E_CERT"), "Thiếu MAILAPP_E2E_CERT");
        validCer = new File(System.getenv("MAILAPP_E2E_CERT"));
        assertTrue(validCer.isFile());
        initialAdminPass = System.getenv("MAILAPP_E2E_ADMIN_PASSWORD");
        assertNotNull(initialAdminPass, "Thiếu mật khẩu admin test riêng");
        changedAdminPass = System.getenv("MAILAPP_E2E_CHANGED_ADMIN_PASSWORD");
        assertTrue(changedAdminPass != null && !changedAdminPass.isBlank(),
                "Thiếu MAILAPP_E2E_CHANGED_ADMIN_PASSWORD cho fixture");
        assertFalse(changedAdminPass.equals(initialAdminPass), "Mật khẩu admin fixture mới phải khác mật khẩu khởi tạo");

        // Tạo một file cert lạ/giả mạo để kiểm thử từ chối kết nối
        Path tempDir = Files.createTempDirectory("mailclient-test-certs");
        certTempDir = tempDir;
        invalidCer = tempDir.resolve("fake.cer").toFile();

        // Tạo cert lạ bằng keytool
        String javaHome = System.getProperty("java.home");
        String keytoolBin = Path.of(javaHome, "bin", "keytool").toString();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            keytoolBin += ".exe";
        }

        File fakeP12 = tempDir.resolve("fake.p12").toFile();
        String tempStorePass = java.util.UUID.randomUUID().toString().replace("-", "");
        Process p = new ProcessBuilder(
                keytoolBin, "-genkeypair", "-alias", "fake", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-keystore", fakeP12.getAbsolutePath(),
                "-storepass", tempStorePass, "-keypass", tempStorePass,
                "-dname", "CN=fake.server", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-noprompt"
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, p.waitFor(), "Không tạo được chứng chỉ thử nghiệm");

        Process p2 = new ProcessBuilder(
                keytoolBin, "-exportcert", "-alias", "fake", "-keystore", fakeP12.getAbsolutePath(),
                "-storepass", tempStorePass, "-file", invalidCer.getAbsolutePath(), "-noprompt"
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, p2.waitFor(), "Không xuất được chứng chỉ thử nghiệm");

        long ts = System.currentTimeMillis() % 100000;
        registeredUsername = "liveuser_" + ts;
        registeredPassword = "LiveUserPass@" + ts;
    }

    @AfterAll
    static void cleanupCertFiles() throws Exception {
        if (certTempDir != null) {
            Files.deleteIfExists(certTempDir.resolve("fake.cer"));
            Files.deleteIfExists(certTempDir.resolve("fake.p12"));
            Files.deleteIfExists(certTempDir);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Task 1: Chạy thật với Server - Có .cer đúng thì kết nối THÀNH CÔNG (200 OK)")
    void test1_ValidCertConnectionSucceeds() throws Exception {
        assertTrue(validCer.exists(), "File chứng chỉ mailapp.cer phải tồn tại!");
        ApiClient client = new ApiClient(SERVER_URL, validCer);

        PublicConfigDto config = client.getPublicConfig();
        assertNotNull(config);
        assertEquals(20, config.mailPageSize(), "Fresh DB must seed the required default page size");
        System.out.println("[THỰC THI THẬT] GET /api/public/config thành công với cert hợp lệ: " + config);
    }

    @Test
    @Order(2)
    @DisplayName("Task 1: Chạy thật với Server - Đổi sang .cer sai/lạ thì bị TỪ CHỐI (SSLHandshakeException)")
    void test2_InvalidCertConnectionRejected() {
        assertTrue(invalidCer.exists(), "File chứng chỉ giả mạo phải tồn tại!");
        ApiClient client = new ApiClient(SERVER_URL, invalidCer);

        Exception ex = assertThrows(Exception.class, client::getPublicConfig);
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        System.out.println("[THỰC THI THẬT] Kết nối bị từ chối chính xác khi dùng cert sai: " + root.getClass().getName() + " - " + root.getMessage());
        assertTrue(root instanceof SSLHandshakeException ||
                        root instanceof java.security.GeneralSecurityException ||
                        root.getMessage().toLowerCase().contains("signature") ||
                        root.getMessage().toLowerCase().contains("certificate") ||
                        root.getMessage().toLowerCase().contains("pkix"),
                "Máy chủ phải từ chối khi Client dùng cert sai!");
    }

    @Test
    @Order(3)
    @DisplayName("Task 6.1: Đăng ký tài khoản người dùng mới thành công")
    void test3_RegisterNewUser() throws Exception {
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        RegisterRequest req = new RegisterRequest(
                registeredUsername,
                registeredUsername + "@ptit.edu.vn",
                registeredPassword,
                "Người Dùng Thực Nghiệm",
                "USER"
        );

        UserDto created = client.register(req);
        assertNotNull(created);
        assertEquals(registeredUsername, created.username());
        System.out.println("[THỰC THI THẬT] Đăng ký thành công tài khoản: " + created.username() + ", role: " + created.role());
    }

    @Test
    @Order(4)
    @DisplayName("Task 6.2: Đăng nhập thành công với tài khoản vừa đăng ký")
    void test4_LoginSuccess() throws Exception {
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        LoginResponse response = client.login(registeredUsername, registeredPassword);

        assertNotNull(response);
        assertNotNull(response.accessToken());
        assertFalse(response.accessToken().isBlank());
        assertEquals(registeredUsername, response.user().username());
        System.out.println("[THỰC THI THẬT] Đăng nhập thành công, nhận token độ dài: " + response.accessToken().length() + " chars");
    }

    @Test
    @Order(5)
    @DisplayName("Task 6.3: Đăng nhập sai 6 lần liên tiếp -> Bị Rate Limit (HTTP 429) và nhận Retry-After")
    void test5_RateLimiting6Attempts() throws Exception {
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        // Mỗi lần chạy dùng một tên đăng nhập dùng một lần (ngẫu nhiên, đã đăng ký trước)
        String victimUser = "rl_victim_" + (System.currentTimeMillis() % 1000000);
        String victimPass = "VictimPass123!";
        client.register(new RegisterRequest(victimUser, victimUser + "@ptit.edu.vn", victimPass, "Victim User", "USER"));

        // Lần 1 đến 5: Sai mật khẩu -> Bắt buộc trả về HTTP 401 UNAUTHORIZED
        for (int i = 1; i <= 5; i++) {
            try {
                client.login(victimUser, "WrongPassword@" + i);
                fail("Lần " + i + " phải thất bại với 401 UNAUTHORIZED");
            } catch (ApiException ex) {
                assertEquals(401, ex.getStatusCode(), "Lần " + i + " phải trả HTTP 401");
                assertEquals("UNAUTHORIZED", ex.getCode(), "Lần " + i + " mã lỗi phải là UNAUTHORIZED");
                System.out.println("[THỰC THI THẬT] Lần thử " + i + ": HTTP 401 - Mã: " + ex.getCode());
            }
        }

        // Lần thứ 6: Bị khóa Rate Limit -> HTTP 429 kèm header Retry-After
        ApiException rateLimitEx = assertThrows(ApiException.class, () -> client.login(victimUser, "WrongPassword@6"));
        assertEquals(429, rateLimitEx.getStatusCode(), "Lần thứ 6 phải trả về mã HTTP 429!");
        assertTrue("TOO_MANY_ATTEMPTS".equals(rateLimitEx.getCode()) || "RATE_LIMIT_EXCEEDED".equals(rateLimitEx.getCode()),
                "Mã lỗi phải là TOO_MANY_ATTEMPTS hoặc RATE_LIMIT_EXCEEDED! Thực tế: " + rateLimitEx.getCode());
        assertTrue(rateLimitEx.getRetryAfterSeconds() > 0, "Header Retry-After phải lớn hơn 0! Thực tế: " + rateLimitEx.getRetryAfterSeconds());
        System.out.println("[THỰC THI THẬT] Lần thứ 6: HTTP 429 - Mã: " + rateLimitEx.getCode() + ", Retry-After: " + rateLimitEx.getRetryAfterSeconds() + " giây");

        // Khẳng định Rate Limit chỉ ảnh hưởng tài khoản vi phạm, KHÔNG ảnh hưởng tài khoản khác
        LoginResponse otherRes = client.login(registeredUsername, registeredPassword);
        assertNotNull(otherRes);
        assertNotNull(otherRes.accessToken());
        assertEquals(registeredUsername, otherRes.user().username());
        System.out.println("[THỰC THI THẬT] Khẳng định Rate Limit không ảnh hưởng tài khoản khác (" + registeredUsername + ") - đăng nhập thành công!");
    }

    @Test
    @Order(6)
    @DisplayName("Task 6.4: Đăng nhập ADMIN lần đầu (mustChangePassword=true), bị chặn 403 ở API nghiệp vụ, đổi mật khẩu xong mở khóa")
    void test6_AdminLoginAndChangePassword() throws Exception {
        ApiClient client = new ApiClient(SERVER_URL, validCer);

        // 1. Đăng nhập ADMIN lần đầu với mật khẩu khởi tạo
        LoginResponse loginRes = client.login("admin", initialAdminPass);

        assertNotNull(loginRes);
        boolean mustChange = loginRes.user().mustChangePassword();
        assertTrue(mustChange, "Server E2E phải được khởi tạo với DB trống");
        System.out.println("[THỰC THI THẬT] Đăng nhập ADMIN thành công. mustChangePassword = " + mustChange);

        if (mustChange) {
            // 2. Thử gọi API nghiệp vụ (GET /api/gmail-accounts) khi chưa đổi mật khẩu -> BẮT BUỘC bị chặn 403 PASSWORD_CHANGE_REQUIRED
            ApiException blockedEx = assertThrows(ApiException.class, client::getGmailAccounts,
                    "Khi mustChangePassword=true, gọi API nghiệp vụ phải bị chặn với 403 FORBIDDEN!");
            assertEquals(403, blockedEx.getStatusCode(), "Phải trả về HTTP 403");
            assertEquals("PASSWORD_CHANGE_REQUIRED", blockedEx.getCode(), "Mã lỗi phải là PASSWORD_CHANGE_REQUIRED");
            System.out.println("[THỰC THI THẬT] Bị chặn 403 PASSWORD_CHANGE_REQUIRED chính xác khi gọi API nghiệp vụ!");

            // 3. Thực hiện đổi mật khẩu sang mật khẩu mới
            String newPass = changedAdminPass;
            var changeRes = client.changePassword(initialAdminPass, newPass);
            assertNotNull(changeRes);
            assertNotNull(changeRes.accessToken());
            assertFalse(changeRes.user().mustChangePassword(), "Sau khi đổi mật khẩu, mustChangePassword phải là false!");
            System.out.println("[THỰC THI THẬT] Đổi mật khẩu ADMIN thành công! Token mới đã được cấp.");

            // 4. Mở khóa: Gọi lại API nghiệp vụ -> Thành công 200 OK
            var accounts = client.getGmailAccounts();
            assertNotNull(accounts, "Sau khi đổi mật khẩu, API nghiệp vụ được mở khóa thành công!");
            System.out.println("[THỰC THI THẬT] Gọi lại API nghiệp vụ (GET /api/gmail-accounts) thành công sau khi mở khóa!");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Task 6.5: Lấy URL ủy quyền Google OAuth2 (GET /api/oauth2/authorize-url)")
    void test7_GetOAuth2AuthorizeUrl() throws Exception {
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        client.login(registeredUsername, registeredPassword);

        var link = client.startOAuth2Link(null);
        assertTrue(link.authorizeUrl().startsWith("https://accounts.google.com/o/oauth2"));
        assertTrue(link.authorizeUrl().contains("code_challenge_method=S256"));
        assertNotNull(link.pollToken());
        assertEquals("PENDING", client.pollOAuth2Link(link.pollToken()).status());
    }

    @Test
    @Order(8)
    @DisplayName("Task 6.6: Tắt server / Sai cổng -> Báo lỗi mạng không kết nối được")
    void test8_NetworkErrorWhenServerDown() {
        // Cổng 59999 không có server lắng nghe
        ApiClient deadClient = new ApiClient("https://localhost:59999", validCer);

        Exception ex = assertThrows(Exception.class, deadClient::getPublicConfig);
        System.out.println("[THỰC THI THẬT] Bắt chính xác lỗi mạng khi không kết nối được máy chủ: " + ex.getMessage());
    }

    @Test
    @Order(9)
    @DisplayName("Task 6.7: API Hộp thư thật chống IDOR (404 khi gmailId không thuộc người dùng)")
    void test9_MailEndpointsAntiIdor() throws Exception {
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        client.login(registeredUsername, registeredPassword);

        ApiException ex = assertThrows(ApiException.class, () -> client.getMailFolders(999999L));
        assertEquals(404, ex.getStatusCode(), "Truy cập gmailId không thuộc sở hữu phải trả về 404!");
        System.out.println("[THỰC THI THẬT] Anti-IDOR /api/mails/folders trả 404 chính xác: " + ex.getCode());
    }

    @Test
    @Order(10)
    @DisplayName("Task 6.8: E2E Sống — USER gọi Admin bị 403; ADMIN khóa user -> JWT của user bị đá 401 NGAY LẬP TỨC")
    void test10_AdminLocksUserAndInvalidatesSessionImmediately() throws Exception {
        ApiClient userClient = new ApiClient(SERVER_URL, validCer);
        LoginResponse userLogin = userClient.login(registeredUsername, registeredPassword);
        Long targetUserId = userLogin.user().id();

        // 1. USER gọi /api/admin/users -> 403 FORBIDDEN
        ApiException forbiddenEx = assertThrows(ApiException.class,
                () -> userClient.adminListUsers(null, null, null, 0, 10, "createdAt", "desc"));
        assertEquals(403, forbiddenEx.getStatusCode());

        // 2. Đăng nhập ADMIN
        ApiClient adminClient = new ApiClient(SERVER_URL, validCer);
        adminClient.login("admin", changedAdminPass);

        // 3. Admin khóa user -> userClient gọi /api/auth/me bằng JWT cũ lập tức bị đá 401!
        var locked = adminClient.adminLockUser(targetUserId);
        assertFalse(locked.enabled());

        ApiException kickedEx = assertThrows(ApiException.class, userClient::getCurrentUser);
        assertEquals(401, kickedEx.getStatusCode(), "JWT của user bị khóa phải vô hiệu ngay lập tức (401)!");
        System.out.println("[THỰC THI THẬT] Admin khóa user id=" + targetUserId + " -> JWT cũ bị đá ngay với HTTP 401!");

        // 4. Mở khóa lại để giữ sạch dữ liệu
        var unlocked = adminClient.adminUnlockUser(targetUserId);
        assertTrue(unlocked.enabled());
        assertEquals(registeredUsername, userClient.getCurrentUser().username());

        // 5. Xác minh endpoint /api/dev/** bị khóa (không token -> 401, USER thường -> 403)
        ApiClient anonClient = new ApiClient(SERVER_URL, validCer);
        ApiException anonDevEx = assertThrows(ApiException.class,
                () -> anonClient.postRaw("/api/dev/maintenance-mode?enabled=true"));
        assertEquals(401, anonDevEx.getStatusCode(), "Gọi /api/dev/** không token phải trả 401!");

        ApiException userDevEx = assertThrows(ApiException.class,
                () -> userClient.postRaw("/api/dev/maintenance-mode?enabled=true"));
        assertEquals(403, userDevEx.getStatusCode(), "USER thường gọi /api/dev/** phải trả 403!");
        System.out.println("[THỰC THI THẬT] /api/dev/** trả 401 khi không token và 403 với USER thường!");
    }

    @Test
    @Order(11)
    void test11_DynamicSettingsMaintenanceAndPersistedAudit() throws Exception {
        ApiClient admin = new ApiClient(SERVER_URL, validCer);
        admin.login("admin", changedAdminPass);
        ApiClient user = new ApiClient(SERVER_URL, validCer);
        Long userId = user.login(registeredUsername, registeredPassword).user().id();
        try {
            admin.adminUpdateSettings(java.util.Map.of("MAIL_PAGE_SIZE", "50", "LOGIN_MAX_FAILED_ATTEMPTS", "3"));
            assertEquals(50, user.getPublicConfig().mailPageSize());
            ApiClient attempt = new ApiClient(SERVER_URL, validCer);
            for (int i = 0; i < 3; i++) {
                assertEquals(401, assertThrows(ApiException.class,
                        () -> attempt.login(registeredUsername, "WrongE2ePass@")).getStatusCode());
            }
            assertEquals(429, assertThrows(ApiException.class,
                    () -> attempt.login(registeredUsername, "WrongE2ePass@")).getStatusCode());
            var logs = admin.adminSearchLogs(userId, null, "LOGIN_FAILED", null, null, 0, 20);
            assertTrue(logs.totalElements() >= 3, "Audit log đăng nhập thất bại phải tồn tại sau rollback");
            admin.adminUpdateSettings(java.util.Map.of("MAINTENANCE_MODE", "true"));
            ApiException maintenance = assertThrows(ApiException.class, user::getCurrentUser);
            assertEquals(503, maintenance.getStatusCode());
            assertEquals("MAINTENANCE_MODE", maintenance.getCode());
            assertNotNull(admin.getCurrentUser());
            var loginDuringMaintenance = new ApiClient(SERVER_URL, validCer);
            ApiException refused = assertThrows(ApiException.class,
                    () -> loginDuringMaintenance.login(registeredUsername, registeredPassword));
            // The same user's rate limit may still be active; use an unrelated
            // fresh USER to verify login-only maintenance below in the mail test.
            assertTrue(refused.getStatusCode() == 403 || refused.getStatusCode() == 429);
        } finally {
            admin.adminUpdateSettings(java.util.Map.of("MAINTENANCE_MODE", "false", "MAIL_PAGE_SIZE", "20",
                    "LOGIN_MAX_FAILED_ATTEMPTS", "5"));
        }
    }

    @Test
    @Order(12)
    void test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail(@TempDir Path output) throws Exception {
        int imaps = Integer.parseInt(System.getenv("MAILAPP_E2E_IMAPS_PORT"));
        int smtp = Integer.parseInt(System.getenv("MAILAPP_E2E_SMTP_PORT"));
        assertTrue(imaps > 1024 && smtp > 1024 && imaps != smtp);
        GreenMail greenMail = new GreenMail(new ServerSetup[]{
                new ServerSetup(imaps, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS),
                new ServerSetup(smtp, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)
        });
        String mailbox = "fixture@test.local";
        String appPassword = "FixtureAppPass123";
        String username = "mail_fixture_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "FixtureLogin@123";
        try {
            greenMail.start();
            greenMail.setUser(mailbox, appPassword);
            ApiClient owner = new ApiClient(SERVER_URL, validCer);
            owner.register(new RegisterRequest(username, username + "@test.local", password, "Mail Fixture", "USER"));
            Long userId = owner.login(username, password).user().id();
            ApiClient admin = new ApiClient(SERVER_URL, validCer);
            admin.login("admin", changedAdminPass);
            try {
                admin.adminUpdateSettings(Map.of("MAINTENANCE_MODE", "true"));
                ApiException refused = assertThrows(ApiException.class,
                        () -> new ApiClient(SERVER_URL, validCer).login(username, password));
                assertEquals(403, refused.getStatusCode());
                assertEquals("MAINTENANCE_MODE", refused.getCode());
            } finally {
                admin.adminUpdateSettings(Map.of("MAINTENANCE_MODE", "false"));
            }

            assertEquals(422, assertThrows(ApiException.class,
                    () -> owner.linkGmailWithAppPassword(mailbox, "WrongFixturePass", null)).getStatusCode());
            assertTrue(owner.getGmailAccounts().isEmpty());
            var account = owner.linkGmailWithAppPassword(mailbox, appPassword, "Fixture mailbox");
            assertNotNull(account.id());
            assertTrue(account.hasAppPassword());
            assertEquals(1, owner.getGmailAccounts().size());
            assertTrue(owner.getMailFolders(account.id()).stream().anyMatch(f -> "INBOX".equalsIgnoreCase(f.name())));

            Path source = Files.writeString(output.resolve("dinh-kem.txt"), "UTF-8 fixture: kiểm thử đính kèm");
            String subject = "E2E fixture " + UUID.randomUUID();
            SendMailPayload payload = new SendMailPayload(account.id(), List.of(mailbox), List.of(), List.of(),
                    subject, "Nội dung kiểm thử UTF-8", null, null, null);
            try {
                admin.adminUpdateSettings(Map.of("ALLOW_SEND_MAIL", "false"));
                ApiException disabled = assertThrows(ApiException.class,
                        () -> owner.sendMailMultipart(payload, List.of(source.toFile())));
                assertEquals(403, disabled.getStatusCode());
                assertEquals("FEATURE_DISABLED", disabled.getCode());
            } finally {
                admin.adminUpdateSettings(Map.of("ALLOW_SEND_MAIL", "true"));
            }
            assertEquals("SENT", owner.sendMailMultipart(payload, List.of(source.toFile())).status());
            assertTrue(greenMail.waitForIncomingEmail(5000, 1), "Mail must arrive at the isolated local fixture");
            var page = owner.getMails(account.id(), "INBOX", 0, 10, subject, null, true, null, null);
            assertEquals(1L, page.total());
            var item = page.items().getFirst();
            assertTrue(item.hasAttachment());
            assertEquals(subject, item.subject());
            var preview = owner.getMailDetail(account.id(), "INBOX", item.uid(), false, page.uidValidity(), false);
            assertFalse(preview.flags().seen());
            var detail = owner.getMailDetail(account.id(), "INBOX", item.uid(), false, page.uidValidity());
            assertTrue(detail.flags().seen());
            assertTrue(detail.textBody().contains("Nội dung kiểm thử UTF-8"));
            assertEquals(1, detail.attachments().size());
            var attachment = detail.attachments().getFirst();
            var key = new SessionStore.AttachmentKey(account.id(), "INBOX", page.uidValidity(), item.uid(), attachment.index());
            var download = FileUtil.download(owner, key, source);
            assertNotEquals(source.toAbsolutePath(), download.file().toPath().toAbsolutePath());
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(download.file().toPath()));
            var flags = owner.updateMailFlags(account.id(), "INBOX", item.uid(), false, true, page.uidValidity());
            assertFalse(flags.seen());
            assertTrue(flags.flagged());
            assertTrue(owner.getMails(account.id(), "INBOX", 0, 10, subject, true, true, null, null)
                    .items().getFirst().flags().flagged());
            assertEquals(409, assertThrows(ApiException.class, () -> owner.getMailDetail(
                    account.id(), "INBOX", item.uid(), false, page.uidValidity() + 1)).getStatusCode());
            ApiClient outsider = new ApiClient(SERVER_URL, validCer);
            // Admin is not an owner of this mailbox either.
            outsider.login("admin", changedAdminPass);
            assertEquals(404, assertThrows(ApiException.class, () -> outsider.getMailFolders(account.id())).getStatusCode());
            // The real fixture supports UIDPLUS; verify the targeted move and permanent delete.
            owner.deleteMail(account.id(), "INBOX", item.uid(), false, page.uidValidity());
            assertEquals(0L, owner.getMails(account.id(), "INBOX", 0, 10, subject, null, null, null, null).total());
            var trash = owner.getMailFolders(account.id()).stream()
                    .filter(folder -> "TRASH".equalsIgnoreCase(folder.type()))
                    .findFirst().orElseThrow();
            var trashPage = owner.getMails(account.id(), trash.name(), 0, 10, subject, null, null, null, null);
            assertEquals(1L, trashPage.total());
            var trashItem = trashPage.items().getFirst();
            assertEquals(subject, trashItem.subject());
            assertEquals(400, assertThrows(ApiException.class, () -> owner.deleteMail(
                    account.id(), trash.name(), trashItem.uid(), false, trashPage.uidValidity())).getStatusCode());
            owner.deleteMail(account.id(), trash.name(), trashItem.uid(), true, trashPage.uidValidity());
            assertEquals(0L, owner.getMails(account.id(), trash.name(), 0, 10, subject, null, null, null, null).total());
            owner.deleteGmailAccount(account.id());
            assertTrue(owner.getGmailAccounts().isEmpty());
            for (String action : List.of("LOGIN", "LINK_GMAIL", "SEND_MAIL", "MAIL_FLAG_CHANGE", "MAIL_DELETE", "UNLINK_GMAIL")) {
                var logs = admin.adminSearchLogs(userId, null, action, null, null, 0, 20);
                assertTrue(logs.totalElements() > 0, "Missing persisted audit: " + action);
                if ("MAIL_DELETE".equals(action)) assertEquals(2L, logs.totalElements());
                for (var log : logs.content()) {
                    assertFalse(String.valueOf(log.detail()).contains(appPassword));
                    assertFalse(String.valueOf(log.detail()).contains(password));
                }
            }
        } finally {
            greenMail.stop();
        }
    }

    // These tests intentionally exercise the displayed Swing components with
    // their real ApiClient and background workers. They are automated UI E2E,
    // not manual acceptance. No Robot, real browser, external Gmail or chooser.

    @Test
    @Order(13)
    @Timeout(120)
    void test13_SwingRegistrationLoginAndDynamicPublicConfig(@TempDir Path output) throws Exception {
        requireUiFixture(output);
        ApiClient admin = fixtureAdmin();
        Map<String, String> original = settingsSnapshot(admin, "ALLOW_REGISTRATION", "ALLOW_OAUTH2_LINK");
        String username = uniqueUiUser("register");
        String password = "UiRegisterPass123!";
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        try (UiWindows windows = new UiWindows(client)) {
            admin.adminUpdateSettings(Map.of("ALLOW_REGISTRATION", "true", "ALLOW_OAUTH2_LINK", "true"));
            LoginFrame login = showLogin(client);
            awaitUi("initial public config on login", () ->
                    button(login, "registerButton").isVisible() && button(login, "googleLoginButton").isVisible());
            edt(() -> {
                button(login, "registerButton").doClick();
                return null;
            });
            RegisterFrame register = windows.awaitWindow(RegisterFrame.class);
            edt(() -> {
                text(register, "passwordField").setText(password);
                return null;
            });
            admin.adminUpdateSettings(Map.of("ALLOW_REGISTRATION", "false", "ALLOW_OAUTH2_LINK", "false"));
            // Allow the real LoginFrame public-config polling timer to update the
            // already open registration window and clear the password.
            awaitUi("registration and Google controls disabled by server polling", () ->
                    !button(login, "registerButton").isVisible()
                            && !button(login, "googleLoginButton").isVisible()
                            && !button(register, "registerButton").isEnabled()
                            && ((JPasswordField) text(register, "passwordField")).getPassword().length == 0);
            admin.adminUpdateSettings(Map.of("ALLOW_REGISTRATION", "true", "ALLOW_OAUTH2_LINK", "true"));
            awaitUi("registration controls re-enabled by server polling", () ->
                    button(login, "registerButton").isVisible() && button(register, "registerButton").isEnabled());
            edt(() -> {
                text(register, "usernameField").setText(username);
                text(register, "fullNameField").setText("Swing Registration Fixture");
                text(register, "emailField").setText(username + "@test.local");
                text(register, "passwordField").setText(password);
                text(register, "confirmPasswordField").setText(password);
                button(register, "registerButton").doClick();
                return null;
            });
            acknowledge(windows, register, "Thành công", username, false);
            awaitUi("registration returns to login", () -> !register.isDisplayable()
                    && text(login, "usernameField").getText().equals(username));
            edt(() -> {
                text(login, "passwordField").setText("WrongUiFixturePassword1!");
                button(login, "loginButton").doClick();
                return null;
            });
            awaitUi("wrong password shown without creating a session", () ->
                    label(login, "errorLabel").getText().contains("Sai tên đăng nhập hoặc mật khẩu")
                            && button(login, "loginButton").isEnabled()
                            && !SessionStore.getInstance().hasActiveSession());
            MainFrame main = loginThroughUi(windows, login, username, password);
            awaitUi("ordinary user rendered by MainFrame", () ->
                    label(main, "userStatusLabel").getText().contains(username + " (USER)"));
            edt(() -> {
                assertFalse(button(main, "adminToolbarBtn").isVisible());
                return null;
            });
            assertEquals(username, client.getCurrentUser().username());
            assertEquals("USER", client.getCurrentUser().role());
        } finally {
            admin.adminUpdateSettings(original);
            SessionStore.getInstance().clearSession();
        }
    }

    @Test
    @Order(14)
    @Timeout(180)
    void test14_SwingMailLifecycleThroughRealHttpsAndGreenMail(@TempDir Path output) throws Exception {
        requireUiFixture(output);
        int imaps = Integer.parseInt(System.getenv("MAILAPP_E2E_IMAPS_PORT"));
        int smtp = Integer.parseInt(System.getenv("MAILAPP_E2E_SMTP_PORT"));
        assertTrue(imaps > 1024 && smtp > 1024 && imaps != smtp);
        GreenMail greenMail = new GreenMail(new ServerSetup[]{
                new ServerSetup(imaps, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS),
                new ServerSetup(smtp, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)
        });
        ApiClient admin = fixtureAdmin();
        Map<String, String> original = settingsSnapshot(admin, "ALLOW_APP_PASSWORD_LINK",
                "ALLOW_SEND_MAIL", "MAIL_PAGE_SIZE", "MAX_GMAIL_PER_USER");
        // This suite uses a freshly seeded DB; never irreversibly migrate a
        // legacy unlimited quota while trying to restore fixture settings.
        assertTrue(Integer.parseInt(original.get("MAX_GMAIL_PER_USER")) > 0);
        String username = uniqueUiUser("mail");
        String mailbox = username + "@test.local";
        String password = "UiMailLoginPass123!";
        String appPassword = "UiFixturePass123";
        String marker = "Swing-" + UUID.randomUUID().toString().substring(0, 8);
        String fixtureSubject = marker + " fixture 05";
        String sentSubject = marker + " sent";
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        Long userId = client.register(new RegisterRequest(username, mailbox, password, "Swing Mail Fixture", "USER")).id();
        try (UiWindows windows = new UiWindows(client)) {
            greenMail.start();
            greenMail.setUser(mailbox, appPassword);
            // Deliver distinct messages directly to the disposable IMAP store
            // so the UI must page a real mailbox, without hitting send limits.
            jakarta.mail.Session mailSession = greenMail.getSmtp().createSession();
            for (int i = 0; i < 12; i++) {
                var message = new jakarta.mail.internet.MimeMessage(mailSession);
                message.setFrom(new jakarta.mail.internet.InternetAddress("sender@test.local"));
                message.setRecipient(jakarta.mail.Message.RecipientType.TO, new jakarta.mail.internet.InternetAddress(mailbox));
                message.setSubject(marker + String.format(java.util.Locale.ROOT, " fixture %02d", i), "UTF-8");
                if (i == 5) {
                    var parts = new jakarta.mail.internet.MimeMultipart("mixed");
                    var body = new jakarta.mail.internet.MimeBodyPart();
                    body.setText("Nội dung Swing fixture " + i, "UTF-8");
                    parts.addBodyPart(body);
                    var attachment = new jakarta.mail.internet.MimeBodyPart();
                    attachment.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource(
                            "UI attachment fixture".getBytes(StandardCharsets.UTF_8), "text/plain")));
                    attachment.setFileName("ui-fixture.txt");
                    attachment.setDisposition(jakarta.mail.Part.ATTACHMENT);
                    parts.addBodyPart(attachment);
                    message.setContent(parts);
                } else {
                    message.setText("Nội dung Swing fixture " + i, "UTF-8");
                }
                message.saveChanges();
                greenMail.getUserManager().getUser(mailbox).deliver(message);
            }
            admin.adminUpdateSettings(Map.of("ALLOW_APP_PASSWORD_LINK", "true", "ALLOW_SEND_MAIL", "true",
                    "MAIL_PAGE_SIZE", "10", "MAX_GMAIL_PER_USER", "1"));
            MainFrame main = loginThroughUi(windows, showLogin(client), username, password);
            awaitUi("empty mailbox and loaded public config", () ->
                    button(main, "composeBtn").isEnabled()
                            && label(main, "userStatusLabel").getText().contains(username)
                            && label(main, "listEmptyTitle").getText().contains("Chưa liên kết"));

            // Open the real three-step App Password wizard via the MainFrame.
            FutureTask<Void> managerClick = clickModal(buttonOnEdt(main, "listEmptyActionBtn"));
            GmailManagerDialog manager = windows.awaitWindow(GmailManagerDialog.class);
            awaitUi("Gmail manager account list loaded", () ->
                    button(manager, "appPassBtn").isShowing()
                            && label(manager, "statusLabel").getText().contains("hộp thư"));
            completeAppPasswordWizard(windows, manager, mailbox, appPassword, false);
            var linked = client.getGmailAccounts();
            assertEquals(1, linked.size());
            assertTrue(linked.getFirst().hasAppPassword());
            Long gmailId = linked.getFirst().id();
            awaitUi("linked Gmail displayed in manager", () -> hasLabel(manager, mailbox));
            // At quota one, the same mailbox is an UPSERT, not a second link.
            completeAppPasswordWizard(windows, manager, mailbox, appPassword, true);
            assertEquals(1, client.getGmailAccounts().size());
            assertEquals(gmailId, client.getGmailAccounts().getFirst().id());
            admin.adminUpdateSettings(Map.of("ALLOW_APP_PASSWORD_LINK", "false"));
            awaitUi("open Gmail manager hides disabled App Password linking", () ->
                    !button(manager, "appPassBtn").isVisible() && hasLabel(manager, mailbox));
            edt(() -> { visibleButton(manager, "Đóng").doClick(); return null; });
            finishEdt(managerClick);
            admin.adminUpdateSettings(Map.of("ALLOW_APP_PASSWORD_LINK", "true"));

            awaitUi("first page from server default page size", () ->
                    readyPage(main, 10) && button(main, "nextPageBtn").isEnabled());
            List<Long> pageOne = edt(() -> uiMailUids(main));
            edt(() -> { button(main, "nextPageBtn").doClick(); return null; });
            awaitUi("second page from actual IMAP", () -> readyPage(main, 2)
                    && button(main, "previousPageBtn").isEnabled() && !button(main, "nextPageBtn").isEnabled());
            List<Long> pageTwo = edt(() -> uiMailUids(main));
            assertTrue(Collections.disjoint(pageOne, pageTwo));
            edt(() -> { button(main, "previousPageBtn").doClick(); return null; });
            awaitUi("previous page preserves order", () -> readyPage(main, 10) && uiMailUids(main).equals(pageOne));
            edt(() -> { ((JToggleButton) field(main, "attachFilterBtn")).doClick(); return null; });
            awaitUi("attachment filter selects only multipart fixture", () -> readyPage(main, 1)
                    && uiMail(main, 0).subject().equals(fixtureSubject) && uiMail(main, 0).hasAttachment());
            edt(() -> { ((JToggleButton) field(main, "attachFilterBtn")).doClick(); return null; });
            awaitUi("attachment filter cleared", () -> readyPage(main, 10));
            // Search, selection and detail use real controls and the same UID.
            searchThroughUi(main, fixtureSubject);
            awaitUi("search results contain exact fixture", () -> readyPage(main, 1)
                    && uiMail(main, 0).subject().equals(fixtureSubject));
            edt(() -> { mailList(main).setSelectedIndex(0); return null; });
            awaitUi("selected message detail", () -> detailReady(main, fixtureSubject)
                    && ((JEditorPane) field(main, "bodyPane")).getText().contains("Nội dung Swing fixture 5")
                    && uiMail(main, 0).flags().seen());
            edt(() -> {
                JPanel attachments = (JPanel) field(main, "attachmentsStrip");
                assertTrue(components(attachments, JLabel.class).stream().anyMatch(l ->
                        l.isShowing() && l.getText().startsWith("ui-fixture.txt")));
                assertTrue(visibleButton(attachments, "Tải xuống").isEnabled());
                assertTrue(visibleButton(attachments, "Mở").isEnabled());
                return null;
            });
            edt(() -> { button(main, "starActionBtn").doClick(); return null; });
            awaitUi("star flag reflected in list", () -> readyPage(main, 1) && uiMail(main, 0).flags().flagged()
                    && "Bỏ gắn sao".equals(button(main, "starActionBtn").getToolTipText()));
            edt(() -> { button(main, "unreadActionBtn").doClick(); return null; });
            awaitUi("unread flag reflected in list", () -> readyPage(main, 1) && !uiMail(main, 0).flags().seen());
            var persisted = client.getMails(gmailId, "INBOX", 0, 10, fixtureSubject, true, null, null, null);
            assertEquals(1L, persisted.total());
            assertTrue(persisted.items().getFirst().flags().flagged());

            // Open a real ComposeDialog and prove a server config update
            // disables an already open Send button. Then re-enable and send.
            FutureTask<Void> composeClick = clickModal(buttonOnEdt(main, "composeBtn"));
            ComposeDialog compose = windows.awaitWindow(ComposeDialog.class);
            edt(() -> {
                JComboBox<?> from = (JComboBox<?>) field(compose, "fromCombo");
                assertEquals(1, from.getItemCount());
                from.setSelectedIndex(0);
                text(field(compose, "toInput"), "inputField").setText(mailbox);
                text(compose, "subjectField").setText(sentSubject);
                ((JTextArea) field(compose, "bodyArea")).setText("Swing SMTP UTF-8 fixture");
                return null;
            });
            admin.adminUpdateSettings(Map.of("ALLOW_SEND_MAIL", "false"));
            awaitUi("live Compose disabled while open", () -> !button(compose, "sendBtn").isEnabled()
                    && !button(main, "composeBtn").isEnabled());
            admin.adminUpdateSettings(Map.of("ALLOW_SEND_MAIL", "true"));
            awaitUi("live Compose re-enabled", () -> button(compose, "sendBtn").isEnabled());
            edt(() -> { button(compose, "sendBtn").doClick(); return null; });
            acknowledge(windows, compose, "Gửi thư", "Đã gửi thư thành công", false);
            finishEdt(composeClick);
            awaitUi("compose closed after success", () -> !compose.isDisplayable());
            awaitUi("mailbox reloaded after compose", () -> readyPage(main, 1));
            searchThroughUi(main, sentSubject);
            awaitUi("SMTP self-send visible in inbox", () -> readyPage(main, 1)
                    && uiMail(main, 0).subject().equals(sentSubject));
            edt(() -> { mailList(main).setSelectedIndex(0); return null; });
            awaitUi("sent mail selected", () -> detailReady(main, sentSubject)
                    && ((JEditorPane) field(main, "bodyPane")).getText().contains("Swing SMTP UTF-8 fixture"));

            FutureTask<Void> deleteClick = clickModal(buttonOnEdt(main, "deleteBtn"));
            acknowledge(windows, main, "Xác nhận xóa thư", "Thùng rác", true);
            finishEdt(deleteClick);
            awaitUi("soft deletion removed source message", () -> readyPage(main, 0));
            assertEquals(0, client.getMails(gmailId, "INBOX", 0, 10, sentSubject, null, null, null, null).total());
            // Re-select the real sidebar account card so the newly created
            // Trash folder is loaded even on IMAP servers without it initially.
            edt(() -> {
                JPanel accounts = (JPanel) field(main, "accountsListPanel");
                Component card = accounts.getComponent(0);
                card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                        0, 5, 5, 1, false, MouseEvent.BUTTON1));
                return null;
            });
            awaitUi("Trash folder visible after reload", () -> findTrash(main) != null && folderList(main).isEnabled());
            edt(() -> {
                folderList(main).setSelectedValue(findTrash(main), true);
                return null;
            });
            awaitUi("message moved into Trash", () -> readyPage(main, 1)
                    && uiMail(main, 0).subject().equals(sentSubject));
            edt(() -> { mailList(main).setSelectedIndex(0); return null; });
            awaitUi("Trash detail loaded", () -> detailReady(main, sentSubject));
            FutureTask<Void> permanentClick = clickModal(buttonOnEdt(main, "deleteBtn"));
            acknowledge(windows, main, "Xác nhận xóa thư", "XÓA VĨNH VIỄN", true);
            finishEdt(permanentClick);
            awaitUi("permanent deletion removed Trash message", () -> readyPage(main, 0));
            String trashName = edt(() -> ((MailFolderDto) folderList(main).getSelectedValue()).name());
            assertEquals(0, client.getMails(gmailId, trashName, 0, 10, sentSubject, null, null, null, null).total());

            // Delete only our linked fixture account via actual manager controls.
            FutureTask<Void> unlinkManagerClick = clickModal(edt(() ->
                    tooltipButton(main, "Quản lý tài khoản Gmail (OAuth2 / App Password)")));
            GmailManagerDialog unlinkManager = windows.awaitWindow(GmailManagerDialog.class);
            awaitUi("fixture link shown", () -> hasLabel(unlinkManager, mailbox));
            FutureTask<Void> unlinkClick = clickModal(edt(() -> visibleButton(unlinkManager, "Xóa liên kết")));
            acknowledge(windows, unlinkManager, "Xác nhận hủy liên kết", mailbox, true);
            finishEdt(unlinkClick);
            awaitUi("manager reflects unlinked fixture", () -> !hasLabel(unlinkManager, mailbox));
            assertTrue(client.getGmailAccounts().isEmpty());
            edt(() -> { visibleButton(unlinkManager, "Đóng").doClick(); return null; });
            finishEdt(unlinkManagerClick);
            awaitUi("main reflects unlinked fixture", () -> label(main, "listEmptyTitle").getText().contains("Chưa liên kết"));
            for (String action : List.of("LINK_GMAIL", "UPDATE_GMAIL_METHOD", "SEND_MAIL", "MAIL_FLAG_CHANGE", "MAIL_DELETE", "UNLINK_GMAIL")) {
                var logs = admin.adminSearchLogs(userId, null, action, null, null, 0, 20);
                assertTrue(logs.totalElements() > 0, "Missing persisted UI audit: " + action);
                if ("MAIL_DELETE".equals(action)) assertEquals(2L, logs.totalElements());
            }
        } finally {
            try {
                admin.adminUpdateSettings(original);
            } finally {
                SessionStore.getInstance().clearSession();
                greenMail.stop();
            }
        }
    }

    @Test
    @Order(15)
    @Timeout(120)
    void test15_SwingAdminTabsSettingsUserActionsAndAudit(@TempDir Path output) throws Exception {
        requireUiFixture(output);
        ApiClient admin = fixtureAdmin();
        Map<String, String> original = settingsSnapshot(admin, "MAIL_PAGE_SIZE", "ALLOW_SEND_MAIL");
        String username = uniqueUiUser("admin_target");
        String password = "UiAdminTargetPass123!";
        ApiClient ordinary = new ApiClient(SERVER_URL, validCer);
        ordinary.register(new RegisterRequest(username, username + "@test.local", password, "Swing Admin Target", "USER"));
        Long userId = ordinary.login(username, password).user().id();
        ApiClient uiClient = new ApiClient(SERVER_URL, validCer);
        boolean restoreLock = false;
        try (UiWindows windows = new UiWindows(uiClient)) {
            MainFrame main = loginThroughUi(windows, showLogin(uiClient), "admin", changedAdminPass);
            awaitUi("admin toolbar visible", () -> button(main, "adminToolbarBtn").isShowing());
            edt(() -> { button(main, "adminToolbarBtn").doClick(); return null; });
            AdminFrame frame = windows.awaitWindow(AdminFrame.class);
            JTabbedPane tabs = edt(() -> (JTabbedPane) field(frame, "tabbedPane"));
            edt(() -> {
                assertEquals(4, tabs.getTabCount());
                tabs.setSelectedIndex(0);
                text(frame, "userSearchField").setText(username);
                text(frame, "userSearchField").postActionEvent();
                return null;
            });
            awaitUi("admin searches only fixture user", () -> userRow(frame, userId, true));
            edt(() -> { ((JTable) field(frame, "userTable")).setRowSelectionInterval(0, 0); return null; });
            restoreLock = true;
            FutureTask<Void> lockClick = clickModal(buttonOnEdt(frame, "lockUnlockBtn"));
            acknowledge(windows, frame, "Xác nhận", username, true);
            finishEdt(lockClick);
            awaitUi("admin table reflects locked user", () -> userRow(frame, userId, false));
            assertEquals(401, assertThrows(ApiException.class, ordinary::getCurrentUser).getStatusCode());
            edt(() -> { ((JTable) field(frame, "userTable")).setRowSelectionInterval(0, 0); return null; });
            FutureTask<Void> unlockClick = clickModal(buttonOnEdt(frame, "lockUnlockBtn"));
            acknowledge(windows, frame, "Xác nhận", username, true);
            finishEdt(unlockClick);
            awaitUi("admin table reflects unlocked user", () -> userRow(frame, userId, true));
            restoreLock = false;
            assertEquals(username, ordinary.login(username, password).user().username());

            edt(() -> { tabs.setSelectedIndex(1); return null; });
            awaitUi("settings loaded into actual form", () -> button(frame, "settingsSaveBtn").isEnabled()
                    && ((JSpinner) field(frame, "spnMailPageSize")).getValue().toString().equals(original.get("MAIL_PAGE_SIZE")));
            int updatedSize = "10".equals(original.get("MAIL_PAGE_SIZE")) ? 20 : 10;
            boolean sendAllowed = !Boolean.parseBoolean(original.get("ALLOW_SEND_MAIL"));
            edt(() -> {
                ((JSpinner) field(frame, "spnMailPageSize")).setValue(updatedSize);
                JCheckBox allowSend = (JCheckBox) field(frame, "chkAllowSendMail");
                assertNotEquals(sendAllowed, allowSend.isSelected());
                allowSend.doClick();
                button(frame, "settingsSaveBtn").doClick();
                return null;
            });
            acknowledge(windows, frame, "Thành công", "Đã lưu cấu hình", false);
            awaitUi("settings saved and MainFrame refreshed", () -> button(frame, "settingsSaveBtn").isEnabled()
                    && button(main, "composeBtn").isEnabled() == sendAllowed);
            PublicConfigDto persisted = ordinary.getPublicConfig();
            assertEquals(updatedSize, persisted.mailPageSize());
            assertEquals(sendAllowed, persisted.sendMailEnabled());

            // Stats and audit are fetched by AdminFrame's own Swing workers.
            long expectedUsers = admin.adminGetStats().totalUsers();
            edt(() -> {
                tabs.setSelectedIndex(2);
                visibleButton(frame, "Làm mới thống kê").doClick();
                return null;
            });
            awaitUi("stats render persisted total", () ->
                    label(frame, "kpiTotalUsersVal").getText().equals(String.valueOf(expectedUsers)));
            edt(() -> {
                tabs.setSelectedIndex(3);
                text(frame, "logUserIdField").setText(String.valueOf(userId));
                ((JComboBox<?>) field(frame, "logActionCombo")).setSelectedItem("LOGIN");
                visibleButton(frame, "Lọc").doClick();
                return null;
            });
            awaitUi("audit tab filters persisted user LOGIN", () -> {
                JTable table = (JTable) field(frame, "logTable");
                if (table.getRowCount() == 0) return false;
                for (int row = 0; row < table.getRowCount(); row++) {
                    if (!username.equals(table.getValueAt(row, 1)) || !"LOGIN".equals(table.getValueAt(row, 2))) return false;
                    assertFalse(String.valueOf(table.getValueAt(row, 3)).contains(password));
                }
                return true;
            });
        } finally {
            try {
                if (restoreLock) admin.adminUnlockUser(userId);
            } finally {
                admin.adminUpdateSettings(original);
                SessionStore.getInstance().clearSession();
            }
        }
    }

    @Test
    @Order(16)
    @Timeout(45)
    void test16_ConcurrentSettingsCannotDisableBothLinkMethods() throws Exception {
        ApiClient first = fixtureAdmin();
        ApiClient second = fixtureAdmin();
        String app = "ALLOW_APP_PASSWORD_LINK", oauth = "ALLOW_OAUTH2_LINK";
        Map<String, String> original = settingsSnapshot(first, app, oauth);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try {
            first.adminUpdateSettings(Map.of(app, "true", oauth, "true"));
            var a = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { first.adminUpdateSettings(Map.of(app, "false")); return 200; }
                catch (ApiException e) { assertEquals("VALIDATION_ERROR", e.getCode()); return e.getStatusCode(); }
            });
            var b = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { second.adminUpdateSettings(Map.of(oauth, "false")); return 200; }
                catch (ApiException e) { assertEquals("VALIDATION_ERROR", e.getCode()); return e.getStatusCode(); }
            });
            assertEquals(Set.of(200, 400), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
            Map<String, String> actual = settingsSnapshot(first, app, oauth);
            assertNotEquals(actual.get(app), actual.get(oauth));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            first.adminUpdateSettings(original);
        }
    }

    private static void requireUiFixture(Path output) throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "Automated Swing E2E requires a display; API-only coverage is not UI acceptance");
        // Gradle passes a dedicated config dir to tests. Do not construct UI
        // if the singleton was initialized against actual user preferences.
        String override = System.getProperty("mailclient.config.dir");
        assertTrue(override != null && !override.isBlank(), "UI E2E requires isolated client config");
        Path build = Path.of("build").toFile().getCanonicalFile().toPath();
        Path config = Path.of(override).toFile().getCanonicalFile().toPath();
        assertTrue(config.startsWith(build), "UI config must be under Gradle build output");
        ClientConfig clientConfig = ClientConfig.getInstance();
        Path actualConfig = ((File) field(clientConfig, "configFile")).getCanonicalFile().toPath();
        assertEquals(config.resolve("config.properties").toFile().getCanonicalFile().toPath(), actualConfig);
        assertFalse(clientConfig.isInsecureMode(), "UI E2E must use HTTPS certificate validation");
        clientConfig.setAutoCleanEnabled(false);
        clientConfig.setDownloadDir(output.toAbsolutePath().toString());
        SessionStore.getInstance().clearSession();
    }

    private static ApiClient fixtureAdmin() throws Exception {
        ApiClient admin = new ApiClient(SERVER_URL, validCer);
        var response = admin.login("admin", changedAdminPass);
        assertEquals("ADMIN", response.user().role());
        assertFalse(response.user().mustChangePassword());
        return admin;
    }

    private static Map<String, String> settingsSnapshot(ApiClient admin, String... keys) throws Exception {
        Map<String, String> original = new LinkedHashMap<>();
        var settings = admin.adminGetSettings();
        for (String key : keys) {
            var item = settings.stream().filter(s -> key.equals(s.key())).findFirst().orElseThrow();
            original.put(key, item.value());
        }
        return original;
    }

    private static String uniqueUiUser(String kind) {
        return "ui_" + kind + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static LoginFrame showLogin(ApiClient client) throws Exception {
        return edt(() -> {
            LoginFrame login = new LoginFrame(client);
            login.setVisible(true);
            return login;
        });
    }

    private static MainFrame loginThroughUi(UiWindows windows, LoginFrame login, String username, String password) throws Exception {
        edt(() -> {
            text(login, "usernameField").setText(username);
            text(login, "passwordField").setText(password);
            button(login, "loginButton").doClick();
            return null;
        });
        MainFrame main = windows.awaitWindow(MainFrame.class);
        awaitUi("login frame closed", () -> !login.isDisplayable());
        return main;
    }

    private static void completeAppPasswordWizard(UiWindows windows, GmailManagerDialog manager,
                                                  String mailbox, String appPassword, boolean updating) throws Exception {
        FutureTask<Void> wizardClick = clickModal(buttonOnEdt(manager, "appPassBtn"));
        JDialog wizard = windows.awaitDialog(manager, "Liên kết Gmail bằng App Password");
        edt(() -> {
            visibleButton(wizard, "Tiếp tục (Bước 2) →").doClick();
            List<JTextField> fields = components(wizard, JTextField.class).stream()
                    .filter(Component::isShowing).filter(f -> !(f instanceof JPasswordField)).toList();
            assertEquals(2, fields.size());
            fields.get(0).setText(mailbox);
            fields.get(1).setText("Swing Mailbox Fixture");
            JPasswordField pass = components(wizard, JPasswordField.class).stream().filter(Component::isShowing)
                    .findFirst().orElseThrow();
            pass.setText(appPassword);
            visibleButton(wizard, updating ? "Cập nhật App Password" : "Kiểm tra IMAP & Liên kết →").doClick();
            return null;
        });
        awaitUi("IMAP wizard completed", () -> components(wizard, JButton.class).stream()
                .anyMatch(b -> b.isShowing() && "Hoàn tất".equals(b.getText()) && b.isEnabled()));
        edt(() -> {
            String expected = updating ? "ĐÃ CẬP NHẬT PHƯƠNG THỨC" : "LIÊN KẾT THÀNH CÔNG";
            assertTrue(components(wizard, JTextArea.class).stream().anyMatch(a -> a.isShowing() && a.getText().contains(expected)));
            visibleButton(wizard, "Hoàn tất").doClick();
            return null;
        });
        finishEdt(wizardClick);
    }

    private static void searchThroughUi(MainFrame main, String q) throws Exception {
        edt(() -> { text(main, "searchField").setText(q); text(main, "searchField").postActionEvent(); return null; });
    }

    private static boolean readyPage(MainFrame main, int count) throws Exception {
        return (Boolean) field(main, "mailboxReady") && mailList(main).getModel().getSize() == count;
    }

    private static boolean detailReady(MainFrame main, String subject) throws Exception {
        if (!(field(main, "currentDetail") instanceof MailDetailResponse detail)
                || !(mailList(main).getSelectedValue() instanceof MailSummaryDto selected)) return false;
        return subject.equals(detail.subject()) && detail.uid() == selected.uid()
                && detail.uidValidity() == selected.uidValidity()
                && subject.equals(label(main, "detailSubjectLabel").getText())
                && button(main, "deleteBtn").isShowing();
    }

    private static JList<?> mailList(MainFrame main) throws Exception { return (JList<?>) field(main, "mailList"); }
    private static JList<?> folderList(MainFrame main) throws Exception { return (JList<?>) field(main, "folderList"); }
    private static MailSummaryDto uiMail(MainFrame main, int index) throws Exception {
        return (MailSummaryDto) mailList(main).getModel().getElementAt(index);
    }
    private static List<Long> uiMailUids(MainFrame main) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < mailList(main).getModel().getSize(); i++) ids.add(uiMail(main, i).uid());
        return ids;
    }
    private static MailFolderDto findTrash(MainFrame main) throws Exception {
        ListModel<?> folders = folderList(main).getModel();
        for (int i = 0; i < folders.getSize(); i++) {
            MailFolderDto folder = (MailFolderDto) folders.getElementAt(i);
            if ("TRASH".equalsIgnoreCase(folder.type())) return folder;
        }
        return null;
    }
    private static boolean userRow(AdminFrame frame, Long id, boolean enabled) throws Exception {
        JTable table = (JTable) field(frame, "userTable");
        return table.getRowCount() == 1 && table.getValueAt(0, 1) instanceof AdminUserSummary row
                && id.equals(row.id()) && enabled == row.enabled();
    }

    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }
    private static JTextField text(Object owner, String name) throws Exception { return (JTextField) field(owner, name); }
    private static JLabel label(Object owner, String name) throws Exception { return (JLabel) field(owner, name); }
    private static JButton button(Object owner, String name) throws Exception { return (JButton) field(owner, name); }
    private static JButton buttonOnEdt(Object owner, String name) throws Exception { return edt(() -> button(owner, name)); }

    private static <T extends Component> List<T> components(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) result.addAll(components(child, type));
        }
        return result;
    }
    private static JButton visibleButton(Component root, String text) {
        return components(root, JButton.class).stream().filter(Component::isShowing)
                .filter(b -> text.equals(b.getText())).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text));
    }
    private static JButton tooltipButton(Component root, String text) {
        return components(root, JButton.class).stream().filter(Component::isShowing)
                .filter(b -> text.equals(b.getToolTipText())).findFirst().orElseThrow(() -> new AssertionError("Missing toolbar button: " + text));
    }
    private static boolean hasLabel(Component root, String exact) {
        return components(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && exact.equals(l.getText()));
    }

    private static <T> T edt(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        SwingUtilities.invokeLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private static FutureTask<Void> clickModal(JButton button) {
        // Modal setVisible/confirm can enter a nested event loop. Never block
        // the test waiting for that click until its owned dialog is handled.
        FutureTask<Void> task = new FutureTask<>(() -> { button.doClick(); return null; });
        SwingUtilities.invokeLater(task);
        return task;
    }
    private static void finishEdt(FutureTask<Void> task) throws Exception { task.get(10, TimeUnit.SECONDS); }
    private static void awaitUi(String description, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (edt(condition)) return;
            Thread.sleep(50);
        }
        fail("UI E2E timed out: " + description);
    }
    private static void acknowledge(UiWindows windows, Window owner, String title, String expectedMessage, boolean yes)
            throws Exception {
        JDialog dialog = windows.awaitDialog(owner, title);
        edt(() -> {
            JOptionPane pane = components(dialog, JOptionPane.class).stream().findFirst().orElseThrow();
            assertTrue(String.valueOf(pane.getMessage()).contains(expectedMessage), "Unexpected fixture dialog: " + title);
            String optionText = UIManager.getString(yes ? "OptionPane.yesButtonText" : "OptionPane.okButtonText", dialog.getLocale());
            visibleButton(dialog, optionText).doClick();
            return null;
        });
    }

    private static final class UiWindows implements AutoCloseable {
        private final Set<Window> before;
        private final ApiClient client;
        UiWindows(ApiClient client) throws Exception {
            this.client = client;
            before = edt(() -> {
                Set<Window> existing = Collections.newSetFromMap(new IdentityHashMap<>());
                Collections.addAll(existing, Window.getWindows());
                return existing;
            });
        }
        private boolean owns(Window w) throws Exception {
            if (w == null || before.contains(w)) return false;
            // Main/Register/Admin frames created by real application navigation
            // are independent JFrames. Only these fixture roots (and their
            // owned dialogs) are eligible for observation/cleanup.
            if (w instanceof LoginFrame || w instanceof RegisterFrame || w instanceof MainFrame || w instanceof AdminFrame) {
                return field(w, "apiClient") == client;
            }
            return owns(w.getOwner());
        }
        <T extends Window> T awaitWindow(Class<T> type) throws Exception {
            java.util.concurrent.atomic.AtomicReference<T> found = new java.util.concurrent.atomic.AtomicReference<>();
            awaitUi("show " + type.getSimpleName(), () -> {
                for (Window w : Window.getWindows()) if (owns(w) && type.isInstance(w) && w.isShowing()) {
                    found.set(type.cast(w)); return true;
                }
                return false;
            });
            return found.get();
        }
        JDialog awaitDialog(Window owner, String titlePrefix) throws Exception {
            java.util.concurrent.atomic.AtomicReference<JDialog> found = new java.util.concurrent.atomic.AtomicReference<>();
            awaitUi("owned dialog " + titlePrefix, () -> {
                for (Window w : Window.getWindows()) if (owns(w) && w instanceof JDialog d
                        && d.isShowing() && d.getOwner() == owner && d.getTitle().startsWith(titlePrefix)) {
                    found.set(d); return true;
                }
                return false;
            });
            return found.get();
        }
        @Override public void close() throws Exception {
            edt(() -> {
                Window[] windows = Window.getWindows();
                for (int i = windows.length - 1; i >= 0; i--) {
                    Window w = windows[i];
                    if (owns(w) && w.isDisplayable()) w.dispose();
                }
                return null;
            });
        }
    }
}
