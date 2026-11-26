package com.mailapp.client;

import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.service.AttachmentCleanupService;
import com.mailapp.client.service.AttachmentCleanupService.ItemStatus;
import com.mailapp.client.service.AttachmentCleanupService.MailCleanupResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử nghiệp vụ cốt lõi: Tải tệp đính kèm rồi Xóa thư (AttachmentCleanupServiceTest)")
class AttachmentCleanupServiceTest {

    private HttpServer server;
    private ApiClient apiClient;
    private AttachmentCleanupService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        apiClient = new ApiClient("http://localhost:" + server.getAddress().getPort());
        apiClient.setAccessToken("jwt-token");
        service = new AttachmentCleanupService(apiClient);
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

    private MailSummaryDto sampleSummary(long uid, String subject) {
        return new MailSummaryDto(uid, 10L, new MailAddressDto("Sender", "s@test.com"),
                List.of(), subject, "snippet", "2026-11-22T09:00:00Z",
                new MailFlagsDto(false, false, false, false), 1024, true, 2);
    }

    @Test
    @DisplayName("Thành công: Tải đủ 2 tệp vào MailManager/<email>/<yyyy-MM-dd>/ + SHA-256 rồi mới gọi XÓA")
    void testHappyPath_DownloadAllThenDelete(@TempDir Path baseDir) throws Exception {
        AtomicInteger deleteCalls = new AtomicInteger(0);
        byte[] f0 = "PDF_FILE_0_BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] f1 = "DOCX_FILE_1_BYTES_LONGER".getBytes(StandardCharsets.UTF_8);

        server.createContext("/api/mails/1/INBOX/10/attachments/0/download", ex -> {
            ex.sendResponseHeaders(200, f0.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(f0); }
        });
        server.createContext("/api/mails/1/INBOX/10/attachments/1/download", ex -> {
            ex.sendResponseHeaders(200, f1.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(f1); }
        });
        server.createContext("/api/mails/1/INBOX/10", ex -> {
            if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                deleteCalls.incrementAndGet();
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            assertTrue(ex.getRequestURI().getQuery().contains("markSeen=false"), "Cleanup preview must not mark mail read");
            respondJson(ex, 200, """
                    {
                      "uid": 10, "uidValidity": 10, "subject": "Báo cáo tuần",
                      "attachments": [
                        {"index":0,"filename":"report.pdf","contentType":"application/pdf","size":16,"inline":false},
                        {"index":1,"filename":"notes.txt","contentType":"text/plain","size":24,"inline":false}
                      ]
                    }
                    """);
        });

        List<MailCleanupResult> res = service.processMails(1L, "cuong@gmail.com", "INBOX",
                List.of(sampleSummary(10L, "Báo cáo tuần")), baseDir.toFile(), false, false, new AtomicBoolean(false), null);

        assertEquals(1, res.size());
        assertEquals(ItemStatus.SUCCESS, res.get(0).status());
        assertTrue(res.get(0).deleted());
        assertEquals(1, deleteCalls.get());
        assertEquals(2, res.get(0).downloadedFiles().size());

        Path expectedDir = AttachmentCleanupService.buildTargetDirectory(baseDir.toFile(), "cuong@gmail.com", LocalDate.now());
        assertTrue(Files.exists(expectedDir.resolve("report.pdf")));
        assertTrue(Files.exists(expectedDir.resolve("notes.txt")));
        assertArrayEquals(f0, Files.readAllBytes(expectedDir.resolve("report.pdf")));
    }

