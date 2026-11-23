package com.mailapp.client;

import com.mailapp.client.model.LoginResponse;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.RegisterRequest;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.util.SessionStore;
import org.junit.jupiter.api.*;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử CHẠY THẬT với Server đang chạy (Task 1 & Task 6 - Live Server Verification)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
        named = "MAILAPP_E2E",
        matches = "1",
        disabledReason = "Bỏ qua Live Server E2E do biến môi trường MAILAPP_E2E!=1; chỉ kích hoạt khi kiểm thử tích hợp trực tiếp với Server đang chạy"
)
class ClientLiveServerE2ETest {

    private static final String SERVER_URL = "https://localhost:8443";
    private static File validCer;
    private static File invalidCer;
    private static String registeredUsername;
    private static String registeredPassword;

    @BeforeAll
    static void initCertFiles() throws Exception {
        validCer = new File("certs/mailapp.cer");
        if (!validCer.exists()) {
            validCer = new File("../certs/mailapp.cer");
        }
        if (!validCer.exists()) {
            validCer = new File("../Mail Server/src/main/resources/mailapp.cer");
        }

        // Tạo một file cert lạ/giả mạo để kiểm thử từ chối kết nối
        Path tempDir = Files.createTempDirectory("mailclient-test-certs");
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
        ).start();
        p.waitFor();

        Process p2 = new ProcessBuilder(
                keytoolBin, "-exportcert", "-alias", "fake", "-keystore", fakeP12.getAbsolutePath(),
                "-storepass", tempStorePass, "-file", invalidCer.getAbsolutePath(), "-noprompt"
        ).start();
        p2.waitFor();

