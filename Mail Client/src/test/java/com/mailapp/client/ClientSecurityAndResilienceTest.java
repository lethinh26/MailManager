package com.mailapp.client;

import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.service.AttachmentCleanupService;
import com.mailapp.client.service.AttachmentCleanupService.ItemStatus;
import com.mailapp.client.service.AttachmentCleanupService.MailCleanupResult;
import com.mailapp.client.ui.frames.LoginFrame;
import com.mailapp.client.util.ClientSafetyUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Bảo mật Client & Độ bền (Đuôi nguy hiểm, Lỗi tiếng Việt, Đĩa đầy, Server khởi động lại)")
class ClientSecurityAndResilienceTest {

    @Test
    @DisplayName("Cảnh báo tệp đính kèm có đuôi thực thi nguy hiểm (.exe, .bat, .js, .jar, .ps1, .lnk, .vbs, .msi)")
    void testDangerousExtensionDetection() {
        for (String bad : List.of("setup.exe", "run.BAT", "script.js", "payload.jar",
                "install.ps1", "shortcut.lnk", "macro.vbs", "patch.msi", "invoice.pdf.exe")) {
            assertTrue(ClientSafetyUtil.isDangerousExecutableExtension(bad),
                    "Phải cảnh báo đuôi nguy hiểm: " + bad);
        }
        for (String safe : List.of("bao-cao.pdf", "bang-diem.docx", "anh.png", "readme.txt", "data.csv")) {
            assertFalse(ClientSafetyUtil.isDangerousExecutableExtension(safe),
                    "Tệp văn phòng/ảnh bình thường không bị đánh dấu nguy hiểm: " + safe);
        }
    }

    @Test
    @DisplayName("Mọi lỗi kỹ thuật đều chuyển sang thông báo tiếng Việt kèm cách khắc phục")
    void testFriendlyVietnameseErrorTranslation() {
        assertTrue(ClientSafetyUtil.toFriendlyVietnameseError(new ConnectException("Connection refused"))
                .contains("khởi động lại"));
        assertTrue(ClientSafetyUtil.toFriendlyVietnameseError(new UnknownHostException("bad.host"))
                .contains("DNS"));
        assertTrue(ClientSafetyUtil.toFriendlyVietnameseError(new SSLHandshakeException("PKIX"))
                .contains(".cer"));
        assertTrue(ClientSafetyUtil.toFriendlyVietnameseError(new IOException("There is not enough space on the disk"))
                .contains("Ổ đĩa"));
    }

