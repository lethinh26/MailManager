package com.mailapp.client;

import com.mailapp.client.ui.theme.Theme;
import com.mailapp.client.ui.theme.WrapLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chặn tái phát các lỗi bố cục đã sửa: hàng badge tràn khỏi thẻ tài khoản ở cột hẹp,
 * cỡ chữ quá nhỏ gây "chữ mờ", và label không cắt bằng "…" khi bị ép hẹp.
 */
@DisplayName("Hồi quy bố cục: WrapLayout, thang cỡ chữ và ellipsis (UiLayoutRegressionTest)")
class UiLayoutRegressionTest {

    private static void onEdt(Runnable r) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { r.run(); } catch (Throwable t) { err.set(t); }
        });
        if (err.get() != null) fail(err.get());
    }

    @Test
    @DisplayName("WrapLayout: 2 badge rộng 110px trong khung 170px phải xuống 2 hàng, không tràn ngang")
    void wrapLayoutBreaksIntoTwoRowsWhenTooNarrow() throws Exception {
        onEdt(() -> {
            JPanel panel = Theme.createWrapPanel(4, 3);
            assertInstanceOf(WrapLayout.class, panel.getLayout());

            for (int i = 0; i < 2; i++) {
                JLabel badge = Theme.badge("App Password", Theme.bgSurfaceAlt(), Theme.textPrimary());
                badge.setPreferredSize(new Dimension(110, 22));
                panel.add(badge);
            }
            panel.setSize(170, 200);
            panel.doLayout();

            Dimension pref = panel.getPreferredSize();
            assertTrue(pref.height >= 44,
                    "2 badge 110px trong khung 170px phải cao >= 2 hàng (thực tế " + pref.height + "px)");

            for (Component c : panel.getComponents()) {
                assertTrue(c.getX() + c.getWidth() <= 170,
                        "Badge vượt ra ngoài khung 170px: x=" + c.getX() + " w=" + c.getWidth());
            }
        });
    }

    @Test
    @DisplayName("Cỡ chữ tối thiểu: tiny >= 12pt, base >= 14pt để không bị mờ ở màn 125%")
    void fontScaleIsLargeEnoughForReadableText() {
        assertTrue(Theme.fontTiny().getSize() >= 12, "fontTiny phải >= 12pt");
        assertTrue(Theme.fontSmall().getSize() >= 13, "fontSmall phải >= 13pt");
        assertTrue(Theme.fontRegular().getSize() >= 14, "fontRegular phải >= 14pt");
        assertTrue(Theme.fontTiny().getSize() < Theme.fontRegular().getSize(), "tiny phải nhỏ hơn regular");
    }

    @Test
    @DisplayName("UIManager dùng đúng font Theme sau installUiDefaults (menu/status bar không còn font hệ thống nhỏ hơn)")
    void uiDefaultsUseThemeFont() throws Exception {
        onEdt(() -> {
            Font before = UIManager.getFont("Label.font");
            try {
                Theme.installUiDefaults();
                for (String key : new String[]{"Label.font", "Menu.font", "MenuItem.font", "ToolTip.font"}) {
                    Font f = UIManager.getFont(key);
                    assertNotNull(f, key + " chưa được đặt");
                    assertEquals(Theme.fontRegular().getSize(), f.getSize(), key + " phải cùng cỡ chữ với Theme");
                }
            } finally {
                if (before != null) UIManager.put("Label.font", before);
            }
        });
    }

    @Test
    @DisplayName("ellipsisLabel: bị ép hẹp thì vẫn giữ tooltip đầy đủ và có minimum width = 0")
    void ellipsisLabelKeepsFullTextInTooltip() throws Exception {
        onEdt(() -> {
            JLabel label = Theme.ellipsisLabel("cuong.work@ptit.edu.vn");
            assertEquals("cuong.work@ptit.edu.vn", label.getToolTipText());
            assertEquals(0, label.getMinimumSize().width, "minimum width phải = 0 để label được ép hẹp");
            assertEquals(Integer.MAX_VALUE, label.getMaximumSize().width);

            label.setSize(40, 20);
            label.paint(new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    .getGraphics());
            assertEquals("cuong.work@ptit.edu.vn", label.getText(), "paint không được làm thay đổi text gốc");
        });
    }
}
