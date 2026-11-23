package com.mailapp.mail.service;

import jakarta.mail.internet.MimeUtility;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Tiện ích làm sạch tên tệp đính kèm chống Path Traversal (../../), ký tự điều khiển,
 * giải mã RFC 2047 / RFC 2231 và tạo header Content-Disposition an toàn chuẩn RFC 5987.
 */
public final class FilenameSanitizer {

    private static final int MAX_FILENAME_LENGTH = 180;

    private FilenameSanitizer() {}

    public static String sanitize(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "attachment.bin";
        }

        String decoded = rawFilename;
        try {
            decoded = MimeUtility.decodeText(rawFilename);
        } catch (Exception ignored) {}

        // Loại bỏ ký tự điều khiển (ASCII 0x00..0x1F, 0x7F) và CRLF
        String cleaned = decoded.replaceAll("[\\x00-\\x1F\\x7F]", "");

        // Chống path traversal: chỉ lấy phần tên tệp sau dấu / hoặc \ cuối cùng
        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }

        // Xóa chuỗi ".." và các ký tự bất hợp lệ trên Windows/POSIX
        cleaned = cleaned.replace("..", "_");
        cleaned = cleaned.replaceAll("[<>:\"/\\\\|?*]", "_").trim();

        // Xóa dấu chấm hoặc khoảng trắng thừa ở đầu/cuối
        while (cleaned.startsWith(".") || cleaned.startsWith(" ")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        if (cleaned.isEmpty() || cleaned.matches("^[._\\s]+$")) {
            return "attachment.bin";
        }

        // Chặn tên thiết bị dành riêng của Windows (CON, PRN, AUX, NUL, COM1..9, LPT1..9 — có hoặc không có phần mở rộng)
        int firstDot = cleaned.indexOf('.');
        String baseName = firstDot >= 0 ? cleaned.substring(0, firstDot) : cleaned;
        if (baseName.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")) {
            cleaned = "file_" + cleaned;
        }

        // Cắt độ dài tối đa nhưng vẫn giữ phần mở rộng
        if (cleaned.length() > MAX_FILENAME_LENGTH) {
            int dot = cleaned.lastIndexOf('.');
            if (dot > 0 && cleaned.length() - dot <= 15) {
                String ext = cleaned.substring(dot);
                cleaned = cleaned.substring(0, MAX_FILENAME_LENGTH - ext.length()) + ext;
            } else {
                cleaned = cleaned.substring(0, MAX_FILENAME_LENGTH);
            }
        }

        return cleaned;
    }

    /**
     * Xây dựng header Content-Disposition chuẩn RFC 5987 an toàn cho mọi trình duyệt và HTTP Client.
     */
    public static String buildContentDisposition(String sanitizedFilename) {
        String safe = sanitize(sanitizedFilename);
        String asciiFallback = safe.replaceAll("[^\\x20-\\x7E]", "_")
                .replace("\"", "_")
                .replace("\\", "_");
        if (asciiFallback.isBlank()) {
            asciiFallback = "attachment.bin";
        }
        String rfc5987 = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + rfc5987;
    }

    /**
     * Trả Content-Type an toàn khi tải tệp (ngăn thực thi XSS nếu tệp là HTML/SVG/JS).
     */
    public static String safeDownloadContentType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return "application/octet-stream";
        }
        String base = rawContentType.split(";")[0].trim().toLowerCase();
        if (base.contains("html") || base.contains("svg") || base.contains("javascript")
                || base.contains("ecmascript") || base.contains("xml")) {
            return "application/octet-stream";
        }
        return base;
    }
}
