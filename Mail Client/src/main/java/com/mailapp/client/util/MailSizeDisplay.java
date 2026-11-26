package com.mailapp.client.util;

import com.mailapp.client.model.MailClientDtos.AttachmentSizeAccuracy;
import com.mailapp.client.model.MailClientDtos.MailSummaryDto;

import java.util.List;

/** Distinguish IMAP metadata from bytes actually downloaded or local file lengths. */
public final class MailSizeDisplay {
    private MailSizeDisplay() {}

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "Không rõ";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public static String attachment(long bytes, AttachmentSizeAccuracy accuracy) {
        if (bytes < 0 || accuracy == AttachmentSizeAccuracy.UNKNOWN
                || (accuracy == null && bytes == 0)) return "Không rõ dung lượng";
        if (accuracy == AttachmentSizeAccuracy.EXACT) return formatBytes(bytes);
        return "≈ " + formatBytes(bytes);
    }

    // Mail summary size refers to the whole message (including MIME overhead),
    // not the decoded attachment payload. Older servers use zero for unknown.
    public static String message(long bytes) {
        return bytes > 0 ? "≈ " + formatBytes(bytes) : "Không rõ dung lượng";
    }

    public static String messageTotal(List<MailSummaryDto> messages) {
        long sum = 0;
        int unknown = 0;
        boolean overflow = false;
        for (MailSummaryDto message : messages) {
            if (message.size() <= 0) {
                unknown++;
            } else if (!overflow) {
                try {
                    sum = Math.addExact(sum, message.size());
                } catch (ArithmeticException ex) {
                    overflow = true;
                }
            }
        }
        if (overflow || sum == 0) {
            return "Không rõ" + (unknown > 0 ? " (" + unknown + " thư chưa rõ dung lượng)" : "");
        }
        return "≈ " + formatBytes(sum)
                + (unknown > 0 ? " (chỉ các thư có số liệu; " + unknown + " thư chưa rõ)" : "");
    }
}
