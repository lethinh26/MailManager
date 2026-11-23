package com.mailapp.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mailapp.model.SystemSetting;
import com.mailapp.model.User;
import com.mailapp.repository.SystemSettingRepository;
import com.mailapp.service.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Triển khai SystemSettingService đọc trực tiếp từ bảng system_settings
 * kết hợp bộ nhớ đệm Caffeine TTL 3 giây và tự động xóa cache tức thì khi cập nhật.
 */
@Service
@Primary
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingServiceImpl.class);

    private final SystemSettingRepository systemSettingRepository;

    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .maximumSize(100)
            .build();

    public SystemSettingServiceImpl(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @Override
    public boolean isOAuth2LinkEnabled() {
        return Boolean.parseBoolean(getSettingValue("ALLOW_OAUTH2_LINK", "true"));
    }

    @Override
    public boolean isAppPasswordLinkEnabled() {
        return Boolean.parseBoolean(getSettingValue("ALLOW_APP_PASSWORD_LINK", "true"));
    }

    @Override
    public boolean isSendMailEnabled() {
        return Boolean.parseBoolean(getSettingValue("ALLOW_SEND_MAIL", "true"));
    }

    @Override
    public boolean isRegistrationEnabled() {
        return Boolean.parseBoolean(getSettingValue("ALLOW_REGISTRATION", "true"));
    }

    @Override
    public boolean isMaintenanceMode() {
        return Boolean.parseBoolean(getSettingValue("MAINTENANCE_MODE", "false"));
    }

    @Override
    public int getMaxGmailPerUser() {
        return parseInt("MAX_GMAIL_PER_USER", 5);
    }

    @Override
    public int getMaxAttachmentSizeMb() {
        return parseInt("MAX_ATTACHMENT_SIZE_MB", 25);
    }

    @Override
    public int getMailPageSize() {
        return parseInt("MAIL_PAGE_SIZE", 30);
    }

    @Override
    public int getLoginMaxFailedAttempts() {
        return parseInt("LOGIN_MAX_FAILED_ATTEMPTS", 5);
    }

    private int parseInt(String key, int fallback) {
        try {
            return Integer.parseInt(getSettingValue(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    @Transactional
    public void setMaintenanceMode(boolean maintenanceMode) {
        updateSetting("MAINTENANCE_MODE", String.valueOf(maintenanceMode));
    }

    @Override
    @Transactional
    public void updateSetting(String key, String value) {
        updateSetting(key, value, null);
    }

    @Override
    @Transactional
    public void updateSetting(String key, String value, User updatedBy) {
        SystemSetting setting = systemSettingRepository.findById(key)
                .orElse(new SystemSetting(key, value, null, updatedBy));
        setting.setValue(value);
        if (updatedBy != null) {
            setting.setUpdatedBy(updatedBy);
        }
        systemSettingRepository.save(setting);
        // Xóa cache tức thì để mọi request tiếp theo thấy ngay giá trị mới
        cache.invalidate(key);
        cache.put(key, value);
        log.info("Cập nhật system_setting key={} value={}", key, value);
    }

    @Override
    public void evictCache() {
        cache.invalidateAll();
    }

    @Override
    public String getSettingValue(String key, String defaultValue) {
        return cache.get(key, k -> systemSettingRepository.findById(k)
                .map(SystemSetting::getValue)
                .orElse(defaultValue));
    }
}
