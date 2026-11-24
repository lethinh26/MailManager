package com.mailapp.client.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * Tạo Avatar vòng tròn chữ cái đầu + màu cố định theo hash email.
 * Kiểm tra độ tương phản chữ (WCAG >= 4.5:1), không gọi dịch vụ ngoài (không Gravatar).
 */
public final class AvatarUtil {

    // Bảng màu hài hòa đã chọn lọc đảm bảo tương phản tốt với chữ trắng
    private static final Color[] PALETTE = {
            new Color(26, 115, 232),  // Blue
            new Color(14, 116, 144),  // Cyan
            new Color(21, 128, 61),   // Green
            new Color(124, 58, 237),  // Violet
            new Color(190, 24, 93),   // Rose
            new Color(194, 65, 12),   // Orange
            new Color(67, 56, 202),   // Indigo
            new Color(15, 118, 110),  // Teal
            new Color(161, 98, 7),    // Amber Dark
            new Color(185, 28, 28)    // Red
    };

    private AvatarUtil() {}

    public static Color colorForKey(String emailOrName) {
        if (emailOrName == null || emailOrName.isBlank()) {
            return PALETTE[0];
        }
        int hash = Math.abs(emailOrName.trim().toLowerCase().hashCode());
        return PALETTE[hash % PALETTE.length];
    }

    public static String initialsFor(String displayName, String email) {
        String source = (displayName != null && !displayName.isBlank()) ? displayName.trim() : (email != null ? email.trim() : "?");
        if (source.contains("@")) {
            source = source.substring(0, source.indexOf('@'));
        }
        String[] parts = source.split("[\\s._-]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && Character.isLetterOrDigit(p.charAt(0))) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (sb.length() == 2) break;
            }
        }
        if (sb.isEmpty()) {
            return "?";
        }
        return sb.toString();
    }

    /**
     * Tính tỷ lệ tương phản WCAG 2.1 giữa 2 màu (>= 4.5:1 là đạt chuẩn AA).
     */
    public static double contrastRatio(Color c1, Color c2) {
        double l1 = relativeLuminance(c1);
        double l2 = relativeLuminance(c2);
        double bright = Math.max(l1, l2);
        double dark = Math.min(l1, l2);
        return (bright + 0.05) / (dark + 0.05);
    }

    public static Color bestTextColor(Color background) {
        return contrastRatio(background, Color.WHITE) >= 4.5 ? Color.WHITE : new Color(20, 24, 31);
    }

    private static double relativeLuminance(Color c) {
        double r = linearize(c.getRed() / 255.0);
        double g = linearize(c.getGreen() / 255.0);
        double b = linearize(c.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double ch) {
        return ch <= 0.03928 ? ch / 12.92 : Math.pow((ch + 0.055) / 1.055, 2.4);
    }

    public static Icon createAvatarIcon(String displayName, String email, int diameter) {
        String key = (email != null && !email.isBlank()) ? email : displayName;
        Color bg = colorForKey(key);
        Color fg = bestTextColor(bg);
        String initials = initialsFor(displayName, email);

        return new Icon() {
            @Override public int getIconWidth() { return diameter; }
            @Override public int getIconHeight() { return diameter; }
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Theme.applyTextHints(g);

                g.setColor(bg);
                g.fill(new Ellipse2D.Double(x, y, diameter, diameter));

                g.setColor(fg);
                Font f = new Font("Segoe UI", Font.BOLD, Math.max(10, (int) (diameter * 0.42)));
                g.setFont(f);
                FontMetrics fm = g.getFontMetrics();
                int tx = x + (diameter - fm.stringWidth(initials)) / 2;
                int ty = y + (diameter - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(initials, tx, ty);
                g.dispose();
            }
        };
    }

    /**
     * Cắt tròn và thu phóng ảnh đại diện thật (từ Google qua Server proxy) sắc nét bằng Java2D.
     */
    public static Icon createCircularPhotoIcon(BufferedImage photo, int diameter) {
        if (photo == null || diameter <= 0) {
            return createAvatarIcon("?", "", diameter);
        }
        BufferedImage out = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int pw = photo.getWidth();
        int ph = photo.getHeight();
        int side = Math.min(pw, ph);
        int sx = (pw - side) / 2;
        int sy = (ph - side) / 2;

        g.setClip(new Ellipse2D.Double(0, 0, diameter, diameter));
        g.drawImage(photo, 0, 0, diameter, diameter, sx, sy, sx + side, sy + side, null);
        g.setClip(null);
        g.setColor(new Color(148, 163, 184, 95));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Ellipse2D.Double(0.5, 0.5, diameter - 1.0, diameter - 1.0));
        g.dispose();
        return new ImageIcon(out);
    }
}

