package com.mailapp.client;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
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
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử ApiClient cho toàn bộ API Hộp thư thật & Gmail EP-G2/PATCH")
class MailApiClientNewEndpointsTest {

    private HttpServer server;
    private ApiClient apiClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        apiClient = new ApiClient("http://localhost:" + server.getAddress().getPort());
        apiClient.setAccessToken("test-jwt-token");
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
    @DisplayName("GET /api/mails/folders phân giải danh sách thư mục tiếng Việt và số chưa đọc")
    void testGetMailFolders() throws Exception {
        server.createContext("/api/mails/folders", ex -> respondJson(ex, 200, """
                [
                  {"name":"INBOX","imapName":"INBOX","displayName":"Hộp thư đến","type":"INBOX","unread":4,"total":25},
                  {"name":"SENT","imapName":"[Gmail]/Sent Mail","displayName":"Đã gửi","type":"SENT","unread":0,"total":10}
                ]
                """));

        List<MailFolderDto> folders = apiClient.getMailFolders(1L);
        assertEquals(2, folders.size());
        assertEquals("Hộp thư đến", folders.get(0).displayName());
        assertEquals(4, folders.get(0).unread());
    }

    @Test
    @DisplayName("GET /api/mails truyền đủ tham số tìm kiếm/lọc và đọc phân trang")
    void testGetMails_QueryParamsAndPagination() throws Exception {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        server.createContext("/api/mails", ex -> {
            capturedQuery.set(ex.getRequestURI().getQuery());
            respondJson(ex, 200, """
                    {
                      "items": [
                        {
                          "uid": 101,
                          "uidValidity": 55,
                          "from": {"name":"PTIT","address":"ptit@edu.vn"},
                          "to": [{"name":"Me","address":"me@gmail.com"}],
                          "subject": "Thông báo",
                          "snippet": "Nội dung ngắn",
                          "date": "2026-11-22T08:00:00Z",
                          "flags": {"seen":false,"flagged":true,"answered":false,"draft":false},
                          "size": 4096,
                          "hasAttachment": true,
                          "attachmentCount": 1
                        }
                      ],
                      "page": 0,
                      "size": 30,
                      "total": 45,
                      "hasMore": true,
                      "uidValidity": 55
                    }
                    """);
        });

        MailListResponse resp = apiClient.getMails(1L, "INBOX", 0, 30, "hóa đơn", true, true, "ptit", null);
        assertEquals(1, resp.items().size());
        assertTrue(resp.hasMore());
        assertEquals(55L, resp.uidValidity());
        assertTrue(capturedQuery.get().contains("unread=true"));
        assertTrue(capturedQuery.get().contains("hasAttachment=true"));
    }

    @Test
    @DisplayName("GET /api/mails/{g}/{f}/{uid} nhận 409 UID_VALIDITY_CHANGED và 409 GMAIL_NEEDS_REAUTH")
    void testConflictErrors_UidValidityAndNeedsReauth() {
        server.createContext("/api/mails/1/INBOX/99", ex -> respondJson(ex, 409, """
                {"code":"UID_VALIDITY_CHANGED","errorCode":"UID_VALIDITY_CHANGED","message":"Hộp thư đã thay đổi, cần đồng bộ lại."}
                """));
        server.createContext("/api/mails/2/INBOX/10", ex -> respondJson(ex, 409, """
                {"code":"GMAIL_NEEDS_REAUTH","errorCode":"GMAIL_NEEDS_REAUTH","message":"Tài khoản cần cấp lại quyền OAuth2."}
                """));

        ApiException ex1 = assertThrows(ApiException.class,
                () -> apiClient.getMailDetail(1L, "INBOX", 99L, false, 12L));
        assertEquals(409, ex1.getStatusCode());
        assertEquals("UID_VALIDITY_CHANGED", ex1.getCode());

        ApiException ex2 = assertThrows(ApiException.class,
                () -> apiClient.getMailDetail(2L, "INBOX", 10L, false, null));
        assertEquals(409, ex2.getStatusCode());
        assertEquals("GMAIL_NEEDS_REAUTH", ex2.getCode());
    }

