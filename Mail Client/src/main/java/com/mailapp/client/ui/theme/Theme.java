package com.mailapp.client.ui.theme;

import com.mailapp.client.util.ClientConfig;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Hệ thống thiết kế tập trung (Design System) cho Mail Manager — Java Swing thuần + Java2D.
 * Lưới khoảng cách bội số 4px, font Segoe UI 13pt (fallback SansSerif), hỗ trợ Sáng & Tối.
 */
public final class Theme {

    // Lưới khoảng cách chuẩn 4px
    public static final int SP_1 = 4;
    public static final int SP_2 = 8;
    public static final int SP_3 = 12;
    public static final int SP_4 = 16;
    public static final int SP_5 = 20;
    public static final int SP_6 = 24;

    public static final int RADIUS_SM = 6;
    public static final int RADIUS_MD = 10;
    public static final int RADIUS_LG = 14;

    private static volatile boolean darkMode = "dark".equalsIgnoreCase(ClientConfig.getInstance().getTheme());

    private Theme() {}

    public static boolean isDark() {
        return darkMode;
    }

    public static void setDark(boolean dark) {
        darkMode = dark;
        ClientConfig.getInstance().setTheme(dark ? "dark" : "light");
    }

    // === Bảng màu động theo chủ đề Sáng / Tối (đạt chuẩn tương phản WCAG AA >= 4.5:1) ===
    public static Color bgApp()           { return darkMode ? new Color(20, 23, 28)   : new Color(244, 246, 250); }
    public static Color bgSurface()       { return darkMode ? new Color(28, 32, 39)   : Color.WHITE; }
    public static Color bgSurfaceAlt()    { return darkMode ? new Color(35, 40, 48)   : new Color(248, 250, 252); }
    public static Color bgSelected()      { return darkMode ? new Color(25, 52, 92)   : new Color(232, 240, 254); }
    public static Color bgHover()         { return darkMode ? new Color(44, 50, 61)   : new Color(238, 243, 249); }
    public static Color border()          { return darkMode ? new Color(64, 72, 86)   : new Color(214, 222, 232); }
    public static Color textPrimary()     { return darkMode ? new Color(240, 244, 249): new Color(24, 31, 43); }
    public static Color textSecondary()   { return darkMode ? new Color(176, 186, 202): new Color(74, 86, 104); }
    public static Color textMuted()       { return darkMode ? new Color(156, 168, 186): new Color(90, 102, 118); }

    // === Màu thương hiệu & ngữ nghĩa (đạt WCAG AA >= 4.5:1 với chữ trắng và với nền soft tương ứng) ===
    public static Color primary()         { return new Color(18, 92, 194); }
    public static Color primaryText()     { return darkMode ? new Color(122, 186, 255): new Color(18, 92, 194); }
    public static Color primaryHover()    { return new Color(14, 76, 164); }
    public static Color success()         { return darkMode ? new Color(102, 224, 158): new Color(18, 118, 70); }
    public static Color successBg()       { return new Color(18, 118, 70); }
    public static Color successSoft()     { return darkMode ? new Color(18, 54, 37)   : new Color(225, 247, 235); }
    public static Color warning()         { return darkMode ? new Color(252, 196, 78) : new Color(146, 72, 2); }
    public static Color warningSoft()     { return darkMode ? new Color(64, 42, 10)   : new Color(254, 243, 199); }
    public static Color danger()          { return darkMode ? new Color(255, 140, 152): new Color(192, 32, 48); }
    public static Color dangerBg()        { return new Color(192, 32, 48); }
    public static Color dangerHover()     { return new Color(162, 24, 38); }
    public static Color dangerSoft()      { return darkMode ? new Color(72, 22, 28)   : new Color(254, 226, 226); }
    public static Color starGold()        { return darkMode ? new Color(250, 194, 56) : new Color(168, 96, 0); }

    // === Kiểu chữ chuẩn ===
    private static String resolveFontFamily() {
        String[] preferred = {"Segoe UI", "Inter", "SansSerif"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String p : preferred) {
            for (String a : available) {
                if (a.equalsIgnoreCase(p)) return a;
            }
        }
        return Font.SANS_SERIF;
    }

    private static final String FONT_FAMILY = resolveFontFamily();

