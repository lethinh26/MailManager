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
}