    @Test
    @DisplayName("GET /attachments/{idx}/download stream tệp xuống ổ đĩa và tính đúng SHA-256")
    void testDownloadAttachmentToFile_Sha256(@TempDir Path tempDir) throws Exception {
        byte[] payload = "Nội dung tệp PDF mẫu tiếng Việt 1234567890".getBytes(StandardCharsets.UTF_8);
        server.createContext("/api/mails/1/INBOX/50/attachments/0/download", ex -> {
            ex.getResponseHeaders().set("Content-Type", "application/pdf");
            ex.sendResponseHeaders(200, payload.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(payload);
            }
        });

        File out = tempDir.resolve("doc.pdf").toFile();
        ApiClient.DownloadResult res = apiClient.downloadAttachmentToFile(1L, "INBOX", 50L, 0, 1L, out);
        assertEquals(payload.length, res.bytesWritten());
        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(payload);
        StringBuilder expectedHex = new StringBuilder();
        for (byte b : digest) expectedHex.append(String.format("%02x", b));
        assertEquals(expectedHex.toString(), res.sha256Hex());
    }

    @Test
    @DisplayName("POST /api/mails/send gửi multipart (JSON + tệp) và xử lý 429 Retry-After")
    void testSendMailMultipartAndRateLimit(@TempDir Path tempDir) throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/api/mails/send", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (capturedBody.get().contains("TRIGGER_429")) {
                ex.getResponseHeaders().set("Retry-After", "60");
                respondJson(ex, 429, """
                        {"code":"SEND_RATE_LIMIT_EXCEEDED","errorCode":"SEND_RATE_LIMIT_EXCEEDED","message":"Vượt giới hạn gửi thư."}
                        """);
                return;
            }
            respondJson(ex, 200, """
                    {"messageId":"<msg-1@test>","status":"SENT"}
                    """);
        });

        File att = tempDir.resolve("hello.txt").toFile();
        Files.writeString(att.toPath(), "FILE_CONTENT_OK");

        SendMailPayload payload = new SendMailPayload(1L, List.of("a@b.com"), List.of(), List.of(),
                "Test Subject", "Hello", null, null, null);
        SendMailResponse sent = apiClient.sendMailMultipart(payload, List.of(att));
        assertEquals("SENT", sent.status());
        assertTrue(capturedBody.get().contains("FILE_CONTENT_OK"));
        assertTrue(capturedBody.get().contains("Test Subject"));

        SendMailPayload limited = new SendMailPayload(1L, List.of("a@b.com"), List.of(), List.of(),
                "TRIGGER_429", "Hi", null, null, null);
        ApiException ex429 = assertThrows(ApiException.class, () -> apiClient.sendMailMultipart(limited, List.of()));
        assertEquals(429, ex429.getStatusCode());
        assertEquals(60, ex429.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("DELETE /api/mails, PUT /flags, EP-G2 App Password và PATCH displayName")
    void testDeleteFlagsAndGmailPatch() throws Exception {
        AtomicReference<String> deleteQuery = new AtomicReference<>();
        server.createContext("/api/mails/1/INBOX/77/flags", ex -> respondJson(ex, 200, """
                {"seen":true,"flagged":true,"answered":false,"draft":false}
                """));
        server.createContext("/api/mails/1/INBOX/77", ex -> {
            deleteQuery.set(ex.getRequestURI().getQuery());
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.createContext("/api/gmail-accounts/5", ex -> respondJson(ex, 200, """
                {"id":5,"email":"u@gmail.com","displayName":"Tên mới","authType":"APP_PASSWORD","status":"ACTIVE"}
                """));

        MailFlagsDto flags = apiClient.updateMailFlags(1L, "INBOX", 77L, true, true, 1L);
        assertTrue(flags.seen());
        assertTrue(flags.flagged());

        apiClient.deleteMail(1L, "INBOX", 77L, false, 1L);
        assertTrue(deleteQuery.get().contains("permanent=false"));

        GmailAccountDto renamed = apiClient.patchGmailDisplayName(5L, "Tên mới");
        assertEquals("Tên mới", renamed.displayName());
    }
}

