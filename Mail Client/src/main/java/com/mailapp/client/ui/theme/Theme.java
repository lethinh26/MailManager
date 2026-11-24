package com.mailapp.client.ui.theme;

import com.mailapp.client.util.ClientConfig;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
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
    public static Color textMuted()       { return darkMode ? new Color(156, 168, 186): new Color(76, 87, 102); }

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

    // === Kiểu chữ chuẩn (thang cỡ chữ đọc được ở màn 125%: 12/13/14/16/21/26) ===
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

    public static final int FONT_BASE = 14;
    public static final int FONT_SMALL = 13;
    public static final int FONT_TINY = 12;

    public static Font fontRegular()      { return new Font(FONT_FAMILY, Font.PLAIN, FONT_BASE); }
    public static Font fontSmall()        { return new Font(FONT_FAMILY, Font.PLAIN, FONT_SMALL); }
    public static Font fontTiny()         { return new Font(FONT_FAMILY, Font.PLAIN, FONT_TINY); }
    public static Font fontBold()         { return new Font(FONT_FAMILY, Font.BOLD, FONT_BASE); }
    public static Font fontSubTitle()     { return new Font(FONT_FAMILY, Font.BOLD, 16); }
    public static Font fontTitle()        { return new Font(FONT_FAMILY, Font.BOLD, 21); }
    public static Font fontDisplay()      { return new Font(FONT_FAMILY, Font.BOLD, 26); }

    public static void installUiDefaults() {
        FontUIResource reg = new FontUIResource(fontRegular());
        FontUIResource bold = new FontUIResource(fontBold());
        for (String key : new String[]{
                "Label.font", "Button.font", "ToggleButton.font", "RadioButton.font", "CheckBox.font",
                "TextField.font", "TextArea.font", "TextPane.font", "EditorPane.font", "PasswordField.font",
                "ComboBox.font", "List.font", "Table.font", "TableHeader.font", "Tree.font",
                "Menu.font", "MenuItem.font", "CheckBoxMenuItem.font", "RadioButtonMenuItem.font",
                "PopupMenu.font", "OptionPane.font", "OptionPane.messageFont", "OptionPane.buttonFont",
                "ToolTip.font", "Spinner.font", "ProgressBar.font", "TabbedPane.font",
                "MenuBar.font", "Panel.font", "ColorChooser.font", "FileChooser.font", "InternalFrame.font"}) {
            UIManager.put(key, reg);
        }
        for (String key : new String[]{"TableHeader.font", "MenuBar.font"}) {
            UIManager.put(key, bold);
        }
        UIManager.put("ToolTip.foreground", textPrimary());
        UIManager.put("ToolTip.background", bgSurfaceAlt());
    }

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

    /** JLabel tự cắt chữ bằng "…" khi bị ép hẹp, tooltip giữ nguyên nội dung đầy đủ. */
    public static JLabel ellipsisLabel(String text) {
        JLabel l = new JLabel(text) {
            private boolean paintingEllipsis;

            @Override
            public void paintComponent(Graphics g) {
                String full = getText();
                if (full == null || full.isEmpty() || getFont() == null) {
                    super.paintComponent(g);
                    return;
                }
                FontMetrics fm = getFontMetrics(getFont());
                Insets in = getInsets();
                int avail = getWidth() - in.left - in.right - (getIcon() != null ? getIcon().getIconWidth() + getIconTextGap() : 0);
                if (avail <= 0 || fm.stringWidth(full) <= avail) {
                    super.paintComponent(g);
                    return;
                }
                String cut = full;
                while (cut.length() > 1 && fm.stringWidth(cut + "…") > avail) {
                    cut = cut.substring(0, cut.length() - 1);
                }
                paintingEllipsis = true;
                try {
                    setText(cut + "…");
                    super.paintComponent(g);
                } finally {
                    setText(full);
                    paintingEllipsis = false;
                }
            }

            // Cho phép BoxLayout/GridBagLayout ép hẹp để phần "…" có tác dụng thay vì bị cắt cụt.
            @Override public Dimension getMinimumSize()    { return new Dimension(0, super.getMinimumSize().height); }
            @Override public Dimension getMaximumSize()    { return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height); }

            @Override
            public void setText(String t) {
                super.setText(t);
                // Giữ tooltip luôn khớp nội dung đầy đủ, kể cả khi caller đổi text sau khi tạo.
                if (!paintingEllipsis) setToolTipText(t);
            }
        };
        l.setToolTipText(text);
        return l;
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
        return badge(text, null, bg, fg);
    }

    public static JLabel badge(String text, Icon icon, Color bg, Color fg) {
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
        label.setFont(fontTiny().deriveFont(Font.BOLD));
        label.setBorder(new EmptyBorder(2, icon != null ? 6 : 8, 2, 8));
        label.setIconTextGap(4);
        if (icon != null) {
            label.setIcon(icon);
            label.setHorizontalTextPosition(SwingConstants.RIGHT);
        }
        label.setToolTipText(text);
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
     * Panel tự xuống dòng theo chiều rộng container cha.
     * Dùng {@link WrapLayout} (tính preferredHeight theo width thật) thay vì FlowLayout:
     * FlowLayout luôn báo preferredSize 1 hàng nên các hàng badge/nút bị tràn ra ngoài và bị cắt chữ.
     */
    public static JPanel createWrapPanel(int hgap, int vgap) {
        WrapLayout layout = new WrapLayout(FlowLayout.LEFT, hgap, vgap);
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /** Panel luôn bám đúng bề rộng viewport (không sinh thanh cuộn ngang, không cắt nội dung). */
    public static JPanel trackedWidthPanel(LayoutManager layout) {
        return new TrackedWidthPanel(layout);
    }

    private static final class TrackedWidthPanel extends JPanel implements Scrollable {
        TrackedWidthPanel(LayoutManager layout) { super(layout); }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Nút icon-only có tooltip + accessible name (dùng cho toolbar gọn). */
    public static JButton iconButton(AppIcons.Glyph glyph, String tooltip, Color fg, Runnable action) {
        return iconButton(glyph, 18, tooltip, fg, action);
    }

    public static JButton iconButton(AppIcons.Glyph glyph, int size, String tooltip, Color fg, Runnable action) {
        JButton btn = new JButton(AppIcons.vectorIcon(glyph, size, fg));
        btn.setToolTipText(tooltip);
        btn.getAccessibleContext().setAccessibleName(tooltip);
        btn.setUI(new BasicButtonUI());
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(SP_2, SP_2, SP_2, SP_2));
        if (action != null) btn.addActionListener(e -> action.run());
        // Opaque chỉ bật khi hover: BasicButtonUI mới vẽ nền hover, còn bình thường vẫn trong suốt.
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(bgHover()); btn.setOpaque(true); btn.repaint(); }
            @Override public void mouseExited(MouseEvent e)  { btn.setOpaque(false); btn.repaint(); }
        });
        return btn;
    }

    /**
     * Splitter mảnh, kéo mượt (continuousLayout) và có nút thu/mở 1 chạm.
     * Ngoài ra double-click lên thanh kéo = thu gọn / mở lại đúng vị trí trước đó.
     */
    public static void styleSplitter(JSplitPane split) {
        split.setDividerSize(7);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setBackground(bgApp());

        int[] lastExpanded = {-1};
        onDivider(split, new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int pos = split.getDividerLocation();
                if (pos > split.getDividerSize() + 4) lastExpanded[0] = pos;
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                int min = split.getDividerSize() + 2;
                int target = split.getDividerLocation() <= min
                        ? (lastExpanded[0] > min ? lastExpanded[0] : split.getWidth() / 3)
                        : min;
                split.setDividerLocation(target);
            }
        });
    }

    /** Ghi vị trí divider vào ~/.mailclient/config.properties khi người dùng thả chuột (không ghi mỗi pixel kéo). */
    public static void persistSplit(JSplitPane split, String key) {
        onDivider(split, new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                int pos = split.getDividerLocation();
                if (pos > 0) ClientConfig.getInstance().setSplit(key, pos);
            }
        });
    }

    /** Gắn listener vào divider thật của split; L&F có thể tạo divider muộn nên thử lại 1 lần. */
    private static void onDivider(JSplitPane split, MouseListener listener) {
        onDivider(split, listener, true);
    }

    private static void onDivider(JSplitPane split, MouseListener listener, boolean mayRetry) {
        for (Component c : split.getComponents()) {
            if (c instanceof BasicSplitPaneDivider divider) {
                divider.addMouseListener(listener);
                return;
            }
        }
        if (mayRetry) SwingUtilities.invokeLater(() -> onDivider(split, listener, false));
    }
}

