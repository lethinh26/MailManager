package com.mailapp.mail;

import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.AuthType;
import com.mailapp.model.AccountStatus;
import com.mailapp.security.AesCipher;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.MessageIDTerm;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Kiểm thử tích hợp kết nối thật tới máy chủ Google Gmail (IMAP over TLS & SMTP STARTTLS).
 * Chỉ chạy khi môi trường có biến GMAIL_TEST_EMAIL và GMAIL_TEST_APP_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "MAILAPP_REAL_GMAIL", matches = "1")
@EnabledIfEnvironmentVariable(named = "MAILAPP_REAL_GMAIL_DEDICATED", matches = "TEST-ACCOUNT")
@EnabledIfEnvironmentVariable(named = "GMAIL_TEST_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GMAIL_TEST_APP_PASSWORD", matches = ".+")
class RealGmailIntegrationTest {

    @Test
    @DisplayName("Kết nối thật tới Gmail IMAPS/SMTP: Xác thực, đếm INBOX và phân giải folders")
    void testRealGmailImap() throws Exception {
        safeStage("App Password authentication", () ->
                verifyAuthentication(MailCredentials.ofAppPassword(testEmail(), System.getenv("GMAIL_TEST_APP_PASSWORD"))));
    }

    static void verifyAuthentication(MailCredentials credentials) throws Exception {
        MailConnectionProperties properties = new MailConnectionProperties();
        properties.setPoolEnabled(false);
        properties.setRetryEnabled(false);
        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

        ConnectionTestResult testResult = factory.testImap(credentials);
        assertTrue(testResult.isSuccessful(), "Thử nghiệm IMAP tới Gmail phải thành công: " + testResult.message());

        try (ImapConnection imap = factory.openImap(credentials)) {
            assertTrue(imap.isConnected());

            Folder inbox = imap.folder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int messageCount = inbox.getMessageCount();
            assertTrue(messageCount >= 0);
            // Do not inspect any pre-existing message, even on a dedicated account.
            inbox.close(false);

            GmailFolderResolver folderResolver = new GmailFolderResolver();
            Folder sent = folderResolver.resolve(imap.getStore(), GmailFolderResolver.SpecialFolder.SENT);
            assertNotNull(sent);
            assertTrue(sent.exists());
        }
        try (SmtpConnection smtp = factory.openSmtp(credentials)) {
            assertTrue(smtp.isConnected());
        }
        if (credentials.authType() == AuthType.APP_PASSWORD) {
            // AM-4396 requires a standalone Store.connect, not just the
            // AutoCloseable business wrapper. This is inside the existing
            // dedicated-account opt-in and safeStage, with no application
            // session, repository or new credential source.
            verifyRawAppPassword(credentials, properties);
        }
    }

