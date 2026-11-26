package com.mailapp.client;

import com.mailapp.client.model.AdminClientDtos.*;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.ui.admin.AdminViewModel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Module Admin phía Client (AdminViewModel & ApiClient EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2)")
class AdminModuleClientTest {

    private HttpServer server;
    private ApiClient apiClient;

    @Test
    void exportNeverOverwritesExistingFileOrPublishesAfterGuardExpires(@TempDir Path dir) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean current = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/admin/logs/export", ex -> {
            calls.incrementAndGet();
            current.set(false); // response arrives after the export window became stale
            byte[] csv = "ID,Action\r\n".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            ex.sendResponseHeaders(200, csv.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(csv); }
        });
        Path existing = Files.writeString(dir.resolve("existing.csv"), "keep");
        assertThrows(IOException.class, () -> apiClient.adminExportLogsCsvToFile(null, null, null, null, null, existing.toFile()));
        assertEquals("keep", Files.readString(existing));
        assertEquals(0, calls.get());
        Path target = dir.resolve("new.csv");
        assertThrows(InterruptedException.class, () -> apiClient.adminExportLogsCsvToFile(null, null, null, null, null, target.toFile(), current::get));
        assertEquals(1, calls.get());
        assertFalse(Files.exists(target));
        try (var files = Files.list(dir)) { assertEquals(1, files.count()); }
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        apiClient = new ApiClient("http://localhost:" + server.getAddress().getPort());
        apiClient.setAccessToken("admin-jwt");
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void respondJson(com.sun.net.httpserver.HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    @Test
    @DisplayName("AdminViewModel: Validate cấu hình (chặn tắt cả OAuth2 & AppPass, kiểm tra khoảng số)")
    void testAdminViewModel_SettingsValidation() {
        String bothOffErr = AdminViewModel.validateSettingsUpdate(Map.of(
                "ALLOW_OAUTH2_LINK", "false",
                "ALLOW_APP_PASSWORD_LINK", "false"
        ));
        assertNotNull(bothOffErr);
        assertTrue(bothOffErr.contains("OAuth2"));

        assertNotNull(AdminViewModel.validateSettingsUpdate(Map.of("MAX_ATTACHMENT_SIZE_MB", "100")));
        assertNotNull(AdminViewModel.validateSettingsUpdate(Map.of("MAIL_PAGE_SIZE", "5")));
        assertNotNull(AdminViewModel.validateSettingsUpdate(Map.of("LOGIN_MAX_FAILED_ATTEMPTS", "2")));
        assertNotNull(AdminViewModel.validateSettingsUpdate(Map.of("MAX_GMAIL_PER_USER", "0")));
        assertNull(AdminViewModel.validateSettingsUpdate(Map.of("MAX_GMAIL_PER_USER", "0"), true));

        assertNull(AdminViewModel.validateSettingsUpdate(Map.of(
                "ALLOW_OAUTH2_LINK", "true",
                "ALLOW_APP_PASSWORD_LINK", "false",
                "MAX_GMAIL_PER_USER", "10",
                "MAX_ATTACHMENT_SIZE_MB", "25",
                "MAIL_PAGE_SIZE", "30",
                "LOGIN_MAX_FAILED_ATTEMPTS", "5"
        )));
    }

    @Test
    @DisplayName("AdminViewModel: Kiểm tra tự thao tác, xác nhận gõ username xóa, cảnh báo bảo trì & nhãn biểu đồ")
    void testAdminViewModel_GuardsAndChartHelpers() {
        assertTrue(AdminViewModel.isSelfAction(1L, 1L));
        assertFalse(AdminViewModel.isSelfAction(1L, 2L));

        assertTrue(AdminViewModel.matchesDeleteConfirmation("student01", "student01"));
        assertFalse(AdminViewModel.matchesDeleteConfirmation("student01", "student02"));
        assertFalse(AdminViewModel.matchesDeleteConfirmation("student01", ""));

        assertTrue(AdminViewModel.isMaintenanceTurningOn(false, true));
        assertFalse(AdminViewModel.isMaintenanceTurningOn(true, true));
        assertFalse(AdminViewModel.isMaintenanceTurningOn(true, false));

        assertEquals("22/11", AdminViewModel.formatShortDayLabel("2026-11-22"));
        assertEquals(15L, AdminViewModel.computeMaxChartValue(List.of(
                new DailyCountPoint("2026-11-21", 4),
                new DailyCountPoint("2026-11-22", 15)
        )));
    }

    @Test
    @DisplayName("ApiClient Admin: Danh sách, tạo, chi tiết, khóa/mở khóa, đổi role, reset-password, xóa")
    void testApiClientAdminUserEndpoints() throws Exception {
        server.createContext("/api/admin/users/5/lock", ex -> respondJson(ex, 200,
                "{\"id\":5,\"username\":\"u5\",\"role\":\"USER\",\"enabled\":false,\"mustChangePassword\":false,\"gmailCount\":0}"));
        server.createContext("/api/admin/users/5/unlock", ex -> respondJson(ex, 200,
                "{\"id\":5,\"username\":\"u5\",\"role\":\"USER\",\"enabled\":true,\"mustChangePassword\":false,\"gmailCount\":0}"));
        server.createContext("/api/admin/users/5/role", ex -> respondJson(ex, 200,
                "{\"id\":5,\"username\":\"u5\",\"role\":\"ADMIN\",\"enabled\":true,\"mustChangePassword\":false,\"gmailCount\":0}"));
        server.createContext("/api/admin/users/5/reset-password", ex -> respondJson(ex, 200,
                "{\"userId\":5,\"username\":\"u5\",\"temporaryPassword\":\"Tmp@2026Pass!1\",\"mustChangePassword\":true,\"message\":\"OK\"}"));
        server.createContext("/api/admin/users/5", ex -> {
            if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            respondJson(ex, 200, """
                    {"id":5,"username":"u5","fullName":"U5","email":"u5@ptit.edu.vn","role":"USER","enabled":true,
                     "mustChangePassword":false,"gmailCount":1,
                     "gmailAccounts":[{"id":10,"email":"u5@gmail.com","authType":"OAUTH2","status":"ACTIVE"}]}
                    """);
        });
        server.createContext("/api/admin/users", ex -> {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respondJson(ex, 201,
                        "{\"id\":6,\"username\":\"u6\",\"role\":\"USER\",\"enabled\":true,\"mustChangePassword\":true,\"gmailCount\":0,\"temporaryPassword\":\"GenPass#123456\"}");
                return;
            }
            respondJson(ex, 200,
                    "{\"content\":[{\"id\":5,\"username\":\"u5\",\"role\":\"USER\",\"enabled\":true,\"mustChangePassword\":false,\"gmailCount\":1}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}");
        });

        AdminPageResponse<AdminUserSummary> page = apiClient.adminListUsers("u5", "USER", true, 0, 20, "createdAt", "desc");
        assertEquals(1, page.totalElements());
        AdminCreateUserResponse created = apiClient.adminCreateUser(new AdminCreateUserRequest("u6", "U6", "u6@ptit.edu.vn", "USER", null));
        assertTrue(created.mustChangePassword());
        assertNotNull(created.temporaryPassword());
        AdminUserDetail detail = apiClient.adminGetUserDetail(5L);
        assertEquals(1, detail.gmailAccounts().size());
        assertFalse(apiClient.adminLockUser(5L).enabled());
        assertTrue(apiClient.adminUnlockUser(5L).enabled());
        assertEquals("ADMIN", apiClient.adminUpdateUserRole(5L, "ADMIN").role());
        assertEquals("Tmp@2026Pass!1", apiClient.adminResetPassword(5L).temporaryPassword());
        assertDoesNotThrow(() -> apiClient.adminDeleteUser(5L));
    }

    @Test
    @DisplayName("ApiClient Admin: Settings, Stats, Logs, Xuất CSV và xử lý 403/401/409 LAST_ADMIN")
    void testApiClientSettingsStatsLogsAndErrors(@TempDir Path tempDir) throws Exception {
        server.createContext("/api/admin/settings", ex -> respondJson(ex, 200,
                "[{\"key\":\"ALLOW_REGISTRATION\",\"value\":\"false\",\"type\":\"BOOLEAN\",\"updatedByUsername\":\"admin\"}]"));
        server.createContext("/api/admin/stats", ex -> respondJson(ex, 200,
                "{\"totalUsers\":10,\"activeUsers\":9,\"lockedUsers\":1,\"totalAdmins\":2,\"totalGmailAccounts\":5,\"gmailByOAuth2\":3,\"gmailByAppPassword\":2,\"gmailNeedsReauth\":0,\"registrationsToday\":1,\"registrationsLast7Days\":4,\"registrationsLast30Days\":10,\"failedLoginsLast24h\":2,\"mailsSentLast7DaysByDay\":[{\"date\":\"2026-11-22\",\"count\":4}]}"));
        server.createContext("/api/admin/logs/export", ex -> {
            byte[] csv = "\uFEFFID,Action\r\n\"1\",\"ADMIN_LOCK_USER\"\r\n".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            ex.sendResponseHeaders(200, csv.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(csv); }
        });
        server.createContext("/api/admin/logs", ex -> respondJson(ex, 200,
                "{\"content\":[{\"id\":1,\"username\":\"admin\",\"action\":\"ADMIN_LOCK_USER\",\"detail\":\"Locked\",\"ipAddress\":\"127.0.0.1\"}],\"page\":0,\"size\":25,\"totalElements\":1,\"totalPages\":1}"));
        server.createContext("/api/admin/users/99/lock", ex -> respondJson(ex, 409,
                "{\"code\":\"LAST_ADMIN\",\"errorCode\":\"LAST_ADMIN\",\"message\":\"Không thể khóa Admin cuối cùng\"}"));
        server.createContext("/api/admin/users/98/lock", ex -> respondJson(ex, 403,
                "{\"code\":\"FORBIDDEN\",\"errorCode\":\"FORBIDDEN\",\"message\":\"Không còn quyền ADMIN\"}"));
        server.createContext("/api/admin/users/97/lock", ex -> respondJson(ex, 401,
                "{\"code\":\"UNAUTHORIZED\",\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Hết phiên\"}"));

        assertEquals("false", apiClient.adminGetSettings().get(0).value());
        assertEquals("false", apiClient.adminUpdateSettings(Map.of("ALLOW_REGISTRATION", "false")).get(0).value());
        assertEquals(10L, apiClient.adminGetStats().totalUsers());
        assertEquals(1L, apiClient.adminSearchLogs(null, "admin", "ADMIN_LOCK_USER", null, null, 0, 25).totalElements());

        File csvFile = tempDir.resolve("logs.csv").toFile();
        long bytes = apiClient.adminExportLogsCsvToFile(null, null, null, null, null, csvFile);
        assertTrue(bytes > 0);
        assertTrue(Files.readString(csvFile.toPath()).contains("ADMIN_LOCK_USER"));

        ApiException e409 = assertThrows(ApiException.class, () -> apiClient.adminLockUser(99L));
        assertEquals(409, e409.getStatusCode());
        assertEquals("LAST_ADMIN", e409.getCode());

        ApiException e403 = assertThrows(ApiException.class, () -> apiClient.adminLockUser(98L));
        assertEquals(403, e403.getStatusCode());
        assertEquals("FORBIDDEN", e403.getCode());

        ApiException e401 = assertThrows(ApiException.class, () -> apiClient.adminLockUser(97L));
        assertEquals(401, e401.getStatusCode());
    }
}

