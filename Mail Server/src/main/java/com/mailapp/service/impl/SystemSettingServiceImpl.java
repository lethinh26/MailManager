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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * Triển khai SystemSettingService đọc trực tiếp từ bảng system_settings
 * kết hợp bộ nhớ đệm Caffeine TTL 3 giây và tự động xóa cache tức thì khi cập nhật.
 */
@Service
@Primary
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingServiceImpl.class);

    private final SystemSettingRepository systemSettingRepository;

    private final Cache<String, Optional<String>> cache = Caffeine.newBuilder()
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
        return parseInt("MAIL_PAGE_SIZE", 20);
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
        // Read committed data on next access; an older commit cannot overwrite a newer cache entry.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            afterCommit(() -> cache.invalidate(key));
        } else {
            cache.invalidate(key);
            cache.put(key, Optional.ofNullable(value));
        }
        log.info("Cập nhật system_setting");
    }

    @Override
    public void evictCache() {
        afterCommit(cache::invalidateAll);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Override
    public String getSettingValue(String key, String defaultValue) {
        // Cache the DB result, not the caller's fallback. Two callers may
        // legitimately ask for different defaults for the same missing key.
        return cache.get(key, k -> systemSettingRepository.findById(k)
                .map(SystemSetting::getValue)).orElse(defaultValue);
    }
}
