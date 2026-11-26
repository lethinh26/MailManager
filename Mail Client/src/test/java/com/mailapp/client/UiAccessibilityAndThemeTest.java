package com.mailapp.client;

import com.mailapp.client.net.ApiClient;
import com.mailapp.client.ui.frames.LoginFrame;
import com.mailapp.client.ui.frames.RegisterFrame;
import com.mailapp.client.ui.theme.AvatarUtil;
import com.mailapp.client.ui.theme.Theme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử WCAG AA (>= 4.5:1) toàn bộ bảng màu Theme & Thứ tự Tab (UiAccessibilityAndThemeTest)")
class UiAccessibilityAndThemeTest {

    private static void assertWcagAA(String label, Color bg, Color fg) {
        double ratio = AvatarUtil.contrastRatio(bg, fg);
        assertTrue(ratio >= 4.5,
                String.format("[WCAG AA FAIL] %s: tỷ lệ tương phản %.2f:1 < 4.5:1 (bg=%s, fg=%s)",
                        label, ratio, bg, fg));
    }

    @Test
    @DisplayName("WCAG AA (>= 4.5:1): Mọi cặp màu chữ/nền trong Theme ở cả chế độ Sáng và Tối đều đạt chuẩn")
    void testAllThemeTextBackgroundPairsMeetWcagAA() {
        boolean original = Theme.isDark();
        try {
            for (boolean dark : new boolean[]{false, true}) {
                Theme.setDark(dark);
                String mode = dark ? "DARK" : "LIGHT";

                Color[] surfaces = new Color[]{Theme.bgApp(), Theme.bgSurface(), Theme.bgSurfaceAlt(), Theme.bgHover()};
                String[] surfaceNames = new String[]{"bgApp", "bgSurface", "bgSurfaceAlt", "bgHover"};

                for (int i = 0; i < surfaces.length; i++) {
                    Color bg = surfaces[i];
                    String sn = surfaceNames[i];
                    assertWcagAA(mode + " textPrimary on " + sn, bg, Theme.textPrimary());
                    assertWcagAA(mode + " textSecondary on " + sn, bg, Theme.textSecondary());
                    assertWcagAA(mode + " textMuted on " + sn, bg, Theme.textMuted());
                    assertWcagAA(mode + " primaryText on " + sn, bg, Theme.primaryText());
                    assertWcagAA(mode + " success on " + sn, bg, Theme.success());
                    assertWcagAA(mode + " warning on " + sn, bg, Theme.warning());
                    assertWcagAA(mode + " danger on " + sn, bg, Theme.danger());
                }

                // Trên nền đang chọn (bgSelected)
                assertWcagAA(mode + " textPrimary on bgSelected", Theme.bgSelected(), Theme.textPrimary());
                assertWcagAA(mode + " textSecondary on bgSelected", Theme.bgSelected(), Theme.textSecondary());
                assertWcagAA(mode + " primaryText on bgSelected", Theme.bgSelected(), Theme.primaryText());

                // Nút nhấn (chữ trắng trên nền nút)
                assertWcagAA(mode + " white on primary button", Theme.primary(), Color.WHITE);
                assertWcagAA(mode + " white on primaryHover button", Theme.primaryHover(), Color.WHITE);
                assertWcagAA(mode + " white on dangerBg button", Theme.dangerBg(), Color.WHITE);
                assertWcagAA(mode + " white on dangerHover button", Theme.dangerHover(), Color.WHITE);
                assertWcagAA(mode + " white on successBg button", Theme.successBg(), Color.WHITE);

                // Badge ngữ nghĩa (chữ ngữ nghĩa trên nền soft tương ứng)
                assertWcagAA(mode + " success on successSoft badge", Theme.successSoft(), Theme.success());
                assertWcagAA(mode + " warning on warningSoft badge", Theme.warningSoft(), Theme.warning());
                assertWcagAA(mode + " danger on dangerSoft badge", Theme.dangerSoft(), Theme.danger());
            }
        } finally {
            Theme.setDark(original);
        }
    }

    @Test
    @DisplayName("Thứ tự điều hướng Tab trên LoginFrame và RegisterFrame đi tuần tự từ trên xuống dưới")
    void testTabFocusTraversalOrderOnAuthForms() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "Cần môi trường đồ họa để kiểm thử JFrame");

        ApiClient client = new ApiClient("http://localhost:19999");
        AtomicReference<Throwable> err = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            LoginFrame login = null;
            RegisterFrame reg = null;
            try {
                login = new LoginFrame(client, false);
                FocusTraversalPolicy lp = login.getFocusTraversalPolicy();
                assertNotNull(lp, "LoginFrame phải có FocusTraversalPolicy tường minh");
                List<Component> expectedLogin = login.getExpectedTabOrder();
                assertEquals(7, expectedLogin.size());
                assertSame(expectedLogin.get(0), lp.getFirstComponent(login));
                for (int i = 0; i < expectedLogin.size(); i++) {
                    Component next = lp.getComponentAfter(login, expectedLogin.get(i));
                    Component expectedNext = expectedLogin.get((i + 1) % expectedLogin.size());
                    assertSame(expectedNext, next, "Thứ tự Tab LoginFrame sai tại bước " + i);
                }
                login.applyPublicConfig(new com.mailapp.client.model.PublicConfigDto(false, false, false, true, false, 5, 25));
                assertTrue(login.getMaintenanceBanner().isVisible());
                assertFalse(expectedLogin.get(4).isVisible());
                assertFalse(expectedLogin.get(5).isVisible());
                assertSame(expectedLogin.get(6), lp.getComponentAfter(login, expectedLogin.get(3)));

                reg = new RegisterFrame(client, null);
                FocusTraversalPolicy rp = reg.getFocusTraversalPolicy();
                assertNotNull(rp, "RegisterFrame phải có FocusTraversalPolicy tường minh");
                List<Component> expectedReg = reg.getExpectedTabOrder();
                assertEquals(7, expectedReg.size());
                assertSame(expectedReg.get(0), rp.getFirstComponent(reg));
                for (int i = 0; i < expectedReg.size(); i++) {
                    Component next = rp.getComponentAfter(reg, expectedReg.get(i));
                    Component expectedNext = expectedReg.get((i + 1) % expectedReg.size());
                    assertSame(expectedNext, next, "Thứ tự Tab RegisterFrame sai tại bước " + i);
                }
            } catch (Throwable t) {
                err.set(t);
            } finally {
                if (login != null) login.dispose();
                if (reg != null) reg.dispose();
            }
        });

        if (err.get() != null) {
            fail(err.get());
        }
    }
}

