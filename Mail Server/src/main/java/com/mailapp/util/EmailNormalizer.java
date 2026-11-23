package com.mailapp.util;

import java.util.Locale;

/**
 * Hàm chuẩn hóa khóa email dùng chung giữa Mail Server và Mail Client.
 * Quy tắc:
 * - Bỏ khoảng trắng hai đầu và hạ toàn bộ sang chữ thường.
 * - Với tên miền gmail.com hoặc googlemail.com:
 *   + Bỏ phần "+tag" ở phần local (từ dấu '+' đầu tiên trở đi).
 *   + Bỏ toàn bộ dấu chấm '.' ở phần local.
 *   + Quy đổi googlemail.com thành gmail.com.
 * - Với các tên miền khác (Google Workspace, công ty, trường học...):
 *   + Chỉ hạ chữ thường và bỏ khoảng trắng, giữ nguyên dấu chấm và "+tag".
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * Chuẩn hóa email thành email_key dùng để chống trùng hộp thư theo từng người dùng.
     */
    public static String normalizeKey(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        String trimmed = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }

        int atIdx = trimmed.lastIndexOf('@');
        if (atIdx <= 0 || atIdx == trimmed.length() - 1) {
            return trimmed;
        }

        String local = trimmed.substring(0, atIdx);
        String domain = trimmed.substring(atIdx + 1);

        if ("gmail.com".equals(domain) || "googlemail.com".equals(domain)) {
            int plusIdx = local.indexOf('+');
            if (plusIdx >= 0) {
                local = local.substring(0, plusIdx);
            }
            local = local.replace(".", "");
            domain = "gmail.com";
        }

        return local + "@" + domain;
    }

    /**
     * Chuẩn hóa nhẹ email để hiển thị: giữ nguyên dạng gốc người dùng/Google cung cấp, chỉ cắt khoảng trắng.
     */
    public static String normalizeDisplayEmail(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim();
    }
}