    @Test
    void dryRunPreservesSizeAccuracyButActualCleanupUsesTransferredBytes(@TempDir Path baseDir) throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        byte[] bytes = "1234567890123456789012".getBytes(StandardCharsets.UTF_8);
        server.createContext("/api/mails/1/INBOX/60/attachments/0/download", ex -> {
            downloads.incrementAndGet();
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/api/mails/1/INBOX/60/attachments/1/download", ex -> {
            downloads.incrementAndGet();
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/api/mails/1/INBOX/60", ex -> {
            if ("DELETE".equals(ex.getRequestMethod())) {
                deletes.incrementAndGet();
                ex.sendResponseHeaders(204, -1);
                ex.close();
            } else {
                respondJson(ex, 200, """
                        {"uid":60,"uidValidity":10,"subject":"size accuracy",
                         "attachments":[
                           {"index":0,"filename":"estimated.txt","size":23,"sizeAccuracy":"ESTIMATED"},
                           {"index":1,"fileName":"unknown.txt","sizeBytes":0,"sizeAccuracy":"UNKNOWN"}
                         ]}
                        """);
            }
        });
        var targets = List.of(sampleSummary(60, "size accuracy"));
        var preview = service.processMails(1L, "fixture@test.local", "INBOX", targets,
                baseDir.toFile(), false, true, new AtomicBoolean(false), null).getFirst();
        assertEquals(ItemStatus.DRY_RUN, preview.status());
        assertEquals(23, preview.downloadedFiles().get(0).bytes());
        assertEquals(AttachmentSizeAccuracy.ESTIMATED, preview.downloadedFiles().get(0).sizeAccuracy());
        assertEquals(AttachmentSizeAccuracy.UNKNOWN, preview.downloadedFiles().get(1).sizeAccuracy());
        assertEquals(0, downloads.get());
        assertEquals(0, deletes.get());

