package com.mailapp.mail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.dto.mail.MailDtos.SendMailRequest;
import com.mailapp.dto.mail.MailDtos.UpdateFlagsRequest;
import com.mailapp.model.GmailAccount;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MailMessageBuilderTest {
    private final Session session = Session.getInstance(new Properties());
    private final HtmlSanitizerService sanitizer = new HtmlSanitizerService();

    @Test
    void plainTextAndUtf8HeadersSurviveMimeRoundTrip() throws Exception {
        MimeMessage parsed = roundTrip(request("Xin chào", "Nội dung tiếng Việt", null), List.of());
        assertEquals("Xin chào", parsed.getSubject());
        assertEquals("Nội dung tiếng Việt", parsed.getContent().toString().trim());
        assertEquals("to@example.test", parsed.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("cc@example.test", parsed.getRecipients(Message.RecipientType.CC)[0].toString());
        assertEquals("bcc@example.test", parsed.getRecipients(Message.RecipientType.BCC)[0].toString());
    }

    @Test
    void htmlOnlyCreatesPlainAlternativeWithoutUnsafeScript() throws Exception {
        MimeMessage parsed = roundTrip(request("HTML", null, "<p>Hello</p><script>bad()</script>"), List.of());
        assertTrue(parsed.isMimeType("multipart/alternative"));
        Multipart parts = (Multipart) parsed.getContent();
        assertEquals(2, parts.getCount());
        assertEquals("Hello", parts.getBodyPart(0).getContent().toString().trim());
        String html = parts.getBodyPart(1).getContent().toString();
        assertTrue(html.contains("Hello"));
        assertFalse(html.contains("bad()"));
    }

    @Test
    void pdfAttachmentAndTextBodyRoundTrip() throws Exception {
        byte[] pdf = "%PDF-1.4\nfixture\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MimeMessage parsed = roundTrip(request("PDF", "report", null),
                List.of(file("report.pdf", "application/pdf", pdf)));
        Multipart parts = (Multipart) parsed.getContent();
        assertTrue(parsed.isMimeType("multipart/mixed"));
        assertEquals("report", parts.getBodyPart(0).getContent().toString().trim());
        assertAttachment(parts.getBodyPart(1), "report.pdf", "application/pdf", pdf);
    }

    @Test
    void nestedAlternativeAndMultiplePngTextAndEmptyAttachmentsRoundTrip() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW3sAAAAASUVORK5CYII=");
        byte[] text = "Tiếng Việt".getBytes(StandardCharsets.UTF_8);
        MimeMessage parsed = roundTrip(request("Multipart", "plain", "<p>rich</p>"),
                List.of(file("image.png", "image/png", png), file("ghi-chú.txt", "text/plain", text),
                        file("empty.txt", "text/plain", new byte[0])));
        Multipart parts = (Multipart) parsed.getContent();
        assertEquals(4, parts.getCount());
        assertTrue(parts.getBodyPart(0).isMimeType("multipart/alternative"));
        assertAttachment(parts.getBodyPart(1), "image.png", "image/png", png);
        assertAttachment(parts.getBodyPart(2), "ghi-chú.txt", "text/plain", text);
        assertAttachment(parts.getBodyPart(3), "empty.txt", "text/plain", new byte[0]);
    }

    @Test
    void acceptsLegacyStarredButKeepsFlaggedWireContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UpdateFlagsRequest legacy = mapper.readValue("{\"starred\":true}", UpdateFlagsRequest.class);
        assertEquals(Boolean.TRUE, legacy.flagged());
        assertNull(legacy.seen());
        assertTrue(mapper.writeValueAsString(legacy).contains("\"flagged\":true"));
    }

    private SendMailRequest request(String subject, String text, String html) {
        return new SendMailRequest(1L, List.of("to@example.test"), List.of("cc@example.test"),
                List.of("bcc@example.test"), subject, text, html, null, null);
    }

    private MimeMessage roundTrip(SendMailRequest request, List<MultipartFile> files) throws Exception {
        GmailAccount account = new GmailAccount();
        account.setEmail("sender@example.test");
        account.setDisplayName("Người gửi");
        MimeMessage message = new MimeMessage(session);
        MailMessageBuilder.build(message, account, request, files, sanitizer);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        message.writeTo(encoded);
        return new MimeMessage(session, new ByteArrayInputStream(encoded.toByteArray()));
    }

    private static MultipartFile file(String filename, String type, byte[] bytes) {
        return new MockMultipartFile("attachments", filename, type, bytes);
    }

    private static void assertAttachment(Part part, String name, String type, byte[] bytes) throws Exception {
        assertEquals(Part.ATTACHMENT, part.getDisposition());
        assertTrue(part.isMimeType(type));
        assertEquals(name, jakarta.mail.internet.MimeUtility.decodeText(part.getFileName()));
        try (var input = part.getInputStream()) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
    }
}
