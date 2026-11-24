package com.mailapp.client.util;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Quản lý cấu hình cục bộ của Mail Client lưu trữ tại ~/.mailclient/config.properties.
 * Tuyệt đối KHÔNG lưu token JWT hay mật khẩu vào file cấu hình.
 * Hỗ trợ lưu trữ: server.url, cert.path, kích thước/vị trí cửa sổ.
 */
public class ClientConfig {

    private static final String DEFAULT_SERVER_URL = "https://localhost:8443";
    private static final String DEFAULT_CERT_PATH = "certs/mailapp.cer";
    private static volatile ClientConfig instance;

    private final File configFile;
    private final Properties properties = new Properties();

    private ClientConfig() {
        String userHome = System.getProperty("user.home", ".");
        File configDir = new File(userHome, ".mailclient");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        configFile = new File(configDir, "config.properties");
        loadConfig();
    }

    public static ClientConfig getInstance() {
        if (instance == null) {
            synchronized (ClientConfig.class) {
                if (instance == null) {
                    instance = new ClientConfig();
                }
            }
        }
        return instance;
    }

    public void loadConfig() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("[CONFIG] Lỗi khi nạp " + configFile.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }

    public void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            // Không bao giờ ghi token hay password
            properties.store(fos, "Mail App Client Configuration (DO NOT STORE PASSWORDS OR TOKENS)");
        } catch (IOException e) {
            System.err.println("[CONFIG] Lỗi khi lưu cấu hình: " + e.getMessage());
        }
    }

    public String getServerUrl() {
        String cliProp = System.getProperty("mailapp.server.url");
        if (cliProp != null && !cliProp.isBlank()) {
            return cliProp;
        }
        return properties.getProperty("server.url", DEFAULT_SERVER_URL);
    }

    public void setServerUrl(String serverUrl) {
        if (serverUrl != null && !serverUrl.isBlank()) {
            properties.setProperty("server.url", serverUrl.trim());
            saveConfig();
        }
    }

    public String getCertPath() {
        return properties.getProperty("cert.path", DEFAULT_CERT_PATH);
    }

    public void setCertPath(String certPath) {
        if (certPath != null && !certPath.isBlank()) {
            properties.setProperty("cert.path", certPath.trim());
            saveConfig();
        }
    }

    public int getWindowWidth(int defaultVal) {
        try {
            return Integer.parseInt(properties.getProperty("window.width", String.valueOf(defaultVal)));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public int getWindowHeight(int defaultVal) {
        try {
            return Integer.parseInt(properties.getProperty("window.height", String.valueOf(defaultVal)));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public void saveWindowBounds(int width, int height, int x, int y) {
        properties.setProperty("window.width", String.valueOf(width));
        properties.setProperty("window.height", String.valueOf(height));
        properties.setProperty("window.x", String.valueOf(x));
        properties.setProperty("window.y", String.valueOf(y));
        saveConfig();
    }

    // === Vị trí các thanh kéo (splitter) ===
    public int getSplit(String key, int defaultVal) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultVal)));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public void setSplit(String key, int pos) {
        if (pos <= 0) return;
        properties.setProperty(key, String.valueOf(pos));
        saveConfig();
    }

    public boolean isInsecureMode() {
        return Boolean.getBoolean("mailclient.insecure");
    }

    // === Chủ đề Sáng / Tối ===
    public String getTheme() {
        return properties.getProperty("ui.theme", "light");
    }

    public void setTheme(String theme) {
        properties.setProperty("ui.theme", "dark".equalsIgnoreCase(theme) ? "dark" : "light");
        saveConfig();
    }

    // === Thư mục tải về mặc định ===
    public String getDownloadDir() {
        String defaultDownloads = new File(System.getProperty("user.home", "."), "Downloads").getAbsolutePath();
        return properties.getProperty("download.dir", defaultDownloads);
    }

    public void setDownloadDir(String dir) {
        if (dir != null && !dir.isBlank()) {
            properties.setProperty("download.dir", dir.trim());
            saveConfig();
        }
    }

    // === Cấu hình Tự động dọn (Mặc định TẮT, không lưu token/mật khẩu) ===
    public boolean isAutoCleanEnabled() {
        return Boolean.parseBoolean(properties.getProperty("autoclean.enabled", "false"));
    }

    public void setAutoCleanEnabled(boolean enabled) {
        properties.setProperty("autoclean.enabled", String.valueOf(enabled));
        saveConfig();
    }

    public int getAutoCleanIntervalMinutes() {
        try { return Integer.parseInt(properties.getProperty("autoclean.interval.minutes", "15")); }
        catch (NumberFormatException e) { return 15; }
    }

    public void setAutoCleanIntervalMinutes(int minutes) {
        properties.setProperty("autoclean.interval.minutes", String.valueOf(Math.max(5, minutes)));
        saveConfig();
    }

    public boolean isAutoCleanDryRun() {
        return Boolean.parseBoolean(properties.getProperty("autoclean.dryrun", "true"));
    }

    public void setAutoCleanDryRun(boolean dryRun) {
        properties.setProperty("autoclean.dryrun", String.valueOf(dryRun));
        saveConfig();
    }

    public int getAutoCleanMaxPerRun() {
        try { return Integer.parseInt(properties.getProperty("autoclean.max.per.run", "10")); }
        catch (NumberFormatException e) { return 10; }
    }

    public void setAutoCleanMaxPerRun(int max) {
        properties.setProperty("autoclean.max.per.run", String.valueOf(Math.max(1, Math.min(100, max))));
        saveConfig();
    }

    public String getAutoCleanSenderFilter() {
        return properties.getProperty("autoclean.sender.filter", "");
    }

    public void setAutoCleanSenderFilter(String sender) {
        properties.setProperty("autoclean.sender.filter", sender != null ? sender.trim() : "");
        saveConfig();
    }

    public int getAutoCleanMinAgeDays() {
        try { return Integer.parseInt(properties.getProperty("autoclean.min.age.days", "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    public void setAutoCleanMinAgeDays(int days) {
        properties.setProperty("autoclean.min.age.days", String.valueOf(Math.max(0, days)));
        saveConfig();
    }

    public boolean isAutoCleanPermanent() {
        return Boolean.parseBoolean(properties.getProperty("autoclean.permanent", "false"));
    }

    public void setAutoCleanPermanent(boolean permanent) {
        properties.setProperty("autoclean.permanent", String.valueOf(permanent));
        saveConfig();
    }

    /**
     * Tìm kiếm file chứng chỉ .cer từ nhiều vị trí tiềm năng.
     */
    public File findCertFile() {
        String configured = getCertPath();
        // Nếu đang dùng giá trị mặc định và chạy từ thư mục gốc repo (VD: IntelliJ IDEA), ưu tiên chứng chỉ mới nhất của Mail Client
        if (DEFAULT_CERT_PATH.equals(configured)) {
            File clientLocal = new File("Mail Client/certs/mailapp.cer");
            if (clientLocal.exists()) return clientLocal;
        }

        File f = new File(configured);
        if (f.exists()) return f;

        // Thử các vị trí tương đối phổ biến
        String[] candidates = {
                "certs/mailapp.cer",
                "./certs/mailapp.cer",
                "Mail Client/certs/mailapp.cer",
                "Mail Server/certs/mailapp.cer",
                "../Mail Server/certs/mailapp.cer",
                "../certs/mailapp.cer",
                "../../certs/mailapp.cer",
                "Mail Server/src/main/resources/mailapp.cer",
                "../Mail Server/src/main/resources/mailapp.cer"
        };
        for (String c : candidates) {
            File cf = new File(c);
            if (cf.exists()) return cf;
        }
        return null;
    }

    /**
     * Tạo banner đỏ cảnh báo KẾT NỐI KHÔNG AN TOÀN khi chạy với -Dmailclient.insecure=true.
     */
    public static JPanel createInsecureBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(220, 53, 69)); // Bootstrap Danger Red
        banner.setBorder(new EmptyBorder(6, 12, 6, 12));

        JLabel label = new JLabel("[!] KET NOI KHONG AN TOAN (-Dmailclient.insecure=true dang bat, khong xac thuc chung chi)", SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(Color.WHITE);
        banner.add(label, BorderLayout.CENTER);
        return banner;
    }
}

