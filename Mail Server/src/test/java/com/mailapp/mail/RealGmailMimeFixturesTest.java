package com.mailapp.mail;

import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.mail.service.HtmlSanitizerService;
import com.mailapp.mail.service.MailHeaderAndRateGuard;
import com.mailapp.mail.service.MailServiceImpl;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.MessageIDTerm;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PREP for manual Postman/Swing acceptance: sends synthetic samples only to
 * the explicitly confirmed test mailbox, verifies them through the production
 * read/download service and leaves them in place for a human to inspect.
 */
@EnabledIfEnvironmentVariable(named = "MAILAPP_REAL_GMAIL_DEDICATED", matches = "TEST-ACCOUNT")
@EnabledIfEnvironmentVariable(named = "MAILAPP_REAL_GMAIL_FIXTURES", matches = "KEEP-FIXTURES")
@EnabledIfEnvironmentVariable(named = "GMAIL_TEST_EMAIL", matches = ".+")
class RealGmailMimeFixturesTest {
    private record Attachment(String name, String type, byte[] data) {}
    private record Sample(String subject, String messageId, String text, String html,
                          List<Attachment> attachments) {}
    private record Location(long uid, long validity) {}

    @Test
    void prepareAndReadIndependentMimeSamples() {
        String marker = "MailApp-MIME-" + UUID.randomUUID();
        System.out.println("Synthetic MIME sample marker: " + marker);
        RealGmailIntegrationTest.safeStage("MIME sample preparation/read/download; marker=" + marker,
                () -> prepare(marker), marker);
    }

    private void prepare(String marker) throws Exception {
        String email = RealGmailIntegrationTest.testEmail();
        boolean app = "1".equals(System.getenv("MAILAPP_REAL_GMAIL"));
        boolean oauth = "1".equals(System.getenv("MAILAPP_REAL_GMAIL_XOAUTH2"));
        assertTrue(app ^ oauth, "Choose exactly one dedicated authentication mode");
        String secret = System.getenv(app ? "GMAIL_TEST_APP_PASSWORD" : "GMAIL_TEST_OAUTH2_ACCESS_TOKEN");
        assertTrue(secret != null && !secret.isBlank(), "Missing opt-in test credential");
        MailCredentials credentials = app ? MailCredentials.ofAppPassword(email, secret)
                : MailCredentials.ofOAuth2(email, secret);

        MailConnectionProperties props = new MailConnectionProperties();
        props.setRetryEnabled(false);
        props.setPoolEnabled(false);
        DefaultMailConnectionFactory live = new DefaultMailConnectionFactory(props,
                mock(AesCipher.class), mock(AccessTokenProvider.class), null);
        prepareAndVerifySamples(marker, credentials, live);
        System.out.println("Samples intentionally retained for manual Postman/Swing: " + marker);
    }

    // Shared with the independent GreenMail rehearsal. No environment or
    // external network is accessed by this method except via its caller's
    // explicitly provided mail transport.
    static void prepareAndVerifySamples(String marker, MailCredentials credentials,
                                        DefaultMailConnectionFactory live) throws Exception {
        String email = credentials.email();
        User owner = new User(1L, "mime-fixture", "fixture@local.test", Role.USER);
        GmailAccount account = new GmailAccount();
        account.setId(10L);
        account.setUser(owner);
        account.setEmail(email);
        account.setAuthType(credentials.authType());
        GmailAccountRepository repository = mock(GmailAccountRepository.class);
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        // Only ownership/storage are mocked; each service operation opens a
        // real TLS Gmail connection via the same production connection factory.
        MailConnectionFactory bridge = mock(MailConnectionFactory.class);
        when(bridge.openImap(account)).thenAnswer(call -> live.openImap(credentials));
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getMaxAttachmentSizeMb()).thenReturn(25);
        when(settings.getMailPageSize()).thenReturn(20);
        MailServiceImpl reader = new MailServiceImpl(repository, bridge, new GmailFolderResolver(), settings,
                mock(AuditLogService.class), new HtmlSanitizerService(), new MailHeaderAndRateGuard(30, 15));

