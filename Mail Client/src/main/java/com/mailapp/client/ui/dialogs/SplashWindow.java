package com.mailapp.client.ui.dialogs;

import com.mailapp.client.ui.theme.AppIcons;
import com.mailapp.client.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Màn hình khởi động (Splash Screen) nền cực quang sang trọng thuần Java2D của thương hiệu Veyra.
 */
public class SplashWindow extends JWindow {

    private final JLabel statusLabel;
    private float barOffset = 0f;
    private Timer barTimer;

    public SplashWindow() {
        JPanel root = new JPanel(new BorderLayout(0, Theme.SP_3)) {
            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth(), h = getHeight();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                LinearGradientPaint bg = new LinearGradientPaint(
                        0, 0, w, h,
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{new Color(9, 14, 34), new Color(22, 28, 72), new Color(11, 58, 96)}
                );
                g2.setPaint(bg);
                g2.fillRect(0, 0, w, h);

                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.8f, h * 0.2f), 170f,
                        new float[]{0f, 1f}, new Color[]{new Color(56, 189, 248, 75), new Color(56, 189, 248, 0)}));
                g2.fillRect(0, 0, w, h);

                g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.2f, h * 0.85f), 160f,
                        new float[]{0f, 1f}, new Color[]{new Color(139, 92, 246, 70), new Color(139, 92, 246, 0)}));
                g2.fillRect(0, 0, w, h);

                g2.setColor(new Color(125, 211, 252, 95));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(0, 0, w - 1, h - 1);
                g2.dispose();
            }
        };
        root.setBorder(new EmptyBorder(Theme.SP_6, Theme.SP_6, Theme.SP_5, Theme.SP_6));

        JLabel logo = new JLabel(AppIcons.logoIcon(76), SwingConstants.CENTER);
        JLabel title = new JLabel("V E Y R A", SwingConstants.CENTER);
        title.setFont(new Font(Theme.fontDisplay().getFamily(), Font.BOLD, 26));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel(AppIcons.BRAND_TAGLINE, SwingConstants.CENTER);
        subtitle.setFont(Theme.fontSmall());
        subtitle.setForeground(new Color(203, 213, 225));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(logo);
        center.add(Box.createVerticalStrut(Theme.SP_3));
        center.add(title);
        center.add(Box.createVerticalStrut(Theme.SP_1));
        center.add(subtitle);

        statusLabel = new JLabel("Đang khởi tạo kết nối TLS bảo mật...", SwingConstants.CENTER);
        statusLabel.setFont(Theme.fontTiny());
        statusLabel.setForeground(new Color(148, 163, 184));

        // Thanh tiến trình chạy mượt bằng Java2D
        JPanel smoothBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(new Color(255, 255, 255, 26));
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
                float segW = w * 0.36f;
                float x = (barOffset * (w + segW)) - segW;
                g2.setPaint(new LinearGradientPaint(x, 0, x + segW, 0,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(56, 189, 248, 60), new Color(110, 231, 183), new Color(56, 189, 248, 60)}));
                g2.fill(new RoundRectangle2D.Float(Math.max(0, x), 0, Math.min(segW, w - Math.max(0, x)), h, h, h));
                g2.dispose();
            }
        };
        smoothBar.setOpaque(false);
        smoothBar.setPreferredSize(new Dimension(280, 5));

        barTimer = new Timer(18, e -> {
            barOffset += 0.032f;
            if (barOffset > 1f) barOffset = 0f;
            smoothBar.repaint();
        });
        barTimer.start();

        JPanel bottom = new JPanel(new BorderLayout(0, Theme.SP_2));
        bottom.setOpaque(false);
        bottom.add(smoothBar, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setSize(420, 255);
        setLocationRelativeTo(null);
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    public void showBriefly(Runnable onFinished) {
        setVisible(true);
        javax.swing.Timer t = new javax.swing.Timer(550, e -> {
            if (barTimer != null) barTimer.stop();
            dispose();
            if (onFinished != null) onFinished.run();
        });
        t.setRepeats(false);
        t.start();
    }

    @Override
    public void dispose() {
        if (barTimer != null && barTimer.isRunning()) barTimer.stop();
        super.dispose();
    }
}
