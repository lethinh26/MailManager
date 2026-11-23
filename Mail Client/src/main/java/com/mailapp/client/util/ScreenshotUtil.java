package com.mailapp.client.util;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Tiện ích chụp màn hình Swing component và gắn watermark "MÔ PHỎNG".
 * Chỉ ghi ảnh vào MỘT thư mục duy nhất: docs/screenshots/mockup/
 */
public class ScreenshotUtil {

    private static final String DEFAULT_OUTPUT_DIR = "docs/screenshots/mockup";

    public static File captureComponent(Component component, String filename) {
        return captureComponent(component, DEFAULT_OUTPUT_DIR, filename, true);
    }

    public static File captureComponent(Component component, String outputDirectory, String filename, boolean addMockWatermark) {
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        component.addNotify();
        component.validate();

        Component target = component;
        if (component instanceof RootPaneContainer rpc && rpc.getRootPane() != null) {
            target = rpc.getRootPane();
            target.validate();
        }

        int width = target.getWidth() > 0 ? target.getWidth() : component.getWidth();
        int height = target.getHeight() > 0 ? target.getHeight() : component.getHeight();

        if (width <= 0) width = 800;
        if (height <= 0) height = 600;

        double uiScale = 1.0;
        try {
            uiScale = Double.parseDouble(System.getProperty("sun.java2d.uiScale", "1.0"));
            if (uiScale <= 0 || uiScale > 4.0) uiScale = 1.0;
        } catch (NumberFormatException ignored) {}

        int imgW = (int) Math.round(width * uiScale);
        int imgH = (int) Math.round(height * uiScale);
        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        if (uiScale != 1.0) {
            g2d.scale(uiScale, uiScale);
        }
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Nền mặc định
        Color bg = target.getBackground() != null ? target.getBackground() : Color.WHITE;
        g2d.setColor(bg);
        g2d.fillRect(0, 0, width, height);

        target.paint(g2d);

        // YÊU CẦU: Gắn Watermark "MÔ PHỎNG" cho ảnh sinh từ chế độ chụp mẫu
        if (addMockWatermark) {
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 28));
            FontMetrics fm = g2d.getFontMetrics();
            String watermark = "[!] MÔ PHỎNG";
            int textWidth = fm.stringWidth(watermark);

            // Vẽ badge watermark nổi bật ở góc trên bên phải
            int badgeX = width - textWidth - 30;
            int badgeY = 15;
            g2d.setColor(new Color(220, 53, 69, 210)); // Màu đỏ bán trong suốt
            g2d.fillRoundRect(badgeX - 10, badgeY, textWidth + 20, 36, 8, 8);
            g2d.setColor(Color.WHITE);
            g2d.drawString(watermark, badgeX, badgeY + 26);
        }

        g2d.dispose();

        File outputFile = new File(dir, filename);
        try {
            ImageIO.write(image, "png", outputFile);
            System.out.println("[SCREENSHOT] Đã lưu ảnh: " + outputFile.getAbsolutePath());
            return outputFile;
        } catch (IOException e) {
            System.err.println("[SCREENSHOT LỖI] Không thể ghi ảnh " + filename + ": " + e.getMessage());
            return null;
        }
    }
}
