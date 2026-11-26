package com.mailapp.client;

import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.ui.frames.LoginFrame;
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
    void attachmentStripKeepsAllChipsReachableAtNarrowWidth() throws Exception {
        onEdt(() -> {
            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            for (int i = 0; i < 3; i++) {
                JButton chip = new JButton("fixture " + i);
                chip.setPreferredSize(new Dimension(180, 30));
                chips.add(chip);
            }
            JScrollPane scroll = Theme.horizontalChipScroll(chips);
            scroll.setSize(260, scroll.getPreferredSize().height);
            scroll.doLayout();
            scroll.getViewport().doLayout();
            chips.doLayout();
            assertTrue(chips.getWidth() > scroll.getViewport().getExtentSize().width);
            Component last = chips.getComponent(2);
            assertEquals(chips.getComponent(0).getY(), last.getY());
            assertTrue(last.getX() + last.getWidth() <= chips.getWidth());
            chips.scrollRectToVisible(last.getBounds());
            assertTrue(scroll.getViewport().getViewPosition().x > 0);
            assertTrue(last.getY() + last.getHeight() <= scroll.getViewport().getExtentSize().height);
        });
    }

    @Test
    void minimumLoginSizeKeepsBottomControlsReachable() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<LoginFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> {
                LoginFrame login = new LoginFrame(new ApiClient("http://127.0.0.1:19999"), false);
                frame.set(login);
                login.applyPublicConfig(new PublicConfigDto(true, true, true, true, true, 5, 25));
                login.setSize(login.getMinimumSize());
                login.setVisible(true);
            });
            onEdt(() -> {
                LoginFrame login = frame.get();
                login.validate();
                JComponent ping = (JComponent) login.getExpectedTabOrder().get(6);
                JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, ping);
                assertNotNull(scroll, "Login controls must be inside a scrollable viewport");
                assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
                assertTrue(scroll.getViewport().getViewSize().height >= scroll.getViewport().getExtentSize().height);
                ping.scrollRectToVisible(new Rectangle(0, 0, ping.getWidth(), ping.getHeight()));
                assertEquals(new Rectangle(0, 0, ping.getWidth(), ping.getHeight()), ping.getVisibleRect());
                assertTrue(scroll.getViewport().getViewPosition().y > 0);
                JComponent first = (JComponent) login.getExpectedTabOrder().get(0);
                first.scrollRectToVisible(new Rectangle(0, 0, first.getWidth(), first.getHeight()));
                assertEquals(new Rectangle(0, 0, first.getWidth(), first.getHeight()), first.getVisibleRect());
            });
        } finally {
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
        }
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
