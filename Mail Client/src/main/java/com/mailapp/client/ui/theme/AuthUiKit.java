package com.mailapp.client.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Bộ thành phần giao diện xác thực hiện đại cho Veyra — vẽ 100% bằng Java2D thuần (không thư viện ngoài).
 * Gồm: nền cực quang động (AuroraHeroPanel), thẻ đổ bóng mềm + rung lỗi (ElevatedCardPanel),
 * ô nhập bo góc phát sáng khi focus + placeholder + nút ẩn/hiện mật khẩu (ModernFieldWrapper),
 * nút gradient chuyển sắc mượt + vòng xoay tải (AnimatedGradientButton).
 */
public final class AuthUiKit {

    private AuthUiKit() {}

    public static Color blend(Color c1, Color c2, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int r = Math.round(c1.getRed() + (c2.getRed() - c1.getRed()) * clamped);
        int g = Math.round(c1.getGreen() + (c2.getGreen() - c1.getGreen()) * clamped);
        int b = Math.round(c1.getBlue() + (c2.getBlue() - c1.getBlue()) * clamped);
        int a = Math.round(c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * clamped);
        return new Color(r, g, b, a);
    }

    /**
     * Bảng thương hiệu bên trái màn hình đăng nhập với nền cực quang chuyển động mềm mại bằng Java2D.
     */
    public static class AuroraHeroPanel extends JPanel {
        private float phase = 0f;
        private Timer animTimer;
        private final BufferedImage brandLogo;
        private final String serverHostLabel;

        public AuroraHeroPanel(String serverUrl) {
            this.brandLogo = AppIcons.renderBrandLogo(76);
            this.serverHostLabel = extractHostLabel(serverUrl);
            setOpaque(true);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(32, 32, 26, 32));
            buildContent();

            animTimer = new Timer(45, e -> {
                phase += 0.025f;
                if (phase > (float) (Math.PI * 200)) phase = 0f;
                repaint();
            });
            animTimer.setCoalesce(true);
        }

        private static String extractHostLabel(String url) {
            if (url == null || url.isBlank()) return "HTTPS • localhost:8443";
            String clean = url.replaceFirst("^https?://", "").replaceAll("/+$", "");
            return (url.startsWith("https://") ? "TLS 1.3 • " : "HTTP • ") + clean;
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (animTimer != null && !animTimer.isRunning()) {
                animTimer.start();
            }
        }

        @Override
        public void removeNotify() {
            stopAnimation();
            super.removeNotify();
        }

        public void stopAnimation() {
            if (animTimer != null && animTimer.isRunning()) {
                animTimer.stop();
            }
        }