        Attachment pdf = new Attachment("report.pdf", "application/pdf", smallPdf());
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pngBytes));
        Attachment png = new Attachment("image.png", "image/png", pngBytes.toByteArray());
        Attachment textFile = new Attachment("notes.txt", "text/plain",
                ("Sample bytes " + marker + "\n").getBytes(StandardCharsets.UTF_8));

        List<Sample> samples = new ArrayList<>();
        try (SmtpConnection smtp = live.openSmtp(credentials)) {
            for (String kind : List.of("plain", "html-only", "alternative", "pdf", "multiple", "utf8", "nested")) {
                String subject = marker + "-" + kind + ("utf8".equals(kind) ? "-Tiếng Việt" : "");
                String id = "<" + marker + "-" + kind + "@mailapp.test>";
                String text = "Sample body " + marker;
                String html = null;
                List<Attachment> attachments = List.of();
                MimeMessage message = smtp.createMimeMessage();
                message.setFrom(new InternetAddress(email));
                message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                message.setSubject(subject, "UTF-8");
                switch (kind) {
                    case "html-only" -> {
                        text = null;
                        html = "HTML sample " + marker;
                        message.setContent("<p>" + html + "</p>", "text/html; charset=UTF-8");
                    }
                    case "alternative" -> {
                        html = "Alternative HTML " + marker;
                        MimeMultipart alternative = new MimeMultipart("alternative");
                        alternative.addBodyPart(textPart(text));
                        MimeBodyPart rich = new MimeBodyPart();
                        rich.setContent("<p>" + html + "</p>", "text/html; charset=UTF-8");
                        alternative.addBodyPart(rich);
                        message.setContent(alternative);
                    }
                    case "pdf", "multiple" -> {
                        attachments = "pdf".equals(kind) ? List.of(pdf) : List.of(png, textFile);
                        message.setContent(mixed(text, attachments));
                    }
                    case "nested" -> {
                        attachments = List.of(textFile);
                        MimeMessage inner = new MimeMessage(smtp.getSession());
                        inner.setFrom(new InternetAddress(email));
                        inner.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                        inner.setSubject("Synthetic inner message", "UTF-8");
                        inner.setContent(mixed("Synthetic inner body", attachments));
                        inner.saveChanges();
                        MimeBodyPart embedded = new MimeBodyPart();
                        embedded.setContent(inner, "message/rfc822");
                        MimeMultipart outer = new MimeMultipart("mixed");
                        outer.addBodyPart(textPart(text));
                        outer.addBodyPart(embedded);
                        message.setContent(outer);
                    }
                    default -> {
                        if ("utf8".equals(kind)) text = "Tiếng Việt có dấu " + marker;
                        message.setText(text, "UTF-8");
                    }
                }
                message.saveChanges();
                message.setHeader("Message-ID", id);
                smtp.sendMessage(message);
                samples.add(new Sample(subject, id, text, html, attachments));
            }
        }

        Map<Sample, Location> locations = new LinkedHashMap<>();
        for (int attempt = 0; attempt < 15 && locations.size() < samples.size(); attempt++) {
            try (ImapConnection imap = live.openImap(credentials)) {
                Folder inbox = imap.folder("INBOX");
                inbox.open(Folder.READ_ONLY);
                try {
                    assertTrue(inbox instanceof UIDFolder);
                    UIDFolder uids = (UIDFolder) inbox;
                    for (Sample sample : samples) {
                        if (locations.containsKey(sample)) continue;
                        Message[] matches = inbox.search(new MessageIDTerm(sample.messageId()));
                        if (matches.length == 0) continue;
                        assertEquals(1, matches.length, "Ambiguous synthetic sample identity");
                        Message candidate = matches[0];
                        String[] ids = candidate.getHeader("Message-ID");
                        assertTrue(ids != null && ids.length == 1 && sample.messageId().equals(ids[0]));
                        assertTrue(sample.subject().equals(candidate.getSubject()));
                        assertTrue(uids.getUID(candidate) > 0 && uids.getUIDValidity() > 0);
                        locations.put(sample, new Location(uids.getUID(candidate), uids.getUIDValidity()));
                    }
                } finally {
                    inbox.close(false);
                }
            }
            if (locations.size() < samples.size()) Thread.sleep(2000);
        }
        assertEquals(samples.size(), locations.size(), "All independent samples must reach Inbox");
        int verified = 0;
        for (Sample sample : samples) {
            Location location = locations.get(sample);
            var detail = reader.getMailDetail(owner, 10L, "INBOX", location.uid(),
                    false, location.validity(), false);
            assertTrue(sample.messageId().equals(detail.messageId()) && sample.subject().equals(detail.subject()));
            assertTrue(sample.text() == null || detail.textBody().contains(sample.text()));
            assertTrue(sample.html() == null || detail.htmlBody().contains(sample.html()));
            assertEquals(sample.attachments().size(), detail.attachments().size());
            for (int i = 0; i < sample.attachments().size(); i++) {
                Attachment expected = sample.attachments().get(i);
                var meta = detail.attachments().get(i);
                assertTrue(meta.index() == i && expected.name().equals(meta.filename())
                        && expected.type().equalsIgnoreCase(meta.contentType()));
                ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
                try (var handle = reader.prepareAttachmentDownload(owner, 10L, "INBOX", location.uid(),
                        i, location.validity())) {
                    handle.streamer().streamTo(downloaded);
                }
                assertTrue(Arrays.equals(expected.data(), downloaded.toByteArray()), "Exact decoded attachment bytes");
            }
            // Metadata for later, separately confirmed manual cleanup.
            // Never delete from this preparation step: the seven samples
            // must remain available for Postman/Swing, and UIDs are scoped
            // to INBOX + UIDVALIDITY. Re-resolve identity before any move.
            System.out.println("Verified synthetic MIME sample " + (++verified) + "/" + samples.size()
                    + " folder=INBOX uid=" + location.uid() + " uidValidity=" + location.validity()
                    + " attachments=" + sample.attachments().size());
        }
    }

    private static MimeBodyPart textPart(String text) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setText(text, "UTF-8");
        return part;
    }

    private static MimeMultipart mixed(String text, List<Attachment> attachments) throws MessagingException {
        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(textPart(text));
        for (Attachment attachment : attachments) {
            MimeBodyPart part = new MimeBodyPart();
            part.setDataHandler(new DataHandler(new ByteArrayDataSource(attachment.data(), attachment.type())));
            part.setFileName(attachment.name());
            part.setDisposition(Part.ATTACHMENT);
            part.setHeader("Content-Transfer-Encoding", "base64");
            mixed.addBodyPart(part);
        }
        return mixed;
    }

    // A real one-page PDF with byte-accurate xref offsets.
    private static byte[] smallPdf() {
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
        for (int offset : offsets) pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        pdf.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
