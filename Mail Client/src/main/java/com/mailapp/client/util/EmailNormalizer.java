package com.mailapp.client.util;

import java.util.Locale;

/**
 * Hàm chuẩn hóa khóa email dùng chung giữa Mail Client và Mail Server.
 * Phải giữ thuật toán giống hệt com.mailapp.util.EmailNormalizer trên Server:
 * - Bỏ khoảng trắng hai đầu và hạ toàn bộ sang chữ thường.
 * - Với gmail.com / googlemail.com: bỏ "+tag" và dấu chấm '.' ở phần local, quy đổi googlemail.com -> gmail.com.
 * - Với tên miền khác (Google Workspace, doanh nghiệp...): chỉ hạ chữ thường và bỏ khoảng trắng.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

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
}
