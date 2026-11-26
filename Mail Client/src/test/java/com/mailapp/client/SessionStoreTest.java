package com.mailapp.client;

import com.mailapp.client.model.UserDto;
import com.mailapp.client.util.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử quản lý phiên làm việc người dùng (SessionStoreTest)")
class SessionStoreTest {

    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        sessionStore = SessionStore.getInstance();
        sessionStore.clearSession();
    }

    @Test
    @DisplayName("Lưu phiên đăng nhập và xóa phiên khi đăng xuất")
    void testSaveAndClearSession() {
        assertFalse(sessionStore.isLoggedIn());

        UserDto user = new UserDto(1L, "cuongdfle", "Lê Thanh Cường", "cuongdfle@gmail.com", "ADMIN", false, "2026-11-22T08:00:00Z");
        sessionStore.saveSession("test-token-jwt", user);

        assertTrue(sessionStore.isLoggedIn());
        assertEquals("test-token-jwt", sessionStore.getAccessToken());
        assertEquals("cuongdfle", sessionStore.getCurrentUser().username());

        sessionStore.clearSession();
        assertFalse(sessionStore.isLoggedIn());
        assertNull(sessionStore.getAccessToken());
        assertNull(sessionStore.getCurrentUser());
    }

    @Test
    void cachedAttachmentIsInvalidatedOnChangeOrSessionReplacement(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var user = new UserDto(1L, "fixture", "Fixture", "fixture@example.test", "USER", false, null);
        sessionStore.saveSession("fixture-only", user);
        try {
            long generation = sessionStore.getGeneration();
            var key = new SessionStore.AttachmentKey(10L, "INBOX", 9L, 7L, 0);
            var file = java.nio.file.Files.writeString(dir.resolve("fixture.txt"), "original");
            sessionStore.rememberDownloadedAttachment(generation, key, file);
            assertEquals(file.toAbsolutePath().normalize(), sessionStore.findDownloadedAttachment(key));
            java.nio.file.Files.writeString(file, "replacement has a different length");
            assertNull(sessionStore.findDownloadedAttachment(key));
            sessionStore.rememberDownloadedAttachment(generation, key, file);
            sessionStore.clearSession();
            sessionStore.saveSession("new-fixture-only", user);
            sessionStore.rememberDownloadedAttachment(generation, key, file);
            assertNull(sessionStore.findDownloadedAttachment(key));
        } finally {
            sessionStore.clearSession();
        }
    }

    @Test
    @DisplayName("Cập nhật token mới khi đổi mật khẩu bắt buộc thành công")
    void testUpdateToken() {
        UserDto user = new UserDto(2L, "user1", "User One", "user1@example.com", "USER", true, "2026-11-22T08:00:00Z");
        sessionStore.saveSession("old-token", user);

        sessionStore.updateToken("new-refreshed-token");

        assertEquals("new-refreshed-token", sessionStore.getAccessToken());
        assertEquals("user1", sessionStore.getCurrentUser().username());
    }
}