    static void verifyRawAppPassword(MailCredentials credentials,
                                     MailConnectionProperties properties) throws Exception {
        assertEquals(AuthType.APP_PASSWORD, credentials.authType());
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        AesCipher cipher = new AesCipher(Base64.getEncoder().encodeToString(key));
        java.util.Arrays.fill(key, (byte) 0);
        GmailAccount account = new GmailAccount();
        account.setEmail(credentials.email());
        account.setAuthType(AuthType.APP_PASSWORD);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAppPasswordEncrypted(cipher.encrypt(credentials.secret()));
        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties, cipher, null, null);
        try {
            Store store = factory.createImapStore(account);
            try (store) {
                assertFalse(store.isConnected());
                store.connect();
                assertTrue(store.isConnected());
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);
                try {
                    assertTrue(inbox.getMessageCount() >= 0 && inbox.getUnreadMessageCount() >= 0);
                } finally {
                    if (inbox.isOpen()) inbox.close(false);
                }
            }
            assertFalse(store.isConnected());
            Transport transport = factory.createSmtpTransport(account);
            try (transport) {
                assertFalse(transport.isConnected());
                transport.connect();
                assertTrue(transport.isConnected());
            }
            assertFalse(transport.isConnected());
        } finally {
            factory.shutdownPool();
        }
    }

    @Test
    @DisplayName("Vòng đời thật Gmail: Gửi cho chính mình kèm 1 file, tải lại file, so SHA-256, xóa vào Thùng rác")
    void testRealGmailSmtpSendAttachmentVerifySha256AndTrash() throws Exception {
        safeStage("App Password synthetic lifecycle", () ->
                verifyLifecycle(MailCredentials.ofAppPassword(testEmail(), System.getenv("GMAIL_TEST_APP_PASSWORD"))));
    }

    static void verifyLifecycle(MailCredentials credentials) throws Exception {
        String email = credentials.email();
        MailConnectionProperties properties = new MailConnectionProperties();
        properties.setPoolEnabled(false);
        properties.setRetryEnabled(false);
        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

        String marker = "MailManager-E2E-" + UUID.randomUUID();
        String messageId = "<" + marker + "@mailapp.test>";
        System.out.println("Synthetic Gmail lifecycle marker: " + marker);
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
            filePart.setHeader("Content-Transfer-Encoding", "base64");
            mixed.addBodyPart(filePart);

            message.setContent(mixed);
            message.saveChanges();
            message.setHeader("Message-ID", messageId);
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
                    Message[] found = inbox.search(new MessageIDTerm(messageId));
                    if (found.length == 0) continue;

                    java.util.List<Message> exact = java.util.Arrays.stream(found).filter(m -> {
                        try { return ("[TEST] " + marker).equals(m.getSubject())
                                && hasExactMessageId(m, messageId); }
                        catch (MessagingException e) { return false; }
                    }).toList();
                    assertEquals(1, exact.size(), "Chỉ thao tác trên đúng thư thử nghiệm duy nhất");
                    Message target = exact.getFirst();
                    assertTrue(((InternetAddress) target.getFrom()[0]).getAddress().equalsIgnoreCase(email));
                    assertTrue(inbox instanceof UIDFolder && ((UIDFolder) inbox).getUID(target) > 0);
                    assertTrue(inbox instanceof org.eclipse.angus.mail.imap.IMAPFolder
                            && imap.getStore() instanceof org.eclipse.angus.mail.imap.IMAPStore is
                            && is.hasCapability("UIDPLUS"), "Cần UIDPLUS để chỉ expunge thư test");
                    boolean shaChecked = false;
                    Object content = target.getContent();
                    if (content instanceof Multipart mp) {
                        for (int i = 0; i < mp.getCount(); i++) {
                            BodyPart bp = mp.getBodyPart(i);
                            if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                                try (InputStream is = bp.getInputStream()) {
                                    byte[] actualBytes = is.readAllBytes();
                                    assertArrayEquals(payloadBytes, actualBytes, "Exact decoded attachment bytes");
                                    byte[] actualSha = MessageDigest.getInstance("SHA-256").digest(actualBytes);
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
                    ((org.eclipse.angus.mail.imap.IMAPFolder) inbox).expunge(new Message[]{target});

                    // 4. Xác minh thư biến mất khỏi INBOX
                    assertEquals(0, inbox.search(new MessageIDTerm(messageId)).length,
                            "Thư phải biến mất khỏi INBOX sau khi chuyển vào Thùng rác");

                    // 5. Xác minh thư đã xuất hiện trong Thùng rác rồi xóa vĩnh viễn (permanent)
                    trash.open(Folder.READ_WRITE);
                    try {
                        Message[] inTrash = trash.search(new MessageIDTerm(messageId));
                        assertEquals(1, inTrash.length, "Chỉ xóa đúng thư test trong Trash");
                        assertEquals("[TEST] " + marker, inTrash[0].getSubject());
                        assertTrue(hasExactMessageId(inTrash[0], messageId));
                        assertTrue(trash instanceof UIDFolder && ((UIDFolder) trash).getUID(inTrash[0]) > 0);
                        assertTrue(trash instanceof org.eclipse.angus.mail.imap.IMAPFolder);
                        inTrash[0].setFlag(Flags.Flag.DELETED, true);
                        ((org.eclipse.angus.mail.imap.IMAPFolder) trash).expunge(inTrash);
                        assertEquals(0, trash.search(new MessageIDTerm(messageId)).length,
                                "Thư phải bị xóa vĩnh viễn khỏi Thùng rác Gmail");
                    } finally {
                        if (trash.isOpen()) trash.close(false);
                    }
                    verifiedAndMoved = true;
                } finally {
                    if (inbox.isOpen()) inbox.close(false);
                }
            }
        }
        assertTrue(verifiedAndMoved, "Phải nhận được thư tự gửi trên Gmail thật, tải tệp khớp SHA-256, chuyển Trash và xóa vĩnh viễn");
    }

    private static boolean hasExactMessageId(Message message, String expected) throws MessagingException {
        String[] ids = message.getHeader("Message-ID");
        return ids != null && ids.length == 1 && expected.equals(ids[0]);
    }

    static String testEmail() {
        String email = System.getenv("GMAIL_TEST_EMAIL");
        assertTrue(email != null && email.matches("(?i)[a-z0-9.]+@gmail\\.com"),
                "Use a dedicated canonical Gmail address without aliases.");
        return email.toLowerCase(java.util.Locale.ROOT);
    }

    @FunctionalInterface
    interface CheckedAction { void run() throws Exception; }

    /**
     * Live suites run serially. Inspect logs in RAM and never put raw
     * provider replies/throwables or credentials into the JUnit XML.
     */
    static void safeStage(String stage, CheckedAction action, String... syntheticLogCanaries) {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> original = new ArrayList<>();
        root.iteratorForAppenders().forEachRemaining(original::add);
        original.forEach(root::detachAppender);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        String failureType = null;
        boolean leaked = false;
        try {
            try {
                action.run();
            } catch (AssertionError | Exception e) {
                failureType = e.getClass().getSimpleName();
            }
            for (ILoggingEvent event : captured.list) {
                String message = event.getFormattedMessage();
                if (event.getThrowableProxy() != null) message += ThrowableProxyUtil.asString(event.getThrowableProxy());
                for (String key : List.of("GMAIL_TEST_EMAIL", "GMAIL_TEST_APP_PASSWORD",
                        "GMAIL_TEST_OAUTH2_ACCESS_TOKEN", "GMAIL_TEST_OAUTH_CLIENT_SECRET")) {
                    String secret = System.getenv(key);
                    if (secret != null && !secret.isBlank() && message.contains(secret)) leaked = true;
                }
                for (String canary : syntheticLogCanaries) {
                    if (canary != null && !canary.isBlank() && message.contains(canary)) leaked = true;
                }
            }
        } finally {
            root.detachAppender(captured);
            captured.stop();
            captured.list.clear();
            original.forEach(root::addAppender);
        }
        assertFalse(leaked, "Live provider log contained an unmasked sensitive value (details suppressed)");
        assertNull(failureType, "Live Gmail stage failed: " + stage + " (provider details suppressed)");
        System.out.println("Verified live stage and in-memory log privacy: " + stage);
    }
}
