package com.mailapp.client.util;

import com.mailapp.client.model.MailClientDtos.MailAddressDto;
import com.mailapp.client.model.MailClientDtos.MailDetailResponse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared preflight checks for the compose form and its background send operation. */
public final class ComposeValidation {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>\"',;:\\r\\n]+@[^\\s@<>\"',;:\\r\\n]+\\.[^\\s@<>\"',;:\\r\\n]+$");
    private ComposeValidation() {}

    public static String safeLinkHref(String value) {
        java.net.URI uri = java.net.URI.create(value.trim());
        if (uri.getHost() == null || uri.getUserInfo() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
            throw new IllegalArgumentException("Liên kết phải là HTTP hoặc HTTPS hợp lệ.");
        return uri.toASCIIString().replace("&", "&amp;").replace("\"", "&quot;");
    }

    /** Detect ordinary edits/replacements between selection and upload preflight.
     * This is not a filesystem lock or a cryptographic content guarantee. */
    public record AttachmentStamp(long size, FileTime modified, Object fileKey) {
        public static AttachmentStamp inspect(File file) throws IOException {
            if (file == null) throw new IOException("Tệp đính kèm không hợp lệ.");
            BasicFileAttributes a = Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!a.isRegularFile() || !Files.isReadable(file.toPath())) throw new IOException("Không đọc được tệp đính kèm.");
            return new AttachmentStamp(a.size(), a.lastModifiedTime(), a.fileKey());
        }
        public boolean matches(File file) {
            try { return equals(inspect(file)); }
            catch (IOException | RuntimeException e) { return false; }
        }
    }

    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value).matches();
    }

    /** Reply-To takes precedence; never copy Bcc or add the selected sender twice. */
    public record ReplyPlan(List<String> to, List<String> cc, String inReplyTo, String references) {}

    public static ReplyPlan replyPlan(MailDetailResponse mail, String ownEmail, boolean all) {
        var seen = new LinkedHashSet<String>();
        String ownKey = EmailNormalizer.normalizeKey(ownEmail);
        if (ownEmail != null) seen.add(ownEmail.trim().toLowerCase(Locale.ROOT));
        List<String> to = new ArrayList<>();
        List<String> cc = new ArrayList<>();
        List<MailAddressDto> targets = mail.replyTo();
        if (targets == null || targets.stream().noneMatch(a -> a != null && isEmail(a.address()))) {
            targets = mail.from() == null ? List.of() : List.of(mail.from());
        }
        addReplyRecipients(to, targets, seen, ownKey);
        if (all) {
            addReplyRecipients(cc, mail.to(), seen, ownKey);
            addReplyRecipients(cc, mail.cc(), seen, ownKey);
            if (to.isEmpty() && !cc.isEmpty()) to.add(cc.removeFirst());
        }
        String messageId = safeMessageId(mail.messageId());
        var chain = new LinkedHashSet<String>();
        if (mail.references() != null) {
            for (String reference : mail.references().trim().split("\\s+")) {
                String id = safeMessageId(reference);
                if (id != null) chain.add(id);
            }
        }
        if (messageId != null) chain.add(messageId);
        return new ReplyPlan(List.copyOf(to), List.copyOf(cc), messageId,
                chain.isEmpty() ? null : String.join(" ", chain));
    }

    private static void addReplyRecipients(List<String> result, List<MailAddressDto> addresses, java.util.Set<String> seen, String ownKey) {
        if (addresses == null) return;
        for (MailAddressDto a : addresses) {
            if (a == null || !isEmail(a.address())) continue;
            if (ownKey.equals(EmailNormalizer.normalizeKey(a.address()))) continue;
            if (seen.add(a.address().toLowerCase(Locale.ROOT))) result.add(a.address());
        }
    }

    private static String safeMessageId(String value) {
        if (value == null) return null;
        String id = value.trim();
        return id.matches("<[^<>\\s\\p{Cntrl}]+@[^<>\\s\\p{Cntrl}]+>") ? id : null;
    }

    /** HTML-only messages still have a quoted body; parsing text does not fetch resources. */
    public static String quoteText(MailDetailResponse mail) {
        if (mail.textBody() != null && !mail.textBody().isBlank()) return mail.textBody();
        if (mail.htmlBody() == null || mail.htmlBody().isBlank()) return "";
        StringBuilder text = new StringBuilder();
        try {
            new javax.swing.text.html.parser.ParserDelegator().parse(new java.io.StringReader(mail.htmlBody()),
                    new javax.swing.text.html.HTMLEditorKit.ParserCallback() {
                        @Override public void handleText(char[] data, int pos) { text.append(data); }
                        @Override public void handleSimpleTag(javax.swing.text.html.HTML.Tag tag, javax.swing.text.MutableAttributeSet attrs, int pos) {
                            if (tag == javax.swing.text.html.HTML.Tag.BR) text.append('\n');
                        }
                        @Override public void handleEndTag(javax.swing.text.html.HTML.Tag tag, int pos) {
                            if (tag == javax.swing.text.html.HTML.Tag.P || tag == javax.swing.text.html.HTML.Tag.DIV
                                    || tag == javax.swing.text.html.HTML.Tag.LI || tag == javax.swing.text.html.HTML.Tag.TR) text.append('\n');
                        }
                    }, true);
        } catch (IOException ignored) {
            return "";
        }
        return text.toString().strip();
    }

    public static boolean isReadableRegularFile(File file) {
        try {
            return file != null && java.nio.file.Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && java.nio.file.Files.isReadable(file.toPath());
        } catch (SecurityException | java.nio.file.InvalidPathException ex) {
            return false;
        }
    }

    public static List<String> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        if (raw.indexOf('\r') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Địa chỉ email chứa ký tự không hợp lệ.");
        }
        List<String> result = new ArrayList<>();
        for (String part : raw.trim().split("[,;\\s]+")) {
            if (part.isBlank()) continue;
            if (!isEmail(part)) throw new IllegalArgumentException("Địa chỉ email không hợp lệ: " + part);
            result.add(part);
        }
        return result;
    }

    public static String validate(List<String> to, List<String> cc, List<String> bcc,
                                  String subject, String body, List<File> files, int maxAttachmentMb) {
        if (to == null || to.isEmpty()) return "Vui lòng nhập ít nhất một địa chỉ người nhận (Đến).";
        int count = 0;
        for (List<String> group : List.of(to, cc == null ? List.<String>of() : cc, bcc == null ? List.<String>of() : bcc)) {
            count += group.size();
            for (String address : group) if (!isEmail(address)) return "Địa chỉ email không hợp lệ.";
        }
        if (count > 50) return "Tối đa 50 địa chỉ người nhận.";
        if (subject != null && (subject.codePointCount(0, subject.length()) > 200 || subject.indexOf('\r') >= 0
                || subject.indexOf('\n') >= 0 || subject.indexOf('\0') >= 0)) {
            return "Tiêu đề không được vượt 200 ký tự hoặc chứa ký tự điều khiển.";
        }
        if (body != null && body.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            return "Nội dung thư không được vượt quá 1 MB.";
        }
        long total = 0;
        long limit = (long) (maxAttachmentMb > 0 ? maxAttachmentMb : 25) * 1024 * 1024;
        if (files != null) for (File file : files) {
            if (!isReadableRegularFile(file)) return "Tệp đính kèm không còn tồn tại, không đọc được hoặc là liên kết.";
            long size = file.length();
            if (size > limit - total) return "Tổng dung lượng tệp đính kèm vượt giới hạn cho phép.";
            total += size;
        }
        return null;
    }
}
