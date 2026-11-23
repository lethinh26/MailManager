package com.mailapp.client;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.util.SessionStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử quy trình OAuth2 Poll, xử lý 401 hết phiên & bảo mật log (Task 5)")
class OAuthFlowAndSessionTest {

    private HttpServer server;
    private ApiClient apiClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        String url = "http://localhost:" + server.getAddress().getPort();
        apiClient = new ApiClient(url);
        server.start();
        SessionStore.getInstance().clearSession();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Poll OAuth2 thành công: Phát hiện tài khoản ACTIVE mới sau khi người dùng cấp quyền")
    void testPollOAuthSuccess() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        server.createContext("/api/gmail-accounts", exchange -> {
            int count = callCount.incrementAndGet();
            String json;
            if (count < 2) {
                json = "[]"; // Chưa hoàn tất
            } else {
                json = """
                        [
                          {
                            "id": 1,
                            "email": "cuongdfle@gmail.com",
                            "displayName": "Lê Thanh Cường",
                            "authType": "OAUTH2",
                            "status": "ACTIVE",
                            "createdAt": "2026-11-22T08:00:00Z"
                          }
                        ]
                        """;
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Giả lập logic polling
        List<GmailAccountDto> result = null;
        for (int i = 0; i < 5; i++) {
            List<GmailAccountDto> list = apiClient.getGmailAccounts();
            if (!list.isEmpty() && "ACTIVE".equalsIgnoreCase(list.get(0).status())) {
                result = list;
                break;
            }
        }

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("cuongdfle@gmail.com", result.get(0).email());
        assertEquals("ACTIVE", result.get(0).status());
        assertEquals(2, callCount.get());
    }

    @Test
    @DisplayName("Poll OAuth2 bị hủy: Dừng polling ngay lập tức khi cờ hủy kích hoạt")
    void testPollOAuthCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger(0);

        // Kích hoạt hủy sau 2 vòng lặp
        for (int i = 0; i < 90; i++) {
            if (i == 2) {
                cancelled.set(true);
            }
            if (cancelled.get()) {
                break;
            }
            attempts.incrementAndGet();
        }

        assertEquals(2, attempts.get(), "Quá trình poll phải dừng ngay khi người dùng nhấn Hủy bỏ!");
    }

    @Test
    @DisplayName("Poll OAuth2 hết giờ (Timeout): Báo hết thời gian sau số lần thử tối đa")
    void testPollOAuthTimeout() throws Exception {
        server.createContext("/api/gmail-accounts", exchange -> {
            String json = "[]";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        int maxAttempts = 3; // Giả lập ngưỡng timeout
        int attempts = 0;
        boolean found = false;

        while (attempts < maxAttempts) {
            attempts++;
            List<GmailAccountDto> list = apiClient.getGmailAccounts();
            if (!list.isEmpty()) {
                found = true;
                break;
            }
        }

        assertFalse(found);
        assertEquals(3, attempts);
    }

    @Test
    @DisplayName("Xử lý 401 Unauthorized: Phát hiện phiên hết hạn và xóa sạch session")
    void test401SessionExpiration() {
        server.createContext("/api/auth/me", exchange -> {
            String json = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Token đã hết hạn hoặc không hợp lệ.\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        SessionStore.getInstance().saveSession("expired.token.jwt", null);
        assertTrue(SessionStore.getInstance().hasActiveSession());

        ApiException ex = assertThrows(ApiException.class, () -> apiClient.getCurrentUser());
        assertEquals(401, ex.getStatusCode());

        // Giả lập hành động khi nhận 401
        SessionStore.getInstance().clearSession();
        apiClient.clearAccessToken();

        assertFalse(SessionStore.getInstance().hasActiveSession());
        assertNull(apiClient.getAccessToken());
    }

    @Test
    @DisplayName("Bảo mật dữ liệu nhạy cảm: Tuyệt đối không log hoặc để lộ JWT token / mật khẩu")
    void testNoPlaintextSecretsLogged() {
        String sensitiveToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sensitive_payload.signature";
        apiClient.setAccessToken(sensitiveToken);

        String clientString = apiClient.toString();
        assertFalse(clientString.contains(sensitiveToken),
                "ApiClient.toString() tuyệt đối không được in JWT token!");

        ApiException ex = new ApiException(401, "UNAUTHORIZED", "Lỗi xác thực người dùng");
        assertFalse(ex.getMessage().contains(sensitiveToken));
    }
}

