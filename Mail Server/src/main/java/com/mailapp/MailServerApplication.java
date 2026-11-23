package com.mailapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class MailServerApplication {

    private static final Logger log = LoggerFactory.getLogger(MailServerApplication.class);

    public static void main(String[] args) {
        // 1. Tự động nạp biến môi trường từ file .env nếu có
        loadDotEnvIfPresent();

        // 2. Kích hoạt profile 'dev' mặc định nếu chưa cấu hình
        if (System.getProperty("spring.profiles.active") == null && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", "dev");
            log.info("[ENV] Đã tự động kích hoạt profile 'dev' cho môi trường chạy trực tiếp.");
        }

        // 3. Đảm bảo cấu hình SSL keystore hợp lệ
        ensureKeystoreConfigured();

        // 4. Kiểm tra cổng máy chủ (8443) có bị chiếm bởi container Docker hoặc tiến trình khác không
        int port = 8443;
        try {
            String portStr = System.getProperty("server.port", System.getenv().getOrDefault("SERVER_PORT", "8443"));
            port = Integer.parseInt(portStr);
        } catch (Exception ignored) {}

        verifyPortNotOccupied(port);

        // 5. Khởi động Spring Boot Application
        SpringApplication.run(MailServerApplication.class, args);
    }

    private static void verifyPortNotOccupied(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            // Kết nối thành công chứng tỏ cổng đã có tiến trình khác lắng nghe
            System.err.println("================================================================================");
            System.err.println("Cổng " + port + " đang bị chiếm, chạy docker compose stop mail-server");
            System.err.println("================================================================================");
            System.exit(1);
        } catch (IOException ignored) {
            // Cổng đang rảnh, sẵn sàng lắng nghe
        }
    }

    private static void loadDotEnvIfPresent() {
        List<File> candidates = List.of(
                new File(".env"),
                new File("Mail Server/.env"),
                new File("../Mail Server/.env"),
                new File("../.env")
        );

        File envFile = null;
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                envFile = f;
                break;
            }
        }

        if (envFile == null) {
            return;
        }

        Map<String, String> envMap = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    envMap.put(key, val);
                }
            }
        } catch (IOException e) {
            log.warn("Không thể đọc file .env: {}", e.getMessage());
            return;
        }

        for (Map.Entry<String, String> entry : envMap.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if (System.getenv(k) == null && System.getProperty(k) == null) {
                System.setProperty(k, v);
            }
        }

        if (envMap.containsKey("KEYSTORE_PASSWORD") && System.getProperty("server.ssl.key-store-password") == null && System.getenv("KEYSTORE_PASSWORD") == null) {
            System.setProperty("server.ssl.key-store-password", envMap.get("KEYSTORE_PASSWORD"));
        }
        if (envMap.containsKey("SPRING_PROFILES_ACTIVE") && System.getProperty("spring.profiles.active") == null && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", envMap.get("SPRING_PROFILES_ACTIVE"));
        }
        if (envMap.containsKey("JWT_SECRET") && System.getProperty("mailapp.jwt.secret") == null && System.getenv("JWT_SECRET") == null) {
            System.setProperty("mailapp.jwt.secret", envMap.get("JWT_SECRET"));
        }
        if (envMap.containsKey("AES_KEY") && System.getProperty("mailapp.aes.key") == null && System.getenv("AES_KEY") == null) {
            System.setProperty("mailapp.aes.key", envMap.get("AES_KEY"));
        }
    }

    private static void ensureKeystoreConfigured() {
        String keyStorePassword = System.getProperty("server.ssl.key-store-password",
                System.getenv().getOrDefault("KEYSTORE_PASSWORD", ""));

        // 1. Tôn trọng đường dẫn keystore đã cấu hình tường minh (-Dserver.ssl.key-store=file:/app/certs/keystore.p12)
        String explicitStore = System.getProperty("server.ssl.key-store",
                System.getenv().getOrDefault("SERVER_SSL_KEY_STORE", ""));
        if (explicitStore != null && !explicitStore.isBlank() && !keyStorePassword.isEmpty()) {
            String normalizedPath = explicitStore.startsWith("file:")
                    ? explicitStore.substring("file:".length())
                    : explicitStore;
            if (new File(normalizedPath).exists()) {
                return;
            }
        }

        // 2. Tìm keystore chuẩn trong certs/ hoặc src/main/resources/
        File[] candidates = new File[]{
                new File("certs/keystore.p12"),
                new File("Mail Server/certs/keystore.p12"),
                new File("src/main/resources/keystore.p12"),
                new File("Mail Server/src/main/resources/keystore.p12"),
                new File("../Mail Server/src/main/resources/keystore.p12")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && !keyStorePassword.isEmpty()) {
                if (candidate.getPath().contains("certs")) {
                    System.setProperty("server.ssl.key-store", "file:" + candidate.getAbsolutePath());
                }
                return;
            }
        }

        if (keyStorePassword.isEmpty()) {
            keyStorePassword = java.util.UUID.randomUUID().toString();
            System.setProperty("server.ssl.key-store-password", keyStorePassword);
            System.setProperty("KEYSTORE_PASSWORD", keyStorePassword);
            File tempKeystore = new File(System.getProperty("java.io.tmpdir"), "mailapp-dev-keystore.p12");
            if (tempKeystore.exists()) {
                tempKeystore.delete();
            }
        }

        File tempKeystore = new File(System.getProperty("java.io.tmpdir"), "mailapp-dev-keystore.p12");
        if (!tempKeystore.exists()) {
            generateSelfSignedKeystore(tempKeystore, keyStorePassword);
        }
        System.setProperty("server.ssl.key-store", "file:" + tempKeystore.getAbsolutePath());
    }

    private static void generateSelfSignedKeystore(File targetFile, String storeCredential) {
        try {
            String javaHome = System.getProperty("java.home");
            String keytool = Path.of(javaHome, "bin", "keytool").toString();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                keytool += ".exe";
            }
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(keytool);
            cmd.add("-genkeypair");
            cmd.add("-alias"); cmd.add("mailapp");
            cmd.add("-keyalg"); cmd.add("RSA");
            cmd.add("-keysize"); cmd.add("2048");
            cmd.add("-validity"); cmd.add("365");
            cmd.add("-keystore"); cmd.add(targetFile.getAbsolutePath());
            cmd.add("-storepass:env"); cmd.add("TEMP_KS_PASS");
            cmd.add("-keypass:env"); cmd.add("TEMP_KS_PASS");
            cmd.add("-dname"); cmd.add("CN=MailApp Dev Server, OU=PTIT, O=FinalProject, L=Hanoi, C=VN");
            cmd.add("-ext"); cmd.add("SAN=dns:localhost,ip:127.0.0.1");
            cmd.add("-noprompt");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("TEMP_KS_PASS", storeCredential);
            Process p = pb.start();
            p.waitFor();
            log.info("[DEV SSL] Đã tự động sinh keystore SSL tự ký tại: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Không thể sinh keystore tạm thời: {}", e.getMessage());
        }
    }
}
