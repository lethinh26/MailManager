package com.mailapp.mail.service;

import com.mailapp.dto.mail.MailDtos.SendMailRequest;
import com.mailapp.model.GmailAccount;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/** Build MIME independently of the SMTP transport so it can be round-trip tested. */
final class MailMessageBuilder {
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private MailMessageBuilder() {}

    static void build(MimeMessage mime, GmailAccount account, SendMailRequest request,
                      List<MultipartFile> attachments, HtmlSanitizerService sanitizer) throws Exception {
        String displayName = account.getDisplayName() != null && !account.getDisplayName().isBlank()
                ? account.getDisplayName() : account.getEmail();
        mime.setFrom(new InternetAddress(account.getEmail(), displayName, UTF8));
        addRecipients(mime, Message.RecipientType.TO, request.to());
        addRecipients(mime, Message.RecipientType.CC, request.cc());
        addRecipients(mime, Message.RecipientType.BCC, request.bcc());
        mime.setSubject(request.subject() == null ? "" : request.subject(), UTF8);
        mime.setSentDate(new Date());
        if (request.inReplyTo() != null && !request.inReplyTo().isBlank()) {
            mime.setHeader("In-Reply-To", request.inReplyTo().trim());
        }
        if (request.references() != null && !request.references().isBlank()) {
            mime.setHeader("References", request.references().trim());
        }

        String text = request.textBody() == null ? "" : request.textBody();
        String html = request.htmlBody() != null && !request.htmlBody().isBlank()
                ? sanitizer.sanitizeOutgoing(request.htmlBody()).html() : null;
        boolean hasFiles = attachments != null && attachments.stream().anyMatch(MailMessageBuilder::isAttachment);
        MimeMultipart alternative = html == null ? null : alternative(text, html, sanitizer);
        if (!hasFiles) {
            if (alternative == null) mime.setText(text, UTF8);
            else mime.setContent(alternative);
        } else {
            MimeMultipart mixed = new MimeMultipart("mixed");
            MimeBodyPart body = new MimeBodyPart();
            if (alternative == null) body.setText(text, UTF8);
            else body.setContent(alternative);
            mixed.addBodyPart(body);
            for (MultipartFile file : attachments) {
                if (!isAttachment(file)) continue;
                String name = FilenameSanitizer.sanitize(file.getOriginalFilename());
                String type = safeMimeType(file.getContentType());
                MimeBodyPart part = new MimeBodyPart();
                // MultipartFile is available for the entire synchronous send;
                // the DataSource reopens its stream when JavaMail needs it.
                part.setDataHandler(new DataHandler(new DataSource() {
                    @Override public InputStream getInputStream() throws IOException { return file.getInputStream(); }
                    @Override public OutputStream getOutputStream() throws IOException { throw new IOException("Read-only attachment"); }
                    @Override public String getContentType() { return type; }
                    @Override public String getName() { return name; }
                }));
                part.setFileName(MimeUtility.encodeText(name, UTF8, "B"));
                part.setDisposition(Part.ATTACHMENT);
                // SMTP canonicalizes line endings in unencoded text. Even an
                // ASCII PDF or text attachment is opaque file data: changing
                // LF/CRLF can invalidate offsets or alter the original bytes.
                // Explicit base64 also protects trailing newlines at MIME
                // boundaries, independently of JavaMail's encoding heuristic.
                part.setHeader("Content-Transfer-Encoding", "base64");
                mixed.addBodyPart(part);
            }
            mime.setContent(mixed);
        }
        mime.saveChanges();
    }

    static boolean isAttachment(MultipartFile file) {
        return file != null && (!file.isEmpty()
                || (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()));
    }

    private static MimeMultipart alternative(String text, String html, HtmlSanitizerService sanitizer)
            throws MessagingException {
        MimeMultipart alt = new MimeMultipart("alternative");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText(text.isBlank() ? sanitizer.htmlToPlainText(html) : text, UTF8);
        MimeBodyPart rich = new MimeBodyPart();
        rich.setContent(html, "text/html; charset=UTF-8");
        alt.addBodyPart(plain);
        alt.addBodyPart(rich);
        return alt;
    }

    private static String safeMimeType(String raw) {
        if (raw == null || raw.isBlank() || raw.indexOf('\r') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\0') >= 0) {
            return "application/octet-stream";
        }
        try {
            String base = new ContentType(raw).getBaseType().toLowerCase(java.util.Locale.ROOT);
            // A caller cannot supply a MIME container and force JavaMail to
            // treat arbitrary file bytes as an embedded message/multipart.
            if (base.startsWith("multipart/") || base.startsWith("message/")) return "application/octet-stream";
            return base;
        } catch (MessagingException e) {
            return "application/octet-stream";
        }
    }

    private static void addRecipients(MimeMessage mime, Message.RecipientType type, List<String> addresses)
            throws MessagingException {
        if (addresses == null) return;
        for (String address : addresses) {
            mime.addRecipient(type, new InternetAddress(address.trim(), true));
        }
    }
}