        var actual = service.processMails(1L, "fixture@test.local", "INBOX", targets,
                baseDir.toFile(), false, false, new AtomicBoolean(false), null).getFirst();
        assertEquals(ItemStatus.SUCCESS, actual.status());
        assertTrue(actual.deleted());
        assertEquals(2, downloads.get());
        assertEquals(1, deletes.get());
        for (var file : actual.downloadedFiles()) {
            assertEquals(22, file.bytes());
            assertEquals(AttachmentSizeAccuracy.EXACT, file.sizeAccuracy());
            assertArrayEquals(bytes, Files.readAllBytes(file.savedPath()));
            assertEquals(64, file.sha256().length());
        }
    }

    @Test
    void uncertainDeleteKeepsDownloadedFilesAndStopsBatch(@TempDir Path baseDir) throws Exception {
        AtomicInteger deleteCalls = new AtomicInteger();
        AtomicInteger nextMailCalls = new AtomicInteger();
        byte[] bytes = "verified attachment".getBytes(StandardCharsets.UTF_8);
        server.createContext("/api/mails/1/INBOX/40/attachments/0/download", ex -> {
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/api/mails/1/INBOX/40", ex -> {
            if ("DELETE".equals(ex.getRequestMethod())) {
                deleteCalls.incrementAndGet();
                respondJson(ex, 502, "{\"code\":\"GMAIL_CONNECTION_ERROR\",\"message\":\"Mutation reply lost\"}");
            } else {
                respondJson(ex, 200, """
                        {"uid":40,"uidValidity":10,"subject":"Fixture",
                         "attachments":[{"index":0,"filename":"fixture.txt","contentType":"text/plain","size":19,"inline":false}]}
                        """);
            }
        });
        server.createContext("/api/mails/1/INBOX/41", ex -> {
            nextMailCalls.incrementAndGet();
            respondJson(ex, 500, "{}");
        });
        var results = service.processMails(1L, "fixture@test.local", "INBOX",
                List.of(sampleSummary(40, "Fixture"), sampleSummary(41, "Next")),
                baseDir.toFile(), false, false, new AtomicBoolean(false), null);
        assertEquals(1, results.size());
        var uncertain = results.getFirst();
        assertEquals(ItemStatus.DELETE_UNCERTAIN, uncertain.status());
        assertFalse(uncertain.deleted());
        assertEquals(1, uncertain.downloadedFiles().size());
        assertArrayEquals(bytes, Files.readAllBytes(uncertain.downloadedFiles().getFirst().savedPath()));
        assertEquals(1, deleteCalls.get());
        assertEquals(0, nextMailCalls.get());
    }

    @Test
    @DisplayName("Bất biến an toàn: Tải tệp thứ 2 thất bại -> TUYỆT ĐỐI KHÔNG gọi XÓA thư")
    void testDownloadFailure_MustNeverDeleteMail(@TempDir Path baseDir) {
        AtomicInteger deleteCalls = new AtomicInteger(0);
        byte[] f0 = "OK_FILE".getBytes(StandardCharsets.UTF_8);

        server.createContext("/api/mails/1/INBOX/20/attachments/0/download", ex -> {
            ex.sendResponseHeaders(200, f0.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(f0); }
        });
        server.createContext("/api/mails/1/INBOX/20/attachments/1/download", ex -> {
            respondJson(ex, 500, "{\"code\":\"IMAP_ERROR\",\"message\":\"Lỗi đọc luồng IMAP\"}");
        });
        server.createContext("/api/mails/1/INBOX/20", ex -> {
            if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                deleteCalls.incrementAndGet();
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            respondJson(ex, 200, """
                    {
                      "uid": 20, "uidValidity": 10, "subject": "Thư lỗi tải",
                      "attachments": [
                        {"index":0,"filename":"ok.pdf","contentType":"application/pdf","size":7,"inline":false},
                                {"index":1,"filename":"broken.zip","contentType":"application/zip","size":100,"inline":true}
                      ]
                    }
                    """);
        });

        List<MailCleanupResult> res = service.processMails(1L, "cuong@gmail.com", "INBOX",
                List.of(sampleSummary(20L, "Thư lỗi tải")), baseDir.toFile(), false, false, new AtomicBoolean(false), null);

        assertEquals(ItemStatus.FAILED, res.get(0).status());
        assertFalse(res.get(0).deleted());
        assertEquals(0, deleteCalls.get(), "KHÔNG ĐƯỢC PHÉP XÓA THƯ khi bất kỳ tệp đính kèm nào tải thất bại!");
        assertEquals(1, service.getConsecutiveErrors());
    }

    @Test
    @DisplayName("Trùng tên tệp & tên độc hại (../, CRLF) -> tự đổi tên (1) và giữ nguyên trong thư mục đích")
    void testFilenameCollisionAndMaliciousPathSanitization(@TempDir Path baseDir) throws Exception {
        Path dayDir = AttachmentCleanupService.buildTargetDirectory(baseDir.toFile(), "u@gmail.com", LocalDate.now());
        Files.createDirectories(dayDir);
        Files.writeString(dayDir.resolve("report.pdf"), "EXISTING_DO_NOT_OVERWRITE");

        byte[] newBytes = "NEW_DATA".getBytes(StandardCharsets.UTF_8);
        server.createContext("/api/mails/1/INBOX/30/attachments/0/download", ex -> {
            ex.sendResponseHeaders(200, newBytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(newBytes); }
        });
        server.createContext("/api/mails/1/INBOX/30/attachments/1/download", ex -> {
            ex.sendResponseHeaders(200, newBytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(newBytes); }
        });
        server.createContext("/api/mails/1/INBOX/30", ex -> {
            if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            respondJson(ex, 200, """
                    {
                      "uid": 30, "uidValidity": 10, "subject": "Trùng tên & Độc hại",
                      "attachments": [
                        {"index":0,"filename":"report.pdf","contentType":"application/pdf","size":8,"inline":false},
                        {"index":1,"filename":"../../evil\\r\\n<script>.exe","contentType":"application/octet-stream","size":8,"inline":false}
                      ]
                    }
                    """);
        });

        List<MailCleanupResult> res = service.processMails(1L, "u@gmail.com", "INBOX",
                List.of(sampleSummary(30L, "Trùng tên")), baseDir.toFile(), false, false, new AtomicBoolean(false), null);

        assertEquals(ItemStatus.SUCCESS, res.get(0).status());
        assertEquals("EXISTING_DO_NOT_OVERWRITE", Files.readString(dayDir.resolve("report.pdf")));
        assertTrue(Files.exists(dayDir.resolve("report (1).pdf")));

        Path secondSaved = res.get(0).downloadedFiles().get(1).savedPath();
        assertTrue(secondSaved.normalize().startsWith(dayDir.normalize()), "Tệp lưu phải nằm tuyệt đối trong dayDir!");
        assertFalse(secondSaved.getFileName().toString().contains(".."));
    }

    @Test
    @DisplayName("Hủy giữa chừng và Chạy thử (dry-run) -> KHÔNG xóa thư")
    void testCancelMidwayAndDryRun_NeverDeletes(@TempDir Path baseDir) {
        AtomicInteger deleteCalls = new AtomicInteger(0);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        byte[] f0 = "PART1".getBytes(StandardCharsets.UTF_8);

        server.createContext("/api/mails/1/INBOX/40/attachments/0/download", ex -> {
            ex.sendResponseHeaders(200, f0.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(f0); }
            cancelled.set(true); // Hủy ngay sau khi tải tệp đầu
        });
        server.createContext("/api/mails/1/INBOX/40", ex -> {
            if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                deleteCalls.incrementAndGet();
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            respondJson(ex, 200, """
                    {
                      "uid": 40, "uidValidity": 10, "subject": "Thư hủy giữa chừng",
                      "attachments": [
                        {"index":0,"filename":"a.pdf","contentType":"application/pdf","size":5,"inline":false},
                        {"index":1,"filename":"b.pdf","contentType":"application/pdf","size":5,"inline":false}
                      ]
                    }
                    """);
        });

        // 1. Dry-run
        List<MailCleanupResult> dryRes = service.processMails(1L, "u@gmail.com", "INBOX",
                List.of(sampleSummary(40L, "Dry")), baseDir.toFile(), false, true, new AtomicBoolean(false), null);
        assertEquals(ItemStatus.DRY_RUN, dryRes.get(0).status());
        assertFalse(dryRes.get(0).deleted());
        assertEquals(0, deleteCalls.get());

        // 2. Cancel mid-way
        List<MailCleanupResult> cancelRes = service.processMails(1L, "u@gmail.com", "INBOX",
                List.of(sampleSummary(40L, "Cancel")), baseDir.toFile(), false, false, cancelled, null);
        assertEquals(ItemStatus.CANCELLED, cancelRes.get(0).status());
        assertFalse(cancelRes.get(0).deleted());
        assertEquals(0, deleteCalls.get());
    }

    @Test
    @DisplayName("Gặp 409 GMAIL_NEEDS_REAUTH -> dừng ngay lô xử lý và không xóa thư")
    void testNeedsReauthStopsBatch(@TempDir Path baseDir) {
        server.createContext("/api/mails/1/INBOX/50", ex -> respondJson(ex, 409,
                "{\"code\":\"GMAIL_NEEDS_REAUTH\",\"message\":\"Hết quyền\"}"));

        List<MailCleanupResult> res = service.processMails(1L, "u@gmail.com", "INBOX",
                List.of(sampleSummary(50L, "M1"), sampleSummary(51L, "M2")),
                baseDir.toFile(), false, false, new AtomicBoolean(false), null);

        assertEquals(1, res.size(), "Phải dừng ngay sau thư đầu tiên báo GMAIL_NEEDS_REAUTH!");
        assertEquals(ItemStatus.FAILED, res.get(0).status());
        assertFalse(res.get(0).deleted());
        assertTrue(service.getConsecutiveErrors() >= 1);
    }

    @Test
    void mismatchedDetailIdentityOrDuplicateAttachmentIndexStopsBatch(@TempDir Path baseDir) {
        AtomicInteger mutationsOrDownloads = new AtomicInteger();
        AtomicInteger secondMailReads = new AtomicInteger();
        AtomicBoolean mismatch = new AtomicBoolean(true);
        server.createContext("/api/mails/1/INBOX/70/attachments", ex -> {
            mutationsOrDownloads.incrementAndGet();
            respondJson(ex, 500, "{}");
        });
        server.createContext("/api/mails/1/INBOX/70", ex -> {
            if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                mutationsOrDownloads.incrementAndGet();
                ex.sendResponseHeaders(204, -1);
                ex.close();
            } else {
                respondJson(ex, 200, """
                        {"uid":70,"uidValidity":%d,"attachments":[
                        {"index":0,"filename":"a.txt","size":3},
                        {"index":0,"filename":"b.txt","size":3}]}
                        """.formatted(mismatch.get() ? 11 : 10));
            }
        });
        server.createContext("/api/mails/1/INBOX/71", ex -> {
            secondMailReads.incrementAndGet();
            respondJson(ex, 500, "{}");
        });
        var targets = List.of(sampleSummary(70, "first"), sampleSummary(71, "second"));
        for (boolean wrongIdentity : new boolean[]{true, false}) {
            mismatch.set(wrongIdentity);
            var result = service.processMails(1L, "fixture@test.local", "INBOX",
                    targets, baseDir.toFile(), false, false, new AtomicBoolean(), null);
            assertEquals(1, result.size());
            assertEquals(ItemStatus.FAILED, result.getFirst().status());
            assertFalse(result.getFirst().deleted());
        }
        assertEquals(0, mutationsOrDownloads.get());
        assertEquals(0, secondMailReads.get());
    }

    @Test
    void changedSelectionWhileDownloadingNeverDeletes(@TempDir Path baseDir) throws Exception {
        AtomicBoolean current = new AtomicBoolean(true);
        AtomicInteger deletes = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();
        server.createContext("/api/mails/1/INBOX/80/attachments/0/download", ex -> {
            downloads.incrementAndGet();
            current.set(false);
            byte[] bytes = "data".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            try (var out = ex.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/api/mails/1/INBOX/80", ex -> {
            if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                deletes.incrementAndGet();
                ex.sendResponseHeaders(204, -1);
                ex.close();
            } else {
                respondJson(ex, 200, """
                        {"uid":80,"uidValidity":10,"attachments":[
                        {"index":0,"filename":"a.txt","size":4}]}
                        """);
            }
        });
        var result = service.processMails(1L, "fixture@test.local", "INBOX",
                List.of(sampleSummary(80, "first")), baseDir.toFile(), false, false,
                new AtomicBoolean(), null, current::get).getFirst();
        assertEquals(ItemStatus.CANCELLED, result.status());
        assertFalse(result.deleted());
        assertEquals(1, downloads.get());
        assertEquals(0, deletes.get());
        try (var files = Files.walk(baseDir)) {
            assertFalse(files.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    @DisplayName("Hồi quy: Chặn tên thiết bị Windows (CON, NUL, AUX, COM1) và cắt đường dẫn dài < 250 ký tự")
    void testWindowsReservedNamesAndLongPathSafety(@TempDir Path baseDir) throws Exception {
        assertEquals("file_CON", AttachmentCleanupService.sanitizeFilename("CON"));
        assertEquals("file_CON.txt", AttachmentCleanupService.sanitizeFilename("CON.txt"));
        assertEquals("file_NUL.pdf", AttachmentCleanupService.sanitizeFilename("NUL.pdf"));
        assertEquals("file_AUX.log", AttachmentCleanupService.sanitizeFilename("AUX.log"));
        assertEquals("file_COM1.doc", AttachmentCleanupService.sanitizeFilename("COM1.doc"));

        // Kiểm tra đường dẫn lồng sâu + tên tệp dài không vượt giới hạn MAX_PATH Windows
        Path deepDir = baseDir.resolve("MailManager").resolve("sinh.vien.ptit@gmail.com").resolve("2026-11-22");
        Files.createDirectories(deepDir);
        String longName = "Tai_lieu_bao_ve_do_an_" + "x".repeat(150) + ".pdf";
        Path resolved = AttachmentCleanupService.resolveUniqueFilePath(deepDir, AttachmentCleanupService.sanitizeFilename(longName));
        assertTrue(resolved.toAbsolutePath().toString().length() <= 250,
                "Đường dẫn đầy đủ phải <= 250 ký tự để tránh lỗi MAX_PATH trên Windows! Thực tế: " + resolved.toAbsolutePath().toString().length());
        assertTrue(resolved.getFileName().toString().endsWith(".pdf"));
        Files.writeString(resolved, "OK_LONG_PATH");
        assertTrue(Files.exists(resolved));
    }
}

