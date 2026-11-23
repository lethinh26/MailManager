package com.mailapp.mail.service;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.exception.MailNetworkException;
import com.mailapp.exception.RateLimitException;
import com.mailapp.mail.AccessTokenProvider;
import com.mailapp.mail.DefaultMailConnectionFactory;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.*;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử OWASP XSS nâng cao, Rate Limit tải đính kèm, Đo hiệu năng thực tế (30/100 thư, thư 10MB,
 * stream tải 25MB đo đỉnh RAM) và Độ bền lỗi mạng/DNS (Resilience).
 */
@DisplayName("Kiểm thử Bảo mật OWASP, Hiệu năng (30/100 thư, 10MB, 25MB stream) & Độ bền (MailPerformanceAndResilienceTest)")
class MailPerformanceAndResilienceTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup[]{
            new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS),
            new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)
    })
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("perf@test.local", "perf@test.local", "perfpass"))
            .withPerMethodLifecycle(false);

    private static AesCipher aesCipher;
    private static MailServiceImpl mailService;
    private static User perfUser;
    private static GmailAccount perfAccount;
    private static long uidOf10MbMail;
    private static long uidOf25MbMail;

    @BeforeAll
    static void initFixtures() throws Exception {
        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 0x5A);
        aesCipher = new AesCipher(Base64.getEncoder().encodeToString(keyBytes));

        MailConnectionProperties props = new MailConnectionProperties();
        props.setImapHost("127.0.0.1");
        props.setImapPort(greenMail.getImaps().getPort());
        props.setSmtpHost("127.0.0.1");
        props.setSmtpPort(greenMail.getSmtp().getPort());
        props.setRetryEnabled(false);

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                props, aesCipher, mock(AccessTokenProvider.class), null);
        GmailAccountRepository repo = mock(GmailAccountRepository.class);
        SystemSettingService settings = mock(SystemSettingService.class);
        AuditLogService audit = mock(AuditLogService.class);

        when(settings.isMaintenanceMode()).thenReturn(false);
        when(settings.isSendMailEnabled()).thenReturn(true);
        when(settings.getMaxAttachmentSizeMb()).thenReturn(30);

        perfUser = new User(100L, "perf_user", "perf@test.local", Role.USER);
        perfAccount = new GmailAccount();
        perfAccount.setId(100L);
        perfAccount.setUser(perfUser);
        perfAccount.setEmail("perf@test.local");
        perfAccount.setAuthType(AuthType.APP_PASSWORD);
        perfAccount.setAppPasswordEncrypted(aesCipher.encrypt("perfpass"));
        perfAccount.setStatus(AccountStatus.ACTIVE);
        when(repo.findById(100L)).thenReturn(Optional.of(perfAccount));

        mailService = new MailServiceImpl(
                repo, factory, new GmailFolderResolver(), settings, audit,
                new HtmlSanitizerService(), new MailHeaderAndRateGuard(100, 15, 120));

        // 1. Nạp 100 thư thật vào GreenMail INBOX (tiếng Việt UTF-8)
        Session session = greenMail.getSmtp().createSession();
        for (int i = 1; i <= 100; i++) {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress("sender" + i + "@ptit.edu.vn", "Người gửi " + i, StandardCharsets.UTF_8.name()));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress("perf@test.local"));
            msg.setSubject("Thông báo kiểm thử hiệu năng số #" + i, StandardCharsets.UTF_8.name());
            msg.setText("Nội dung thư kiểm thử UTF-8 số " + i, StandardCharsets.UTF_8.name());
            greenMail.getUserManager().getUser("perf@test.local").deliver(msg);
        }

        // 2. Nạp 1 thư có tệp đính kèm 10 MB
        deliverMailWithPayload(session, "Thư có tệp 10MB", "tailieu-10mb.bin", 10 * 1024 * 1024);

        // 3. Nạp 1 thư có tệp đính kèm 25 MB
        deliverMailWithPayload(session, "Thư có tệp 25MB", "video-25mb.bin", 25 * 1024 * 1024);

        // Lấy UID của 2 thư lớn (nằm ở đầu danh sách mới nhất trước)
        MailListResponse top = mailService.listMails(perfUser, 100L, "INBOX", 0, 5, null, null, null, null, null);
        uidOf25MbMail = top.items().stream().filter(m -> m.subject().contains("25MB")).findFirst().orElseThrow().uid();
        uidOf10MbMail = top.items().stream().filter(m -> m.subject().contains("10MB")).findFirst().orElseThrow().uid();
    }

    private static void deliverMailWithPayload(Session session, String subject, String filename, int sizeBytes) throws Exception {
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress("files@ptit.edu.vn"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress("perf@test.local"));
        msg.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeMultipart mp = new MimeMultipart("mixed");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Thư kiểm thử đính kèm dung lượng lớn: " + filename, StandardCharsets.UTF_8.name());
        mp.addBodyPart(text);

        byte[] payload = new byte[sizeBytes];
        Arrays.fill(payload, (byte) 0x42);
        MimeBodyPart att = new MimeBodyPart();
        att.setDataHandler(new DataHandler(new ByteArrayDataSource(payload, "application/octet-stream")));
        att.setFileName(filename);
        att.setDisposition(MimeBodyPart.ATTACHMENT);
        mp.addBodyPart(att);

        msg.setContent(mp);
        greenMail.getUserManager().getUser("perf@test.local").deliver(msg);
    }

    @Test
    @DisplayName("OWASP XSS Battery: Chặn 100% 15+ mẫu payload XSS, CSS độc, SVG, iframe, ảnh tracking và javascript:")
    void testOwaspXssPayloadBattery() {
        HtmlSanitizerService sanitizer = new HtmlSanitizerService();
        List<String> payloads = List.of(
                "<script>alert('xss')</script>",
                "<img src=x onerror=alert(1)>",
                "<svg/onload=alert(1)>",
                "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe>",
                "<math><mi xlink:href=\"javascript:alert(1)\">click</mi></math>",
                "<a href=\"javascript:alert(document.cookie)\">link</a>",
                "<a href=\"vbscript:msgbox(1)\">vb</a>",
                "<a href=\"data:text/html,<script>alert(1)</script>\">data</a>",
                "<div style=\"background:url(javascript:alert(1))\">css</div>",
                "<div style=\"width:expression(alert(1))\">ie</div>",
                "<object data=\"evil.swf\"></object>",
                "<embed src=\"evil.svg\">",
                "<form action=\"https://phish.example\"><input name=pw></form>",
                "<img src=\"data:image/svg+xml,<svg onload=alert(1)>\">",
                "<img src=\"https://tracker.evil/pixel.gif?u=123\" width=\"1\" height=\"1\">"
        );

        // Kiểm thử độc lập từng payload OWASP XSS (tránh 1 thẻ <svg> không đóng nuốt các payload sau)
        for (String payload : payloads) {
            HtmlSanitizerService.SanitizeResult res = sanitizer.sanitize(payload, false);
            String clean = res.html() != null ? res.html().toLowerCase(Locale.ROOT) : "";
            assertFalse(clean.contains("<script"), "Phải gỡ thẻ script trong: " + payload);
            assertFalse(clean.contains("<svg"), "Phải gỡ thẻ svg trong: " + payload);
            assertFalse(clean.contains("<iframe"), "Phải gỡ thẻ iframe trong: " + payload);
            assertFalse(clean.contains("<object"), "Phải gỡ thẻ object trong: " + payload);
            assertFalse(clean.contains("<embed"), "Phải gỡ thẻ embed trong: " + payload);
            assertFalse(clean.contains("<form"), "Phải gỡ thẻ form trong: " + payload);
            assertFalse(clean.contains("onerror"), "Phải gỡ sự kiện onerror trong: " + payload);
            assertFalse(clean.contains("onload"), "Phải gỡ sự kiện onload trong: " + payload);
            assertFalse(clean.contains("javascript:"), "Phải gỡ giao thức javascript: trong: " + payload);
            assertFalse(clean.contains("vbscript:"), "Phải gỡ giao thức vbscript: trong: " + payload);
            assertFalse(clean.contains("expression("), "Phải gỡ CSS expression() trong: " + payload);
        }

        // Kiểm thử riêng ảnh theo dõi (tracking pixel) bị chặn khi loadRemote=false
        HtmlSanitizerService.SanitizeResult trackerRes = sanitizer.sanitize(
                "<p>Chào bạn</p><img src=\"https://tracker.evil/pixel.gif?u=123\" width=\"1\" height=\"1\">", false);
        assertTrue(trackerRes.hasBlockedRemoteImages(), "Phải phát hiện và chặn ảnh tracker từ xa!");
        assertFalse(trackerRes.html().toLowerCase(Locale.ROOT).contains("tracker.evil"),
                "Không được để lại URL tracking pixel khi loadRemote=false");
    }

    @Test
    @DisplayName("OWASP Rate Limit: Chặn tải đính kèm vượt ngưỡng bằng 429 + Retry-After")
    void testAttachmentDownloadRateLimit() {
        MailHeaderAndRateGuard strictGuard = new MailHeaderAndRateGuard(5, 15, 3);
        strictGuard.checkAndRecordDownloadRate(77L);
        strictGuard.checkAndRecordDownloadRate(77L);
        strictGuard.checkAndRecordDownloadRate(77L);
        RateLimitException ex = assertThrows(RateLimitException.class,
                () -> strictGuard.checkAndRecordDownloadRate(77L));
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Benchmark Hiệu năng: Liệt kê 30/100 thư, mở thư 10MB, stream tải 25MB & đo RAM đỉnh")
    void testPerformanceBenchmarks_30_100_10MB_25MBStream() throws Exception {
        // 1. Đo liệt kê 30 thư (FetchProfile)
        long t0 = System.nanoTime();
        MailListResponse list30 = mailService.listMails(perfUser, 100L, "INBOX", 0, 30, null, null, null, null, null);
        long ms30 = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(30, list30.items().size());

        // 2. Đo liệt kê 100 thư (FetchProfile)
        long t1 = System.nanoTime();
        MailListResponse list100 = mailService.listMails(perfUser, 100L, "INBOX", 0, 100, null, null, null, null, null);
        long ms100 = (System.nanoTime() - t1) / 1_000_000;
        assertEquals(100, list100.items().size());

        // 3. Đo mở chi tiết thư có tệp đính kèm 10 MB (chỉ trả metadata đính kèm)
        long t2 = System.nanoTime();
        MailDetailResponse detail10Mb = mailService.getMailDetail(perfUser, 100L, "INBOX", uidOf10MbMail, false, null);
        long ms10Mb = (System.nanoTime() - t2) / 1_000_000;
        assertEquals(1, detail10Mb.attachments().size());
        assertEquals("tailieu-10mb.bin", detail10Mb.attachments().get(0).filename());

        // 4. Đo stream tải tệp 25 MB và ước lượng RAM đỉnh của Server (dùng OutputStream đếm byte như HTTP response)
        System.gc();
        Runtime rt = Runtime.getRuntime();
        long heapBefore = rt.totalMemory() - rt.freeMemory();
        long t3 = System.nanoTime();
        MailService.AttachmentDownloadHandle handle = mailService.prepareAttachmentDownload(
                perfUser, 100L, "INBOX", uidOf25MbMail, 0, null);
        final long[] streamedBytes = {0L};
        final long[] peakHeap = {heapBefore};
        OutputStream countingSink = new OutputStream() {
            @Override public void write(int b) { streamedBytes[0]++; }
            @Override public void write(byte[] b, int off, int len) {
                streamedBytes[0] += len;
                long used = rt.totalMemory() - rt.freeMemory();
                if (used > peakHeap[0]) peakHeap[0] = used;
            }
        };
        handle.streamer().streamTo(countingSink);
        long ms25Mb = (System.nanoTime() - t3) / 1_000_000;
        long heapDeltaMb = Math.max(0, (peakHeap[0] - heapBefore) / (1024 * 1024));

        assertEquals(25L * 1024 * 1024, streamedBytes[0], "Phải stream đủ 25 MB!");
        System.out.printf("[PERF_BENCHMARK] List30=%dms | List100=%dms | Open10MB=%dms | Stream25MB=%dms | StreamHeapDelta=%dMB%n",
                ms30, ms100, ms10Mb, ms25Mb, heapDeltaMb);
    }

    @Test
    @DisplayName("Độ bền (Resilience): Lỗi DNS máy chủ IMAP trả MailNetworkException sạch, không treo")
    void testResilience_DnsFailureReturnsCleanMailNetworkException() {
        MailConnectionProperties badDns = new MailConnectionProperties();
        badDns.setImapHost("nonexistent.invalid.domain.test");
        badDns.setImapPort(993);
        badDns.setConnectionTimeoutMs(1500);
        badDns.setTimeoutMs(1500);
        badDns.setRetryEnabled(false);

        DefaultMailConnectionFactory badFactory = new DefaultMailConnectionFactory(
                badDns, aesCipher, mock(AccessTokenProvider.class), null);
        MailCredentials creds = MailCredentials.ofAppPassword("u@test.com", "secret-pass");

        long start = System.currentTimeMillis();
        assertThrows(MailNetworkException.class, () -> badFactory.openImap(creds));
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 10_000, "Lỗi DNS phải fail nhanh, không treo vô hạn (thực tế: " + elapsed + "ms)");
    }
}