        private void buildContent() {
            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            // Logo có quầng sáng tinh tế
            JPanel logoWrap = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx = 42, cy = 42;
                    g2.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), 44f,
                            new float[]{0f, 1f},
                            new Color[]{new Color(56, 189, 248, 85), new Color(56, 189, 248, 0)}));
                    g2.fillOval(cx - 44, cy - 44, 88, 88);
                    g2.drawImage(brandLogo, 4, 4, null);
                    g2.dispose();
                }
            };
            logoWrap.setOpaque(false);
            logoWrap.setPreferredSize(new Dimension(84, 84));
            logoWrap.setMaximumSize(new Dimension(84, 84));
            logoWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Hàng tên thương hiệu + nhãn SECURE MAIL (cố định chiều cao tối đa để không đẩy khoảng trắng thừa)
            JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            brandRow.setOpaque(false);
            brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            brandRow.setMaximumSize(new Dimension(340, 42));

            JLabel wordmark = new JLabel("V E Y R A");
            wordmark.setFont(new Font(Theme.fontDisplay().getFamily(), Font.BOLD, 28));
            wordmark.setForeground(Color.WHITE);
            brandRow.add(wordmark);
            brandRow.add(Box.createHorizontalStrut(12));
            brandRow.add(createGlassTag("SECURE MAIL"));

            JLabel tagline = new JLabel("<html><div style='line-height:1.45;'>"
                    + "Không gian thư điện tử bảo mật,<br>"
                    + "nhanh gọn &amp; chuẩn mã hóa hai lớp."
                    + "</div></html>");
            tagline.setFont(Theme.fontRegular());
            tagline.setForeground(new Color(203, 213, 225));
            tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
            tagline.setMaximumSize(new Dimension(340, 48));

            center.add(Box.createVerticalGlue());
            center.add(logoWrap);
            center.add(Box.createVerticalStrut(16));
            center.add(brandRow);
            center.add(Box.createVerticalStrut(10));
            center.add(tagline);
            center.add(Box.createVerticalStrut(26));

            center.add(createFeaturePill(AppIcons.Glyph.SHIELD, "Bảo mật 2 lớp TLS & mã hóa AES-256-GCM"));
            center.add(Box.createVerticalStrut(10));
            center.add(createFeaturePill(AppIcons.Glyph.CHECK_CIRCLE, "Liên kết Google OAuth2 PKCE không lộ mật khẩu"));
            center.add(Box.createVerticalStrut(10));
            center.add(createFeaturePill(AppIcons.Glyph.DOWNLOAD, "Tải tệp đính kèm streaming & dọn thư thông minh"));
            center.add(Box.createVerticalGlue());

            add(center, BorderLayout.CENTER);

            // Footer hiển thị trạng thái kết nối bảo mật
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            footer.setOpaque(false);
            JLabel dot = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    float pulse = 0.65f + 0.35f * (float) Math.sin(phase * 1.8);
                    g2.setColor(new Color(52, 211, 153, Math.round(90 * pulse)));
                    g2.fillOval(0, 0, 12, 12);
                    g2.setColor(new Color(52, 211, 153));
                    g2.fillOval(3, 3, 6, 6);
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(12, 12));
            JLabel hostLbl = new JLabel(serverHostLabel);
            hostLbl.setFont(Theme.fontTiny());
            hostLbl.setForeground(new Color(148, 163, 184));
            footer.add(dot);
            footer.add(hostLbl);
            add(footer, BorderLayout.SOUTH);
        }

        private static JLabel createGlassTag(String text) {
            JLabel tag = new JLabel(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(56, 189, 248, 38));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                    g2.setColor(new Color(125, 211, 252, 130));
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, getHeight(), getHeight()));
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            tag.setOpaque(false);
            tag.setFont(new Font(Theme.fontTiny().getFamily(), Font.BOLD, 10));
            tag.setForeground(new Color(186, 230, 253));
            tag.setBorder(new EmptyBorder(4, 10, 4, 10));
            return tag;
        }

        private static JPanel createFeaturePill(AppIcons.Glyph glyph, String text) {
            JPanel p = new JPanel(new BorderLayout(10, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(255, 255, 255, 16));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                    g2.setColor(new Color(255, 255, 255, 34));
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 12, 12));
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            p.setBorder(new EmptyBorder(9, 12, 9, 12));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.setMaximumSize(new Dimension(340, 42));

            JLabel ic = new JLabel(AppIcons.vectorIcon(glyph, 16, new Color(110, 231, 183)));
            JLabel lbl = new JLabel(text);
            lbl.setFont(Theme.fontSmall());
            lbl.setForeground(new Color(226, 232, 240));
            p.add(ic, BorderLayout.WEST);
            p.add(lbl, BorderLayout.CENTER);
            return p;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Nền gradient sâu
            LinearGradientPaint base = new LinearGradientPaint(
                    0, 0, w, h,
                    new float[]{0f, 0.52f, 1f},
                    new Color[]{new Color(9, 14, 34), new Color(21, 27, 68), new Color(10, 52, 88)}
            );
            g2.setPaint(base);
            g2.fillRect(0, 0, w, h);

            // 3 vầng cực quang chuyển động mượt
            float x1 = w * 0.82f + (float) Math.cos(phase * 0.7) * 34f;
            float y1 = h * 0.16f + (float) Math.sin(phase * 0.9) * 28f;
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(x1, y1), Math.max(180f, w * 0.58f),
                    new float[]{0f, 1f},
                    new Color[]{new Color(56, 189, 248, 86), new Color(56, 189, 248, 0)}));
            g2.fillRect(0, 0, w, h);

            float x2 = w * 0.18f + (float) Math.sin(phase * 0.6) * 26f;
            float y2 = h * 0.84f + (float) Math.cos(phase * 0.8) * 24f;
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(x2, y2), Math.max(170f, w * 0.55f),
                    new float[]{0f, 1f},
                    new Color[]{new Color(139, 92, 246, 82), new Color(139, 92, 246, 0)}));
            g2.fillRect(0, 0, w, h);

            float x3 = w * 0.55f + (float) Math.cos(phase * 0.45 + 1.4) * 30f;
            float y3 = h * 0.58f + (float) Math.sin(phase * 0.55 + 0.8) * 22f;
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(x3, y3), Math.max(140f, w * 0.42f),
                    new float[]{0f, 1f},
                    new Color[]{new Color(16, 185, 129, 44), new Color(16, 185, 129, 0)}));
            g2.fillRect(0, 0, w, h);

            // Lưới chấm kiến trúc mảnh
            g2.setColor(new Color(255, 255, 255, 14));
            for (int gx = 20; gx < w; gx += 26) {
                for (int gy = 20; gy < h; gy += 26) {
                    g2.fillRect(gx, gy, 2, 2);
                }
            }

            // Vệt sáng chéo mảnh sang trọng
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(186, 230, 253, 22));
            g2.drawLine(-40, (int) (h * 0.28), w + 40, (int) (h * 0.04));
            g2.drawLine(-40, (int) (h * 0.92), w + 40, (int) (h * 0.66));

            g2.dispose();
        }
    }

    /**
     * Thẻ nội dung bo góc đổ bóng đa lớp kiểu glass/surface, hỗ trợ hiệu ứng rung ngang khi nhập sai.
     */
    public static class ElevatedCardPanel extends JPanel {
        private int shakeOffsetX = 0;
        private Timer shakeTimer;
        private int shakeStep = 0;
        private final int radius;

        public ElevatedCardPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
            setBorder(new EmptyBorder(26, 30, 24, 30));
        }

        public void triggerShake() {
            if (shakeTimer != null && shakeTimer.isRunning()) {
                shakeTimer.stop();
            }
            shakeStep = 0;
            shakeTimer = new Timer(22, e -> {
                shakeStep++;
                if (shakeStep >= 14) {
                    shakeOffsetX = 0;
                    shakeTimer.stop();
                } else {
                    double decay = 1.0 - (shakeStep / 14.0);
                    shakeOffsetX = (int) Math.round(Math.sin(shakeStep * 1.8) * 9.0 * decay);
                }
                repaint();
            });
            shakeTimer.start();
        }

        public void stopAnimation() {
            if (shakeTimer != null && shakeTimer.isRunning()) {
                shakeTimer.stop();
            }
            shakeOffsetX = 0;
        }

        @Override
        public void removeNotify() {
            stopAnimation();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int pad = 10;
            int x = pad + shakeOffsetX;
            int y = pad;
            int w = getWidth() - pad * 2;
            int h = getHeight() - pad * 2;

            // Đổ bóng mềm 5 lớp
            for (int i = 5; i >= 1; i--) {
                int alpha = Theme.isDark() ? (16 - i * 2) : (13 - i * 2);
                g2.setColor(new Color(15, 23, 42, Math.max(2, alpha)));
                g2.fill(new RoundRectangle2D.Float(x - i, y + i * 0.9f, w + i * 2, h + i * 1.4f, radius + i, radius + i));
            }

            // Nền thẻ
            g2.setColor(Theme.bgSurface());
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, radius, radius));

            // Viền tinh tế
            g2.setColor(Theme.border());
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, radius, radius));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Khung bao ô nhập liệu bo góc có icon dẫn đầu, placeholder mịn, hiệu ứng viền sáng khi focus,
     * báo lỗi mượt và nút con mắt ẩn/hiện mật khẩu (không phá thứ tự điều hướng Tab).
     */
    public static class ModernFieldWrapper extends JPanel {
        private final JTextField field;
        private final AppIcons.Glyph leadingGlyph;
        private final String placeholder;
        private float focusProgress = 0f;
        private boolean focused = false;
        private boolean errorState = false;
        private boolean passwordVisible = false;
        private char defaultEchoChar = '•';
        private Timer focusTimer;
        private JLabel iconLabel;
        private JButton eyeButton;

        public ModernFieldWrapper(JTextField field, AppIcons.Glyph leadingGlyph, String placeholder) {
            this.field = field;
            this.leadingGlyph = leadingGlyph;
            this.placeholder = placeholder;

            setLayout(new BorderLayout(8, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(5, 12, 5, 10));
            setPreferredSize(new Dimension(280, 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            field.setOpaque(false);
            field.setBorder(new EmptyBorder(2, 0, 2, 0));
            field.setFont(Theme.fontRegular());
            field.setForeground(Theme.textPrimary());
            field.setCaretColor(Theme.primaryText());

            iconLabel = new JLabel(AppIcons.vectorIcon(leadingGlyph, 16, Theme.textMuted()));
            add(iconLabel, BorderLayout.WEST);

            // Overlay để vẽ placeholder khi ô rỗng mà không thay đổi giá trị thật của JTextField
            JPanel fieldContainer = new JPanel(new BorderLayout()) {
                @Override
                protected void paintChildren(Graphics g) {
                    super.paintChildren(g);
                    if (field.getDocument().getLength() == 0 && placeholder != null && !placeholder.isEmpty()) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setFont(Theme.fontRegular());
                        g2.setColor(Theme.textMuted());
                        FontMetrics fm = g2.getFontMetrics();
                        int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                        g2.drawString(placeholder, 2, y);
                        g2.dispose();
                    }
                }
            };
            fieldContainer.setOpaque(false);
            fieldContainer.add(field, BorderLayout.CENTER);
            add(fieldContainer, BorderLayout.CENTER);

            if (field instanceof JPasswordField pf) {
                defaultEchoChar = pf.getEchoChar() == 0 ? '•' : pf.getEchoChar();
                eyeButton = new JButton(AppIcons.vectorIcon(AppIcons.Glyph.EYE, 16, Theme.textMuted()));
                eyeButton.setUI(new BasicButtonUI());
                eyeButton.setOpaque(false);
                eyeButton.setContentAreaFilled(false);
                eyeButton.setBorderPainted(false);
                eyeButton.setFocusPainted(false);
                eyeButton.setFocusable(false); // Giữ nguyên thứ tự Tab chuẩn
                eyeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                eyeButton.setToolTipText("Hiện / ẩn mật khẩu");
                eyeButton.setBorder(new EmptyBorder(2, 4, 2, 2));
                eyeButton.addActionListener(e -> {
                    passwordVisible = !passwordVisible;
                    pf.setEchoChar(passwordVisible ? (char) 0 : defaultEchoChar);
                    updateIcons();
                });
                add(eyeButton, BorderLayout.EAST);
            }

            field.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { focused = true; animateFocus(); updateIcons(); }
                @Override public void focusLost(FocusEvent e)   { focused = false; animateFocus(); updateIcons(); }
            });

            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e)  { clearErrorOnType(); repaint(); }
                @Override public void removeUpdate(DocumentEvent e)  { clearErrorOnType(); repaint(); }
                @Override public void changedUpdate(DocumentEvent e) { clearErrorOnType(); repaint(); }
            });

            // Bấm vào bất kỳ vùng nào trong khung bo góc đều focus thẳng vào text field
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { field.requestFocusInWindow(); }
            });
        }

        private void clearErrorOnType() {
            if (errorState) {
                errorState = false;
                updateIcons();
                repaint();
            }
        }

        public void setError(boolean error) {
            this.errorState = error;
            updateIcons();
            repaint();
        }

        public void refreshTheme() {
            field.setForeground(Theme.textPrimary());
            field.setCaretColor(Theme.primaryText());
            updateIcons();
            repaint();
        }

        private void updateIcons() {
            Color iconColor = errorState
                    ? Theme.danger()
                    : (focused ? Theme.primaryText() : Theme.textMuted());
            iconLabel.setIcon(AppIcons.vectorIcon(leadingGlyph, 16, iconColor));
            if (eyeButton != null) {
                eyeButton.setIcon(AppIcons.vectorIcon(
                        passwordVisible ? AppIcons.Glyph.EYE_OFF : AppIcons.Glyph.EYE,
                        16,
                        focused ? Theme.textPrimary() : Theme.textMuted()
                ));
            }
        }

        private void animateFocus() {
            if (focusTimer != null && focusTimer.isRunning()) {
                return;
            }
            focusTimer = new Timer(16, e -> {
                float target = focused ? 1f : 0f;
                float step = 0.16f;
                if (Math.abs(focusProgress - target) <= step) {
                    focusProgress = target;
                    focusTimer.stop();
                } else {
                    focusProgress += (focusProgress < target ? step : -step);
                }
                repaint();
            });
            focusTimer.start();
        }

        public void stopAnimation() {
            if (focusTimer != null && focusTimer.isRunning()) {
                focusTimer.stop();
            }
        }

        @Override
        public void removeNotify() {
            stopAnimation();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int pad = 3;
            float x = pad;
            float y = pad;
            float w = getWidth() - pad * 2f;
            float h = getHeight() - pad * 2f;
            float arc = 12f;

            // Vầng sáng focus hoặc lỗi phía ngoài viền
            if (errorState) {
                g2.setColor(new Color(Theme.dangerBg().getRed(), Theme.dangerBg().getGreen(), Theme.dangerBg().getBlue(), 42));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc + 4, arc + 4));
            } else if (focusProgress > 0.01f) {
                Color ring = Theme.primary();
                int alpha = Math.round(48 * focusProgress);
                g2.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), alpha));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc + 4, arc + 4));
            }

            // Nền ô nhập
            Color inputBg = focused ? Theme.bgSurface() : Theme.bgSurfaceAlt();
            g2.setColor(inputBg);
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, arc, arc));

            // Viền chuyển màu mượt theo trạng thái
            Color borderColor = errorState
                    ? Theme.danger()
                    : blend(Theme.border(), Theme.primaryText(), focusProgress);
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(errorState || focusProgress > 0.5f ? 1.6f : 1.1f));
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, arc, arc));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Nút hành động chính với nền gradient mượt, hiệu ứng chuyển màu hover, nhấn nhẹ 1px và spinner quay khi đang tải.
     */
    public static class AnimatedGradientButton extends JButton {
        private float hoverProgress = 0f;
        private boolean hovered = false;
        private boolean pressed = false;
        private boolean loading = false;
        private int spinnerAngle = 0;
        private Timer animTimer;

        public AnimatedGradientButton(String text) {
            super(text);
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setFont(new Font(Theme.fontBold().getFamily(), Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(11, 20, 11, 20));
            setPreferredSize(new Dimension(260, 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e)  { hovered = true; ensureAnim(); }
                @Override public void mouseExited(MouseEvent e)   { hovered = false; pressed = false; ensureAnim(); }
                @Override public void mousePressed(MouseEvent e)  { if (isEnabled()) { pressed = true; repaint(); } }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }

        public void setLoading(boolean loading, String text) {
            this.loading = loading;
            setEnabled(!loading);
            setText(text);
            ensureAnim();
            repaint();
        }

        private void ensureAnim() {
            if (animTimer != null && animTimer.isRunning()) return;
            animTimer = new Timer(18, e -> {
                boolean needContinue = false;
                float target = hovered && isEnabled() ? 1f : 0f;
                if (Math.abs(hoverProgress - target) > 0.08f) {
                    hoverProgress += (hoverProgress < target ? 0.12f : -0.12f);
                    needContinue = true;
                } else {
                    hoverProgress = target;
                }
                if (loading) {
                    spinnerAngle = (spinnerAngle + 14) % 360;
                    needContinue = true;
                }
                repaint();
                if (!needContinue) {
                    animTimer.stop();
                }
            });
            animTimer.start();
        }

        public void stopAnimation() {
            if (animTimer != null && animTimer.isRunning()) {
                animTimer.stop();
            }
        }

        @Override
        public void removeNotify() {
            stopAnimation();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int yOff = pressed ? 1 : 0;
            float arc = 12f;

            // Đổ bóng xanh nhẹ dưới nút
            if (isEnabled()) {
                g2.setColor(new Color(18, 92, 194, Math.round(38 + 26 * hoverProgress)));
                g2.fill(new RoundRectangle2D.Float(3, 4 + yOff, w - 6, h - 4, arc, arc));
            }

            // Nền gradient (đạt WCAG AA >= 4.5:1 với chữ trắng)
            if (!isEnabled()) {
                g2.setColor(Theme.isDark() ? new Color(56, 65, 82) : new Color(110, 124, 144));
                g2.fill(new RoundRectangle2D.Float(0, yOff, w, h - 3, arc, arc));
            } else {
                Color left = blend(new Color(20, 96, 204), new Color(16, 82, 184), hoverProgress);
                Color right = blend(new Color(38, 66, 178), new Color(24, 118, 210), hoverProgress);
                g2.setPaint(new LinearGradientPaint(0, yOff, w, h, new float[]{0f, 1f}, new Color[]{left, right}));
                g2.fill(new RoundRectangle2D.Float(0, yOff, w, h - 3, arc, arc));

                // Đường ánh sáng mỏng cạnh trên
                g2.setPaint(new GradientPaint(0, yOff, new Color(255, 255, 255, 70), 0, h * 0.45f, new Color(255, 255, 255, 0)));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(1, yOff + 0.8f, w - 2, h - 4.6f, arc, arc));
            }

            // Viền focus khi điều hướng bằng bàn phím (Tab)
            if (isFocusOwner()) {
                g2.setColor(new Color(186, 230, 253));
                g2.setStroke(new BasicStroke(1.8f));
                g2.draw(new RoundRectangle2D.Float(1.5f, yOff + 1.5f, w - 3f, h - 6f, arc, arc));
            }

            // Chữ & vòng xoay tải
            FontMetrics fm = g2.getFontMetrics(getFont());
            String txt = getText() != null ? getText() : "";
            int textW = fm.stringWidth(txt);
            int spinnerSpace = loading ? 22 : 0;
            int totalW = textW + spinnerSpace;
            int startX = (w - totalW) / 2;
            int textY = yOff + (h - 3 + fm.getAscent() - fm.getDescent()) / 2;

            if (loading) {
                g2.setColor(new Color(255, 255, 255, 90));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(startX, yOff + (h - 3 - 14) / 2, 14, 14);
                g2.setColor(Color.WHITE);
                g2.drawArc(startX, yOff + (h - 3 - 14) / 2, 14, 14, -spinnerAngle, 110);
                startX += spinnerSpace;
            }

            g2.setFont(getFont());
            g2.setColor(Color.WHITE);
            g2.drawString(txt, startX, textY);

            g2.dispose();
        }
    }

    /**
     * Nút liên kết chữ mảnh có gạch chân chuyển động khi di chuột.
     */
    public static class AccentLinkButton extends JButton {
        private boolean hovered = false;

        public AccentLinkButton(String text) {
            super(text);
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(Theme.fontSmall().deriveFont(Font.BOLD));
            setForeground(Theme.primaryText());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(4, 6, 4, 6));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            setForeground(Theme.primaryText());
            super.paintComponent(g);
            if (hovered || isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.primaryText());
                g2.setStroke(new BasicStroke(1.2f));
                int y = getHeight() - 3;
                g2.drawLine(6, y, getWidth() - 6, y);
                g2.dispose();
            }
        }
    }

    /**
     * Nút dạng viên thuốc (pill ghost button) cho thao tác phụ như Kiểm tra kết nối hoặc Đổi chủ đề.
     */
    public static class PillGhostButton extends JButton {
        private boolean hovered = false;

        public PillGhostButton(String text, Icon icon) {
            super(text, icon);
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(Theme.fontTiny().deriveFont(Font.BOLD));
            setForeground(Theme.textSecondary());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 12, 6, 12));
            setIconTextGap(6);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            setForeground(hovered ? Theme.textPrimary() : Theme.textSecondary());
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = hovered ? Theme.bgHover() : Theme.bgSurfaceAlt();
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.setColor(isFocusOwner() ? Theme.primaryText() : Theme.border());
            g2.setStroke(new BasicStroke(isFocusOwner() ? 1.5f : 1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Nhãn tên trường đứng trên ô nhập liệu theo chuẩn thiết kế hiện đại.
     */
    public static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.fontSmall().deriveFont(Font.BOLD));
        l.setForeground(Theme.textPrimary());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /**
     * Đường kẻ phân cách mảnh có chữ ở giữa ("hoặc") cho màn hình đăng nhập.
     */
    public static JPanel orDivider(String labelText) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setFont(Theme.fontTiny());
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(labelText);
                int gap = 10;
                int midY = h / 2;
                int leftEnd = (w - tw) / 2 - gap;
                int rightStart = (w + tw) / 2 + gap;
                g2.setColor(Theme.border());
                if (leftEnd > 4) g2.drawLine(0, midY, leftEnd, midY);
                if (rightStart < w - 4) g2.drawLine(rightStart, midY, w, midY);
                g2.setColor(Theme.textMuted());
                g2.drawString(labelText, (w - tw) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(260, 20));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /**
     * Nút "Tiếp tục với Google" vẽ hoàn toàn bằng Java2D kèm biểu trưng Google "G" 4 màu sắc nét.
     */
    public static class GoogleSignInButton extends JButton {
        private boolean hovered = false;
        private boolean pressed = false;

        public GoogleSignInButton(String text) {
            super(text, createGoogleGIcon(18));
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(new Font(Theme.fontBold().getFamily(), Font.BOLD, 13));
            setForeground(Theme.textPrimary());
            setIconTextGap(10);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 16, 10, 16));
            setPreferredSize(new Dimension(260, 42));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e)  { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)   { hovered = false; pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e)  { if (isEnabled()) { pressed = true; repaint(); } }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            setForeground(Theme.textPrimary());
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int yOff = pressed ? 1 : 0;
            float arc = 12f;

            Color bg = hovered ? Theme.bgHover() : Theme.bgSurfaceAlt();
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, yOff, w, h - 1, arc, arc));
            g2.setColor(isFocusOwner() ? Theme.primaryText() : Theme.border());
            g2.setStroke(new BasicStroke(isFocusOwner() ? 1.6f : 1.15f));
            g2.draw(new RoundRectangle2D.Float(0.6f, yOff + 0.6f, w - 1.2f, h - 2.2f, arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }

        public static Icon createGoogleGIcon(int size) {
            return new Icon() {
                @Override public int getIconWidth() { return size; }
                @Override public int getIconHeight() { return size; }
                @Override
                public void paintIcon(Component c, Graphics g0, int x, int y) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.translate(x, y);
                    double sc = size / 24.0;
                    g.scale(sc, sc);
                    BasicStroke st = new BasicStroke(4.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
                    g.setStroke(st);
                    // 4 cung màu chuẩn Google G (Đỏ - Vàng - Xanh lá - Xanh dương)
                    g.setColor(new Color(234, 67, 53));   // Red (top)
                    g.draw(new Arc2D.Double(3, 3, 18, 18, 42, 102, Arc2D.OPEN));
                    g.setColor(new Color(251, 188, 5));   // Yellow (left)
                    g.draw(new Arc2D.Double(3, 3, 18, 18, 144, 72, Arc2D.OPEN));
                    g.setColor(new Color(52, 168, 83));   // Green (bottom)
                    g.draw(new Arc2D.Double(3, 3, 18, 18, 216, 98, Arc2D.OPEN));
                    g.setColor(new Color(66, 133, 244));  // Blue (right)
                    g.draw(new Arc2D.Double(3, 3, 18, 18, -46, 52, Arc2D.OPEN));
                    // Thanh ngang chữ G
                    g.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(12.5, 12.2, 20.6, 12.2));
                    g.dispose();
                }
            };
        }
    }
}

