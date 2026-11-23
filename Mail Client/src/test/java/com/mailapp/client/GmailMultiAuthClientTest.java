package com.mailapp.client;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.ui.dialogs.GmailManagerDialog;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Client xử lý đa phương thức Gmail:
 * - Pre-check email_key cục bộ trên Wizard App Password (dấu chấm, +tag, googlemail.com)
 * - Xử lý các outcome (CREATED, METHOD_ADDED, METHOD_UPDATED) thành thông điệp tiếng Việt thân thiện
 * - Gọi API mới: PUT /api/gmail-accounts/{id}/preferred-method và DELETE /api/gmail-accounts/{id}/methods/{method}
 */
class GmailMultiAuthClientTest {

    private HttpServer server;
    private ApiClient apiClient;
    private final AtomicReference<String> lastRequestMethod = new AtomicReference<>();
    private final AtomicReference<String> lastRequestPath = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/gmail-accounts", exchange -> {
            lastRequestMethod.set(exchange.getRequestMethod());
            lastRequestPath.set(exchange.getRequestURI().toString());
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            String resp;
            if ("PUT".equals(exchange.getRequestMethod())) {
                resp = """
                        {
                          "id": 10,
                          "email": "cuongdfle@gmail.com",
                          "emailKey": "cuongdfle@gmail.com",
                          "displayName": "Lê Cường",
                          "authType": "APP_PASSWORD",
                          "preferredMethod": "APP_PASSWORD",
                          "activeMethod": "APP_PASSWORD",
                          "fallbackActive": false,
                          "methods": { "hasOAuth": true, "hasAppPassword": true },
                          "status": "ACTIVE",
                          "message": "Đã đặt App Password làm phương thức đăng nhập chính."
                        }
                        """;
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                resp = """
                        {
                          "unlinked": false,
                          "removedMethod": "OAUTH2",
                          "message": "Đã gỡ phương thức Google OAuth2 khỏi hộp thư c***@gmail.com."
                        }
                        """;
            } else {
                resp = """
                        {
                          "id": 10,
                          "email": "cuongdfle@gmail.com",
                          "emailKey": "cuongdfle@gmail.com",
                          "displayName": "Lê Cường",
                          "authType": "OAUTH2",
                          "preferredMethod": "OAUTH2",
                          "activeMethod": "OAUTH2",
                          "fallbackActive": false,
                          "methods": { "hasOAuth": true, "hasAppPassword": true },
                          "status": "ACTIVE",
                          "outcome": "METHOD_ADDED",
                          "message": "Đã thêm phương thức App Password cho hộp thư c***@gmail.com (dùng làm dự phòng cùng Google OAuth2)."
                        }
                        """;
            }
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        apiClient = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
        apiClient.setAccessToken("test-jwt-token");
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    @DisplayName("Wizard App Password kiểm tra trước theo email_key và báo ngay trước khi gọi mạng")
    void precheckDetectsExistingGoogleAccountAcrossVariants() {
        GmailAccountDto existingOAuth = new GmailAccountDto(
                1L, "cuongdfle@gmail.com", "cuongdfle@gmail.com", "Lê Cường",
                "OAUTH2", "OAUTH2", "OAUTH2", false,
                new GmailAccountDto.MethodsInfo(true, false),
                "ACTIVE", null, null, null, null, null, null);

        // Nhập biến thể có dấu chấm + +tag + googlemail.com
        var res = GmailManagerDialog.evaluateAppPasswordPrecheck("  Cuong.DF.Le+backup@googlemail.com ", List.of(existingOAuth));
        assertTrue(res.matchedExisting());
        assertEquals("Hộp thư này đã được liên kết bằng Google. Bấm 'Thêm App Password' để dùng làm dự phòng", res.bannerMessage());
        assertEquals("Thêm App Password", res.buttonLabel());

        // Email hoàn toàn mới -> không hiện banner
        var fresh = GmailManagerDialog.evaluateAppPasswordPrecheck("other@gmail.com", List.of(existingOAuth));
        assertFalse(fresh.matchedExisting());
        assertNull(fresh.bannerMessage());
    }

