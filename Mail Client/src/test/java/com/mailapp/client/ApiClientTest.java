package com.mailapp.client;

import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.RegisterRequest;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử ApiClient & xử lý mã lỗi thống nhất (ApiClientTest)")
class ApiClientTest {

    private HttpServer server;
    private String serverUrl;
    private ApiClient apiClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serverUrl = "http://localhost:" + server.getAddress().getPort();
        apiClient = new ApiClient(serverUrl);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Xử lý lỗi: Đọc mã lỗi từ trường 'code' chuẩn Spring Boot")
    void testErrorCodeParsing_FromCodeField() {
        server.createContext("/api/auth/register", exchange -> {
            String json = """
                    {
                      "code": "USERNAME_TAKEN",
                      "message": "Tên đăng nhập đã tồn tại trong hệ thống."
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        RegisterRequest request = new RegisterRequest("cuongdfle", "cuong@ptit.edu.vn", "Pass123456", "Cuong Le", "USER");
        ApiException ex = assertThrows(ApiException.class, () -> apiClient.register(request));

        assertEquals(409, ex.getStatusCode());
        assertEquals("USERNAME_TAKEN", ex.getCode());
        assertEquals("Tên đăng nhập đã tồn tại trong hệ thống.", ex.getMessage());
    }

    @Test
    @DisplayName("Xử lý lỗi: Đọc mã lỗi từ trường phụ 'errorCode'")
    void testErrorCodeParsing_FromErrorCodeField() {
        server.createContext("/api/auth/change-password", exchange -> {
            String json = """
                    {
                      "errorCode": "PASSWORD_MISMATCH",
                      "message": "Mật khẩu hiện tại không chính xác."
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ApiException ex = assertThrows(ApiException.class, () -> apiClient.changePassword("wrongPass", "NewPass123"));

        assertEquals(400, ex.getStatusCode());
        assertEquals("PASSWORD_MISMATCH", ex.getCode());
        assertEquals("Mật khẩu hiện tại không chính xác.", ex.getMessage());
    }

    @Test
    @DisplayName("Rate Limit (429): Đọc giá trị header Retry-After chính xác")
    void testRateLimit_WithRetryAfterHeader() {
        server.createContext("/api/auth/login", exchange -> {
            String json = """
                    {
                      "code": "RATE_LIMIT_EXCEEDED",
                      "message": "Đăng nhập sai quá 5 lần. Vui lòng chờ."
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After", "45");
            exchange.sendResponseHeaders(429, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ApiException ex = assertThrows(ApiException.class, () -> apiClient.login("admin", "wrong"));

        assertEquals(429, ex.getStatusCode());
        assertEquals(45, ex.getRetryAfterSeconds());
        assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
    }

    @Test
    @DisplayName("Tự động đính kèm header 'Authorization: Bearer <token>' khi đã có accessToken")
    void testAuthorizationHeaderAttachment() throws Exception {
        AtomicReference<String> receivedAuthHeader = new AtomicReference<>();

        server.createContext("/api/auth/me", exchange -> {
            receivedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String json = """
                    {
                      "id": 1,
                      "username": "cuongdfle",
                      "fullName": "Lê Thanh Cường",
                      "email": "cuongdfle@gmail.com",
                      "role": "ADMIN",
                      "mustChangePassword": false,
                      "createdAt": "2026-11-22T08:00:00Z"
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        apiClient.setAccessToken("test.jwt.token.value");
        var user = apiClient.getCurrentUser();

        assertNotNull(user);
        assertEquals("cuongdfle", user.username());
        assertEquals("ADMIN", user.role());
        assertEquals("Bearer test.jwt.token.value", receivedAuthHeader.get());
    }

    @Test
    @DisplayName("Lấy cấu hình công khai GET /api/public/config thành công")
    void testGetPublicConfig_Success() throws Exception {
        server.createContext("/api/public/config", exchange -> {
            String json = """
                    {
                      "registrationEnabled": true,
                      "oauth2LinkEnabled": true,
                      "appPasswordLinkEnabled": true,
                      "maintenanceMode": false,
                      "sendMailEnabled": true,
                      "maxGmailPerUser": 5,
                      "maxAttachmentSizeMb": 25
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        PublicConfigDto config = apiClient.getPublicConfig();

        assertNotNull(config);
        assertTrue(config.registrationEnabled());
        assertFalse(config.maintenanceMode());
        assertTrue(config.oauth2LinkEnabled());
        assertEquals(5, config.maxGmailPerUser());
        assertEquals(25, config.maxAttachmentSizeMb());
    }
}
