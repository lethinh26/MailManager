package com.mailapp.mail;

import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.security.AesCipher;
import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.SubjectTerm;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Kiểm thử tích hợp kết nối thật tới máy chủ Google Gmail (IMAP over TLS & SMTP STARTTLS).
 * Chỉ chạy khi môi trường có biến GMAIL_TEST_EMAIL và GMAIL_TEST_APP_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "GMAIL_TEST_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GMAIL_TEST_APP_PASSWORD", matches = ".+")
class RealGmailIntegrationTest {

    @Test
    @DisplayName("Kết nối thật tới Gmail IMAPS: Xác thực, liệt kê INBOX, đọc 1 thư và phân giải folders")
    void testRealGmailImap() throws Exception {
        String email = System.getenv("GMAIL_TEST_EMAIL");
        String appPassword = System.getenv("GMAIL_TEST_APP_PASSWORD");

        MailConnectionProperties properties = new MailConnectionProperties();
        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

        MailCredentials credentials = MailCredentials.ofAppPassword(email, appPassword);

        ConnectionTestResult testResult = factory.testImap(credentials);
        assertTrue(testResult.isSuccessful(), "Thử nghiệm IMAP tới Gmail phải thành công: " + testResult.message());

        try (ImapConnection imap = factory.openImap(credentials)) {
            assertTrue(imap.isConnected());

            Folder inbox = imap.folder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int messageCount = inbox.getMessageCount();
            assertTrue(messageCount >= 0);
            if (messageCount > 0) {
                Message latest = inbox.getMessage(messageCount);
                assertNotNull(latest.getSubject());
            }
            inbox.close(false);

            GmailFolderResolver folderResolver = new GmailFolderResolver();
            Folder sent = folderResolver.resolve(imap.getStore(), GmailFolderResolver.SpecialFolder.SENT);
            assertNotNull(sent);
            assertTrue(sent.exists());
        }
    }

    @Test
    @DisplayName("Vòng đời thật Gmail: Gửi cho chính mình kèm 1 file, tải lại file, so SHA-256, xóa vào Thùng rác")
    void testRealGmailSmtpSendAttachmentVerifySha256AndTrash() throws Exception {
        String email = System.getenv("GMAIL_TEST_EMAIL");
        String appPassword = System.getenv("GMAIL_TEST_APP_PASSWORD");

        MailConnectionProperties properties = new MailConnectionProperties();
        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

        MailCredentials credentials = MailCredentials.ofAppPassword(email, appPassword);
        String marker = "MailManager-E2E-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] payloadBytes = ("Du lieu kiem thu SHA-256: " + marker).getBytes(StandardCharsets.UTF_8);
        byte[] expectedSha = MessageDigest.getInstance("SHA-256").digest(payloadBytes);

        // 1. Gửi email kèm 1 file đính kèm cho chính mình
        try (SmtpConnection smtp = factory.openSmtp(credentials)) {
            assertTrue(smtp.isConnected());
            MimeMessage message = smtp.createMimeMessage();
            message.setFrom(new InternetAddress(email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("[TEST] " + marker, "UTF-8");

            MimeMultipart mixed = new MimeMultipart("mixed");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Kiểm thử gửi-nhận-tải-xóa tự động từ MailManager.", "UTF-8");
            mixed.addBodyPart(textPart);

            MimeBodyPart filePart = new MimeBodyPart();
            filePart.setDataHandler(new DataHandler(new ByteArrayDataSource(payloadBytes, "text/plain")));
            filePart.setFileName("verify-sha256.txt");
            filePart.setDisposition(Part.ATTACHMENT);
            mixed.addBodyPart(filePart);

            message.setContent(mixed);
            message.saveChanges();
            smtp.sendMessage(message);
        }

        // 2. Chờ Gmail nhận thư, tải file đính kèm, so SHA-256, chuyển vào Thùng rác, kiểm tra INBOX & Trash, rồi xóa vĩnh viễn
        boolean verifiedAndMoved = false;
        for (int attempt = 0; attempt < 5 && !verifiedAndMoved; attempt++) {
            Thread.sleep(3000);
            try (ImapConnection imap = factory.openImap(credentials)) {
                Folder inbox = imap.folder("INBOX");
                inbox.open(Folder.READ_WRITE);
                try {
                    Message[] found = inbox.search(new SubjectTerm(marker));
                    if (found.length == 0) continue;

                    Message target = found[found.length - 1];
                    boolean shaChecked = false;
                    Object content = target.getContent();
                    if (content instanceof Multipart mp) {
                        for (int i = 0; i < mp.getCount(); i++) {
                            BodyPart bp = mp.getBodyPart(i);
                            if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                                try (InputStream is = bp.getInputStream()) {
                                    byte[] actualSha = MessageDigest.getInstance("SHA-256").digest(is.readAllBytes());
                                    assertArrayEquals(expectedSha, actualSha, "SHA-256 tệp tải về từ Gmail thật phải khớp!");
                                    shaChecked = true;
                                }
                            }
                        }
                    }
                    assertTrue(shaChecked, "Phải tải và kiểm chứng được SHA-256 tệp đính kèm từ Gmail thật");

                    // 3. Chuyển thư test vào Thùng rác (\Trash): COPY + \Deleted + expunge
                    GmailFolderResolver resolver = new GmailFolderResolver();
                    Folder trash = resolver.resolve(imap.getStore(), GmailFolderResolver.SpecialFolder.TRASH);
                    assertNotNull(trash);
                    assertTrue(trash.exists(), "Thư mục Thùng rác Gmail phải tồn tại");
                    inbox.copyMessages(new Message[]{target}, trash);
                    target.setFlag(Flags.Flag.DELETED, true);
                    inbox.expunge();

                    // 4. Xác minh thư biến mất khỏi INBOX
                    assertEquals(0, inbox.search(new SubjectTerm(marker)).length,
                            "Thư phải biến mất khỏi INBOX sau khi chuyển vào Thùng rác");

                    // 5. Xác minh thư đã xuất hiện trong Thùng rác rồi xóa vĩnh viễn (permanent)
                    trash.open(Folder.READ_WRITE);
                    try {
                        Message[] inTrash = trash.search(new SubjectTerm(marker));
                        assertTrue(inTrash.length >= 1, "Thư phải xuất hiện trong Thùng rác Gmail");
                        for (Message tm : inTrash) {
                            tm.setFlag(Flags.Flag.DELETED, true);
                        }
                        trash.expunge();
                        assertEquals(0, trash.search(new SubjectTerm(marker)).length,
                                "Thư phải bị xóa vĩnh viễn khỏi Thùng rác Gmail");
                    } finally {
                        if (trash.isOpen()) trash.close(true);
                    }
                    verifiedAndMoved = true;
                } finally {
                    if (inbox.isOpen()) inbox.close(true);
                }
            }
        }
        assertTrue(verifiedAndMoved, "Phải nhận được thư tự gửi trên Gmail thật, tải tệp khớp SHA-256, chuyển Trash và xóa vĩnh viễn");
    }
}