    @Test
    @DisplayName("Xử lý các outcome CREATED / METHOD_ADDED / METHOD_UPDATED thành thông điệp tiếng Việt thân thiện")
    void formatsAllServerOutcomesFriendly() {
        GmailAccountDto created = new GmailAccountDto(
                1L, "a@gmail.com", "a@gmail.com", "A", "APP_PASSWORD", "APP_PASSWORD", "APP_PASSWORD", false,
                new GmailAccountDto.MethodsInfo(false, true), "ACTIVE", null, null, null, null,
                "CREATED", "Đã liên kết hộp thư a***@gmail.com bằng App Password thành công.");
        String msgCreated = GmailManagerDialog.formatLinkOutcomeMessage(created);
        assertTrue(msgCreated.contains("LIÊN KẾT THÀNH CÔNG"));
        assertFalse(msgCreated.toLowerCase().contains("lỗi"));

        GmailAccountDto added = new GmailAccountDto(
                1L, "a@gmail.com", "a@gmail.com", "A", "OAUTH2", "OAUTH2", "OAUTH2", false,
                new GmailAccountDto.MethodsInfo(true, true), "ACTIVE", null, null, null, null,
                "METHOD_ADDED", "Đã thêm phương thức App Password cho hộp thư a***@gmail.com.");
        String msgAdded = GmailManagerDialog.formatLinkOutcomeMessage(added);
        assertTrue(msgAdded.contains("ĐÃ THÊM PHƯƠNG THỨC ĐĂNG NHẬP"));
        assertTrue(msgAdded.contains("Đã thêm phương thức App Password"));
        assertFalse(msgAdded.toLowerCase().contains("lỗi"));

        GmailAccountDto updated = new GmailAccountDto(
                1L, "a@gmail.com", "a@gmail.com", "A", "OAUTH2", "OAUTH2", "OAUTH2", false,
                new GmailAccountDto.MethodsInfo(true, true), "ACTIVE", null, null, null, null,
                "METHOD_UPDATED", "Đã cập nhật App Password cho hộp thư a***@gmail.com.");
        String msgUpdated = GmailManagerDialog.formatLinkOutcomeMessage(updated);
        assertTrue(msgUpdated.contains("ĐÃ CẬP NHẬT PHƯƠNG THỨC ĐĂNG NHẬP"));
        assertFalse(msgUpdated.toLowerCase().contains("lỗi"));
    }

    @Test
    @DisplayName("ApiClient hỗ trợ link outcome, PUT /preferred-method và DELETE /methods/{method}")
    void apiClientCallsMultiAuthEndpointsCorrectly() throws Exception {
        GmailAccountDto linked = apiClient.linkGmailWithAppPassword("cuongdfle@gmail.com", "abcd efgh ijkl mnop", "Lê Cường");
        assertEquals("METHOD_ADDED", linked.outcome());
        assertTrue(linked.hasOAuth());
        assertTrue(linked.hasAppPassword());

        GmailAccountDto pref = apiClient.setPreferredGmailMethod(10L, "APP_PASSWORD");
        assertEquals("PUT", lastRequestMethod.get());
        assertEquals("/api/gmail-accounts/10/preferred-method", lastRequestPath.get());
        assertTrue(lastRequestBody.get().contains("APP_PASSWORD"));
        assertEquals("APP_PASSWORD", pref.effectivePreferredMethod());

        Map<String, Object> removed = apiClient.removeGmailMethod(10L, "OAUTH2", true);
        assertEquals("DELETE", lastRequestMethod.get());
        assertEquals("/api/gmail-accounts/10/methods/OAUTH2?confirmUnlink=true", lastRequestPath.get());
        assertEquals("OAUTH2", removed.get("removedMethod"));
    }
}

