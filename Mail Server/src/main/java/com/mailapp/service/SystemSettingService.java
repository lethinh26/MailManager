package com.mailapp.service;

import com.mailapp.model.User;

/**
 * Service quản lý các cấu hình động của hệ thống từ bảng system_settings.
 */
public interface SystemSettingService {

    boolean isOAuth2LinkEnabled();

    boolean isAppPasswordLinkEnabled();

    boolean isSendMailEnabled();

    boolean isRegistrationEnabled();

    boolean isMaintenanceMode();

    int getMaxGmailPerUser();

    int getMaxAttachmentSizeMb();

    int getMailPageSize();

    int getLoginMaxFailedAttempts();

    void setMaintenanceMode(boolean maintenanceMode);

    void updateSetting(String key, String value);

    void updateSetting(String key, String value, User updatedBy);

    void evictCache();

    String getSettingValue(String key, String defaultValue);

    /** Compatibility accessors; known keys use the same defaults as typed callers. */
    default String getString(String key) {
        String fallback = switch (key) {
            case "ALLOW_REGISTRATION", "ALLOW_OAUTH2_LINK", "ALLOW_APP_PASSWORD_LINK", "ALLOW_SEND_MAIL" -> "true";
            case "MAINTENANCE_MODE" -> "false";
            case "MAX_GMAIL_PER_USER", "LOGIN_MAX_FAILED_ATTEMPTS" -> "5";
            case "MAX_ATTACHMENT_SIZE_MB" -> "25";
            case "MAIL_PAGE_SIZE" -> "20";
            default -> null;
        };
        return getSettingValue(key, fallback);
    }

    default boolean getBool(String key) {
        return Boolean.parseBoolean(getString(key));
    }

    default boolean getBoolOrDefault(String key, boolean defaultValue) {
        String value = getSettingValue(key, null);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return defaultValue;
    }

    default int getInt(String key) {
        String value = getString(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return switch (key) {
                case "MAX_GMAIL_PER_USER", "LOGIN_MAX_FAILED_ATTEMPTS" -> 5;
                case "MAX_ATTACHMENT_SIZE_MB" -> 25;
                case "MAIL_PAGE_SIZE" -> 20;
                default -> 0;
            };
        }
    }
}