    public static Font fontRegular()      { return new Font(FONT_FAMILY, Font.PLAIN, 13); }
    public static Font fontSmall()        { return new Font(FONT_FAMILY, Font.PLAIN, 12); }
    public static Font fontTiny()         { return new Font(FONT_FAMILY, Font.PLAIN, 11); }
    public static Font fontBold()         { return new Font(FONT_FAMILY, Font.BOLD, 13); }
    public static Font fontSubTitle()     { return new Font(FONT_FAMILY, Font.BOLD, 15); }
    public static Font fontTitle()        { return new Font(FONT_FAMILY, Font.BOLD, 20); }
    public static Font fontDisplay()      { return new Font(FONT_FAMILY, Font.BOLD, 24); }

    /**
     * Hint khử răng cưa chữ khớp với cách Swing vẽ chữ native (LCD subpixel nếu desktop hỗ trợ),
     * để nét chữ ở component tự vẽ Java2D không bị mảnh/mờ hơn phần còn lại của cửa sổ.
     */
    public static void applyTextHints(Graphics2D g2) {
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof java.util.Map<?, ?> map) {
            // Giữ cả hướng subpixel và độ tương phản LCD do hệ điều hành chọn.
            // Không ép fractional metrics ON: giữ nguyên cách tính chiều rộng chữ của Swing.
            for (RenderingHints.Key key : new RenderingHints.Key[]{
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.KEY_TEXT_LCD_CONTRAST,
                    RenderingHints.KEY_FRACTIONALMETRICS}) {
                Object value = map.get(key);
                if (value != null) g2.setRenderingHint(key, value);
            }
        }
    }

    // === Nút bo góc vẽ bằng Java2D (Chính / Phụ / Nguy hiểm) ===
    public static JButton primaryButton(String text, Icon icon) {
        return createStyledButton(text, icon, primary(), primaryHover(), Color.WHITE, null);
    }

    public static JButton secondaryButton(String text, Icon icon) {
        return createStyledButton(text, icon, bgSurfaceAlt(), bgHover(), textPrimary(), border());
    }

    public static JButton dangerButton(String text, Icon icon) {
        return createStyledButton(text, icon, dangerBg(), dangerHover(), Color.WHITE, null);
    }

    public static JButton successButton(String text, Icon icon) {
        return createStyledButton(text, icon, successBg(), new Color(14, 96, 56), Color.WHITE, null);
    }

    public static JButton createStyledButton(String text, Icon icon, Color bg, Color hoverBg, Color fg, Color borderColor) {
        JButton btn = new JButton(text, icon) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = !isEnabled()
                        ? (darkMode ? new Color(56, 62, 74) : new Color(110, 120, 134))
                        : (hovered ? hoverBg : bg);
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS_SM * 2f, RADIUS_SM * 2f));
                if (borderColor != null) {
                    g2.setColor(borderColor);
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, RADIUS_SM * 2f, RADIUS_SM * 2f));
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setUI(new BasicButtonUI());
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setForeground(fg);
        btn.setFont(fontBold());
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(SP_2, SP_3, SP_2, SP_3));
        btn.setIconTextGap(SP_2);
        return btn;
    }

    public static JLabel badge(String text, Color bg, Color fg) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setForeground(fg);
        label.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        label.setBorder(new EmptyBorder(2, 8, 2, 8));
        return label;
    }

    public static Border cardBorder() {
        return new CompoundBorder(
                BorderFactory.createLineBorder(border(), 1),
                new EmptyBorder(SP_3, SP_3, SP_3, SP_3)
        );
    }

    public static void styleTextField(JTextField field) {
        field.setFont(fontRegular());
        field.setBackground(bgSurface());
        field.setForeground(textPrimary());
        field.setCaretColor(textPrimary());
        field.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(border(), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
    }

    /**
     * Panel tự động xuống dòng theo chiều rộng container cha (tính đúng preferredHeight để không bị cắt/chồng lấn ở 800x600 và HiDPI).
     */
    public static JPanel createWrapPanel(int hgap, int vgap) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, hgap, vgap)) {
            @Override
            public Dimension getPreferredSize() {
                int availW = getWidth();
                if (availW <= 0 && getParent() != null) {
                    availW = getParent().getWidth();
                }
                if (availW <= 0) availW = 760;
                Insets ins = getInsets();
                int maxW = Math.max(180, availW - ins.left - ins.right);
                int x = 0, rowH = 0, totalH = ins.top + ins.bottom + vgap;
                for (Component c : getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension d = c.getPreferredSize();
                    if (x > 0 && x + d.width > maxW) {
                        totalH += rowH + vgap;
                        x = 0;
                        rowH = 0;
                    }
                    x += d.width + hgap;
                    rowH = Math.max(rowH, d.height);
                }
                totalH += rowH + vgap;
                return new Dimension(availW, Math.max(32, totalH));
            }
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        p.setOpaque(false);
        return p;
    }
}