        long ts = System.currentTimeMillis() % 100000;
        registeredUsername = "liveuser_" + ts;
        registeredPassword = "LiveUserPass@" + ts;
    }

    @Test
    @Order(1)
    @DisplayName("Task 1: Chạy thật với Server - Có .cer đúng thì kết nối THÀNH CÔNG (200 OK)")
    void test1_ValidCertConnectionSucceeds() throws Exception {
        assertTrue(validCer.exists(), "File chứng chỉ mailapp.cer phải tồn tại!");
        ApiClient client = new ApiClient(SERVER_URL, validCer);

        PublicConfigDto config = client.getPublicConfig();
        assertNotNull(config);
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

        // Lần 1 đến 5: Sai mật khẩu -> Bắt buộc trả về HTTP 401 INVALID_CREDENTIALS
        for (int i = 1; i <= 5; i++) {
            try {
                client.login(victimUser, "WrongPassword@" + i);
                fail("Lần " + i + " phải thất bại với 401 INVALID_CREDENTIALS");
            } catch (ApiException ex) {
                assertEquals(401, ex.getStatusCode(), "Lần " + i + " phải trả HTTP 401");
                assertEquals("INVALID_CREDENTIALS", ex.getCode(), "Lần " + i + " mã lỗi phải là INVALID_CREDENTIALS");
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

        String initialAdminPass = System.getenv("ADMIN_INITIAL_PASSWORD");
        if (initialAdminPass == null || initialAdminPass.isBlank()) {
            initialAdminPass = "AdminInitialPass123!";
        }

        // 1. Đăng nhập ADMIN lần đầu với mật khẩu khởi tạo
        LoginResponse loginRes;
        try {
            loginRes = client.login("admin", initialAdminPass);
        } catch (ApiException ex) {
            // Nếu đã đổi mật khẩu ở lần chạy trước, thử với mật khẩu mới đã đổi
            initialAdminPass = "AdminNewSecurePass2026!";
            loginRes = client.login("admin", initialAdminPass);
        }

        assertNotNull(loginRes);
        boolean mustChange = loginRes.user().mustChangePassword();
        System.out.println("[THỰC THI THẬT] Đăng nhập ADMIN thành công. mustChangePassword = " + mustChange);

        if (mustChange) {
            // 2. Thử gọi API nghiệp vụ (GET /api/gmail-accounts) khi chưa đổi mật khẩu -> BẮT BUỘC bị chặn 403 PASSWORD_CHANGE_REQUIRED
            ApiException blockedEx = assertThrows(ApiException.class, client::getGmailAccounts,
                    "Khi mustChangePassword=true, gọi API nghiệp vụ phải bị chặn với 403 FORBIDDEN!");
            assertEquals(403, blockedEx.getStatusCode(), "Phải trả về HTTP 403");
            assertEquals("PASSWORD_CHANGE_REQUIRED", blockedEx.getCode(), "Mã lỗi phải là PASSWORD_CHANGE_REQUIRED");
            System.out.println("[THỰC THI THẬT] Bị chặn 403 PASSWORD_CHANGE_REQUIRED chính xác khi gọi API nghiệp vụ!");

            // 3. Thực hiện đổi mật khẩu sang mật khẩu mới
            String newPass = "AdminNewSecurePass2026!";
            var changeRes = client.changePassword(initialAdminPass, newPass);
            assertNotNull(changeRes);
            assertNotNull(changeRes.accessToken());
            assertFalse(changeRes.user().mustChangePassword(), "Sau khi đổi mật khẩu, mustChangePassword phải là false!");
            System.out.println("[THỰC THI THẬT] Đổi mật khẩu ADMIN thành công! Token mới đã được cấp.");

            // 4. Mở khóa: Gọi lại API nghiệp vụ -> Thành công 200 OK
            var accounts = client.getGmailAccounts();
            assertNotNull(accounts, "Sau khi đổi mật khẩu, API nghiệp vụ được mở khóa thành công!");
            System.out.println("[THỰC THI THẬT] Gọi lại API nghiệp vụ (GET /api/gmail-accounts) thành công sau khi mở khóa!");
        } else {
            System.out.println("[THỰC THI THẬT] Tài khoản ADMIN trong DB đã đổi mật khẩu ở phiên trước (mustChangePassword=false).");
            String nextPass = initialAdminPass.equals("AdminInitialPass123!") ? "AdminNewSecurePass2026!" : "AdminInitialPass123!";
            var changeRes = client.changePassword(initialAdminPass, nextPass);
            assertNotNull(changeRes);
            assertNotNull(changeRes.accessToken());
            assertFalse(changeRes.user().mustChangePassword());
            System.out.println("[THỰC THI THẬT] Đổi mật khẩu ADMIN thành công!");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Task 6.5: Lấy URL ủy quyền Google OAuth2 (GET /api/oauth2/authorize-url)")
    void test7_GetOAuth2AuthorizeUrl() throws Exception {
        ApiClient client = new ApiClient(SERVER_URL, validCer);
        client.login(registeredUsername, registeredPassword);

        try {
            String authUrl = client.getOAuth2AuthorizeUrl();
            assertNotNull(authUrl);
            assertTrue(authUrl.startsWith("https://accounts.google.com/o/oauth2"),
                    "URL ủy quyền phải trỏ tới Google Accounts OAuth2! Thực tế: " + authUrl);
            System.out.println("[THỰC THI THẬT] Nhận URL ủy quyền Google thành công: " + authUrl.substring(0, Math.min(authUrl.length(), 60)) + "...");
        } catch (ApiException ex) {
            if ("GOOGLE_CONFIG_MISSING".equalsIgnoreCase(ex.getCode()) || ex.getStatusCode() == 503) {
                System.out.println("[THỰC THI THẬT] Server phản hồi 503 GOOGLE_CONFIG_MISSING (do container Docker chưa nạp biến GOOGLE_CLIENT_ID/SECRET từ .env).");
                System.out.println("  -> BƯỚC NÀY CẦN TAY NGƯỜI DÙNG: CHƯA KIỂM ĐƯỢC (cần truyền GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET và mở trình duyệt đăng nhập Google thật).");
            } else {
                throw ex;
            }
        }
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
        try {
            adminClient.login("admin", "AdminNewSecurePass2026!");
        } catch (ApiException e) {
            adminClient.login("admin", "AdminInitialPass123!");
        }

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
}
