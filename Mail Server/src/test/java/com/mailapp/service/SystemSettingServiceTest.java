package com.mailapp.service;

import com.mailapp.model.SystemSetting;
import com.mailapp.repository.SystemSettingRepository;
import com.mailapp.service.impl.SystemSettingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingServiceImpl(systemSettingRepository);
    }

    @Test
    @DisplayName("Giá trị mặc định trả về chuẩn xác khi chưa có cấu hình trong DB")
    void testDefaultValuesWhenDbEmpty() {
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());

        assertTrue(service.isRegistrationEnabled());
        assertTrue(service.isOAuth2LinkEnabled());
        assertTrue(service.isAppPasswordLinkEnabled());
        assertTrue(service.isSendMailEnabled());
        assertFalse(service.isMaintenanceMode());
        assertEquals(5, service.getMaxGmailPerUser());
        assertEquals(25, service.getMaxAttachmentSizeMb());
        assertEquals(20, service.getMailPageSize());
        assertEquals(5, service.getLoginMaxFailedAttempts());
    }

    @Test
    @DisplayName("Đọc giá trị từ DB và sử dụng bộ nhớ đệm Caffeine")
    void testReadFromDbAndCache() {
        when(systemSettingRepository.findById("ALLOW_OAUTH2_LINK"))
                .thenReturn(Optional.of(new SystemSetting("ALLOW_OAUTH2_LINK", "false", null, null)));

        // Lần 1: đọc từ repository
        assertFalse(service.isOAuth2LinkEnabled());
        verify(systemSettingRepository, times(1)).findById("ALLOW_OAUTH2_LINK");

        // Lần 2: lấy từ Caffeine cache, không gọi lại repository
        assertFalse(service.isOAuth2LinkEnabled());
        verify(systemSettingRepository, times(1)).findById("ALLOW_OAUTH2_LINK");
    }

    @Test
    void genericAccessorsUseKnownDefaultsWithoutCachingCallerFallback() {
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        assertFalse(service.getBoolOrDefault("ALLOW_REGISTRATION", false));
        assertTrue(service.getBool("ALLOW_REGISTRATION"));
        assertTrue(service.isRegistrationEnabled());
        verify(systemSettingRepository, times(1)).findById("ALLOW_REGISTRATION");
        assertEquals(20, service.getInt("MAIL_PAGE_SIZE"));
        assertEquals("20", service.getString("MAIL_PAGE_SIZE"));
        assertNull(service.getString("MISSING_KEY"));
        assertTrue(service.getBoolOrDefault("MISSING_KEY", true));
        assertFalse(service.getBoolOrDefault("MISSING_KEY", false));
        verify(systemSettingRepository, times(1)).findById("MISSING_KEY");
    }

    @Test
    @DisplayName("Cập nhật cờ hệ thống lưu vào DB và làm mới cache tức thì")
    void testUpdateSetting() {
        when(systemSettingRepository.findById("MAINTENANCE_MODE"))
                .thenReturn(Optional.empty());

        service.setMaintenanceMode(true);

        verify(systemSettingRepository).save(any(SystemSetting.class));
        assertTrue(service.isMaintenanceMode());
    }
}

