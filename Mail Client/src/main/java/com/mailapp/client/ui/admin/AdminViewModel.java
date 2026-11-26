package com.mailapp.client.ui.admin;

import com.mailapp.client.model.AdminClientDtos.DailyCountPoint;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * View-Model thuần logic cho AdminFrame (tách biệt hoàn toàn khỏi Swing để kiểm thử đơn vị dễ dàng).
 */
public final class AdminViewModel {

    private AdminViewModel() {}

    /**
     * Kiểm tra hợp lệ toàn bộ bản đồ cấu hình trước khi gọi PUT /api/admin/settings.
     * Trả về null nếu hợp lệ, hoặc chuỗi lỗi tiếng Việt cụ thể nếu vi phạm.
     */
    public static String validateSettingsUpdate(Map<String, String> settings) {
        return validateSettingsUpdate(settings, false);
    }

    public static String validateSettingsUpdate(Map<String, String> settings, boolean legacyUnlimitedQuota) {
        if (settings == null || settings.isEmpty()) {
            return "Không có cấu hình nào để lưu.";
        }

        boolean oauthOn = Boolean.parseBoolean(settings.getOrDefault("ALLOW_OAUTH2_LINK", "true"));
        boolean appPassOn = Boolean.parseBoolean(settings.getOrDefault("ALLOW_APP_PASSWORD_LINK", "true"));
        if (!oauthOn && !appPassOn) {
            return "Không được tắt đồng thời cả liên kết Google OAuth2 và App Password.";
        }

        String err;
        if ((err = checkIntRange(settings, "MAX_GMAIL_PER_USER", legacyUnlimitedQuota ? 0 : 1, 50)) != null) return err;
        if ((err = checkIntRange(settings, "MAX_ATTACHMENT_SIZE_MB", 1, 50)) != null) return err;
        if ((err = checkIntRange(settings, "MAIL_PAGE_SIZE", 10, 100)) != null) return err;
        if ((err = checkIntRange(settings, "LOGIN_MAX_FAILED_ATTEMPTS", 3, 20)) != null) return err;

        return null;
    }

    private static String checkIntRange(Map<String, String> settings, String key, int min, int max) {
        if (!settings.containsKey(key)) return null;
        try {
            int v = Integer.parseInt(settings.get(key).trim());
            if (v < min || v > max) {
                return "Giá trị của " + key + " phải nằm trong khoảng [" + min + ", " + max + "].";
            }
            return null;
        } catch (NumberFormatException ex) {
            return "Giá trị của " + key + " phải là số nguyên hợp lệ.";
        }
    }

    public static boolean isSelfAction(Long currentAdminId, Long targetUserId) {
        return currentAdminId != null && Objects.equals(currentAdminId, targetUserId);
    }

    public static boolean matchesDeleteConfirmation(String expectedUsername, String typedUsername) {
        if (expectedUsername == null || expectedUsername.isBlank() || typedUsername == null) {
            return false;
        }
        return expectedUsername.equals(typedUsername.trim());
    }

    public static boolean isMaintenanceTurningOn(boolean previousValue, boolean newValue) {
        return !previousValue && newValue;
    }

    public static long computeMaxChartValue(List<DailyCountPoint> points) {
        if (points == null || points.isEmpty()) return 1L;
        long max = 1L;
        for (DailyCountPoint p : points) {
            if (p != null && p.count() > max) max = p.count();
        }
        return max;
    }

    public static String formatShortDayLabel(String yyyyMmDd) {
        if (yyyyMmDd == null || yyyyMmDd.length() < 10) {
            return yyyyMmDd != null ? yyyyMmDd : "";
        }
        return yyyyMmDd.substring(8, 10) + "/" + yyyyMmDd.substring(5, 7);
    }
}