    @Test
    void publicConnectionCheckDoesNotOverlapOrExposeRawErrors() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<LoginFrame> frame = new AtomicReference<>();
        ApiClient transport = new ApiClient("http://127.0.0.1:19999") {
            @Override public long pingServer() throws IOException, InterruptedException {
                if (calls.incrementAndGet() == 1) {
                    started.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
                    throw new ConnectException();
                }
                throw new SSLHandshakeException("fixture-internal-certificate-diagnostic");
            }
        };
        try {
            SwingUtilities.invokeAndWait(() -> {
                LoginFrame login = new LoginFrame(transport, false);
                frame.set(login);
                login.checkServerConnection();
                assertFalse(login.getExpectedTabOrder().get(6).isEnabled());
                login.checkServerConnection();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            release.countDown();
            assertEquals("[X] Không kết nối được máy chủ.", awaitConnectionResult(frame.get()));
            SwingUtilities.invokeAndWait(() -> frame.get().checkServerConnection());
            assertEquals("[X] Chứng chỉ TLS không hợp lệ.", awaitConnectionResult(frame.get()));
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
        }
    }

    private static String awaitConnectionResult(LoginFrame frame) throws Exception {
        for (int i = 0; i < 100; i++) {
            AtomicReference<String> text = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                if (frame.getExpectedTabOrder().get(6).isEnabled())
                    text.set(connectionResult(frame));
            });
            if (text.get() != null) return text.get();
            Thread.sleep(50);
        }
        fail("Public connection check never completed");
        return null;
    }

    private static String connectionResult(Component c) {
        if (c instanceof JLabel label && label.getText() != null && label.getText().startsWith("[X]"))
            return label.getText();
        if (c instanceof Container container)
            for (Component child : container.getComponents()) {
                String value = connectionResult(child);
                if (value != null) return value;
            }
        return null;
    }

    @Test
    @DisplayName("Độ bền Đĩa đầy khi tải đính kèm -> Báo lỗi rõ và TUYỆT ĐỐI KHÔNG XÓA thư")
    void testDiskFullDuringCleanup_NeverDeletesMail(@TempDir Path tempDir) {
        AtomicInteger deleteCalls = new AtomicInteger(0);
        // Subclass ApiClient giả lập lỗi đĩa đầy (ENOSPC / Not enough space) khi ghi tệp
        ApiClient diskFullClient = new ApiClient("http://localhost:1") {
            @Override
            public MailDetailResponse getMailDetail(Long gmailId, String folder, long uid, boolean loadRemote, Long uidValidity) {
                return new MailDetailResponse(uid, 1L, new MailAddressDto("A", "a@t.com"),
                        List.of(), List.of(), List.of(), List.of(), "2026-11-22T09:00:00Z",
                        null, null, null, "Thư test đĩa đầy", "Body", null, false, false,
                        List.of(new MailAttachmentDto(0, "big.zip", "application/zip", 1024, false, null)),
                        new MailFlagsDto(true, false, false, false));
            }
            @Override
            public DownloadResult downloadAttachmentToFile(Long gmailId, String folder, long uid,
                                                           int attachmentIndex, Long uidValidity, File targetFile) throws IOException {
                throw new IOException("There is not enough space on the disk");
            }
            @Override
            public void deleteMail(Long gmailId, String folder, long uid, boolean permanent, Long uidValidity) {
                deleteCalls.incrementAndGet();
            }
        };

        AttachmentCleanupService svc = new AttachmentCleanupService(diskFullClient);
        MailSummaryDto sum = new MailSummaryDto(88L, 1L, new MailAddressDto("A", "a@t.com"),
                List.of(), "Thư test đĩa đầy", "", "2026-11-22T09:00:00Z",
                new MailFlagsDto(false, false, false, false), 1024, true, 1);

        List<MailCleanupResult> res = svc.processMails(1L, "u@gmail.com", "INBOX",
                List.of(sum), tempDir.toFile(), false, false, new AtomicBoolean(false), null);

        assertEquals(ItemStatus.FAILED, res.get(0).status());
        assertFalse(res.get(0).deleted());
        assertEquals(0, deleteCalls.get(), "Khi đĩa đầy, TUYỆT ĐỐI không được gọi xóa thư!");
    }

    @Test
    @DisplayName("Độ bền: Server khởi động lại khi Client đang mở -> báo lỗi rõ và tự phục hồi ngay khi Server lên lại")
    void testServerRestartRecovery() throws Exception {
        HttpServer s1 = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = s1.getAddress().getPort();
        String okJson = "{\"registrationEnabled\":true,\"oauth2LinkEnabled\":true,\"appPasswordLinkEnabled\":true,\"maintenanceMode\":false,\"sendMailEnabled\":true,\"maxGmailPerUser\":5,\"maxAttachmentSizeMb\":25}";
        s1.createContext("/api/public/config", ex -> {
            byte[] b = okJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        s1.start();

        ApiClient client = new ApiClient("http://localhost:" + port);
        client.setAccessToken("preserved-jwt");

        // 1. Server đang sống -> thành công
        PublicConfigDto c1 = client.getPublicConfig();
        assertTrue(c1.registrationEnabled());

        // 2. Server tắt đột ngột -> Client nhận lỗi thân thiện bằng tiếng Việt, KHÔNG mất token
        s1.stop(0);
        Exception downEx = assertThrows(Exception.class, client::getPublicConfig);
        String msg = ClientSafetyUtil.toFriendlyVietnameseError(downEx);
        assertTrue(msg.contains("máy chủ"), "Phải thông báo tiếng Việt rõ ràng khi server tắt: " + msg);

        // 3. Server khởi động lại trên cùng cổng -> Client phục hồi ngay lập tức ở lệnh tiếp theo
        HttpServer s2 = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        s2.createContext("/api/public/config", ex -> {
            byte[] b = okJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        s2.start();
        try {
            PublicConfigDto c2 = client.getPublicConfig();
            assertTrue(c2.registrationEnabled());
        } finally {
            s2.stop(0);
        }
    }
}

