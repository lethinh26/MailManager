package com.mailapp.client;

import com.mailapp.client.model.UserDto;
import com.mailapp.client.ui.frames.MainFrame;
import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.AvatarUtil;
import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.util.SessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.ZonedDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Avatar quyết định, Tương phản WCAG và Nhận diện thương hiệu (AvatarThemeAndPrivacyTest)")
class AvatarThemeAndPrivacyTest {

    @Test
    @DisplayName("AvatarUtil sinh màu quyết định theo email và tách chữ cái đầu chuẩn")
    void testAvatarDeterminismAndInitials() {
        Color c1 = AvatarUtil.colorForKey("cuongdfle@gmail.com");
        Color c2 = AvatarUtil.colorForKey("CUONGDFLE@GMAIL.COM");
        assertEquals(c1, c2, "Cùng địa chỉ email (không phân biệt hoa/thường) phải ra cùng một màu nền!");

        assertEquals("LT", AvatarUtil.initialsFor("Lê Thanh Cường", "cuongdfle@gmail.com"));
        assertEquals("PA", AvatarUtil.initialsFor("", "ptit.admin@ptit.edu.vn"));
        assertEquals("?", AvatarUtil.initialsFor("", ""));
    }

    @Test
    @DisplayName("Toàn bộ bảng màu Avatar đạt chuẩn tương phản WCAG AA (>= 4.5:1) với màu chữ chọn tự động")
    void testAvatarPaletteWcagContrast() {
        for (String sample : new String[]{
                "a@test.com", "b@test.com", "c@test.com", "d@test.com", "e@test.com",
                "f@test.com", "g@test.com", "h@test.com", "i@test.com", "j@test.com",
                "k@test.com", "l@test.com", "cuongdfle@gmail.com", "daotao@ptit.edu.vn"
        }) {
            Color bg = AvatarUtil.colorForKey(sample);
            Color fg = AvatarUtil.bestTextColor(bg);
            double ratio = AvatarUtil.contrastRatio(bg, fg);
            assertTrue(ratio >= 4.5,
                    "Màu nền " + bg + " + chữ " + fg + " cho " + sample + " phải đạt tương phản >= 4.5:1, thực tế: " + ratio);
        }
    }

    @Test
    @DisplayName("AppIcons vẽ logo thương hiệu Java2D đa kích thước và Theme chuyển đổi Sáng/Tối không lỗi")
    void testBrandLogoAndThemeSwitch() {
        for (int sz : new int[]{16, 32, 64, 256}) {
            BufferedImage img = AppIcons.renderBrandLogo(sz);
            assertEquals(sz, img.getWidth());
            assertEquals(sz, img.getHeight());
            int centerArgb = img.getRGB(sz / 2, sz / 2);
            assertTrue(((centerArgb >>> 24) & 0xFF) > 0, "Logo kích thước " + sz + " phải có nội dung hiển thị!");
        }

        boolean original = Theme.isDark();
        try {
            Theme.setDark(false);
            assertNotEquals(Theme.bgSurface(), Theme.textPrimary());
            Theme.setDark(true);
            assertNotEquals(Theme.bgSurface(), Theme.textPrimary());
        } finally {
            Theme.setDark(original);
        }
    }

    @Test
    @DisplayName("Hồi quy: Định dạng ngày đa múi giờ (Instant Z, OffsetDateTime +07:00, LocalDateTime) và đa luồng SessionStore")
    void testTimezoneDateParsingAndSessionStoreThreadSafety() throws Exception {
        ZonedDateTime z1 = MainFrame.parseDateToSystemZone("2026-11-22T09:15:00Z");
        ZonedDateTime z2 = MainFrame.parseDateToSystemZone("2026-11-22T16:15:00+07:00");
        ZonedDateTime z3 = MainFrame.parseDateToSystemZone("2026-11-22T16:15:00");
        assertNotNull(z1);
        assertNotNull(z2);
        assertNotNull(z3);
        assertEquals(z1.toInstant(), z2.toInstant());
        assertFalse(MainFrame.formatFriendlyDate("2026-05-10T10:00:00+07:00").isBlank());

        // Stress test đa luồng 16 luồng đọc/ghi SessionStore đồng thời
        SessionStore store = SessionStore.getInstance();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(16);
        AtomicInteger errors = new AtomicInteger(0);
        for (int i = 0; i < 16; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    for (int k = 0; k < 50; k++) {
                        store.saveSession("tok-" + idx + "-" + k,
                                new UserDto((long) idx, "u" + idx, "User " + idx, "u@t.com", "USER", false, "2026-11-22"));
                        store.getAccessToken();
                        store.getCurrentUser();
                        store.isLoggedIn();
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        store.clearSession();
        assertEquals(0, errors.get(), "SessionStore phải hoàn toàn an toàn đa luồng!");
    }
}
