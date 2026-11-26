package com.mailapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

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

// All identities come from the application's DB and JWT filter. Do not create
// Spring Boot's unused default user or print its generated password on startup.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class MailServerApplication {

    private static final Logger log = LoggerFactory.getLogger(MailServerApplication.class);

    public static void main(String[] args) {
        // 1. Tự động nạp biến môi trường từ file .env nếu có
        if (!"false".equalsIgnoreCase(System.getenv("MAILAPP_LOAD_DOTENV"))) {
            loadDotEnvIfPresent();
        }

        // 2. Kích hoạt profile 'dev' mặc định nếu chưa cấu hình
        SimpleCommandLinePropertySource commandLine = new SimpleCommandLinePropertySource(args);
        if (configured(commandLine, "spring.profiles.active", "SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", "dev");
            log.info("[ENV] Đã tự động kích hoạt profile 'dev' cho môi trường chạy trực tiếp.");
        }

        // 3. Đảm bảo cấu hình SSL keystore hợp lệ
        ensureKeystoreConfigured(commandLine);

        // 4. Kiểm tra cổng máy chủ (8443) có bị chiếm bởi container Docker hoặc tiến trình khác không
        int port = 8443;
        try {
            String portStr = configured(commandLine, "server.port", "SERVER_PORT");
            if (portStr != null) port = Integer.parseInt(portStr);
        } catch (Exception ignored) {}

        if (port != 0) verifyPortNotOccupied(port);

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
            log.warn("Không thể đọc file .env: {}", e.getClass().getSimpleName());
            return;
        }

        applyDotEnvProperties(envMap);
    }

    static void applyDotEnvProperties(Map<String, String> envMap) {
        for (Map.Entry<String, String> entry : envMap.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if (System.getenv(k) == null && System.getProperty(k) == null) {
                System.setProperty(k, v);
            }
        }

        // Secret aliases are resolved by the property placeholders in application.properties.
        // Promoting a legacy .env key to a Spring property would override MAILAPP_* values.
        if (envMap.containsKey("SPRING_PROFILES_ACTIVE") && System.getProperty("spring.profiles.active") == null && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            System.setProperty("spring.profiles.active", envMap.get("SPRING_PROFILES_ACTIVE"));
        }
    }

    private static String configured(SimpleCommandLinePropertySource args, String property, String... aliases) {
        String value = args.getProperty(property);
        if (value != null) return value;
        value = System.getProperty(property);
        if (value != null) return value;
        for (String alias : aliases) {
            value = System.getProperty(alias);
            if (value == null) value = System.getenv(alias);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static void ensureKeystoreConfigured(SimpleCommandLinePropertySource args) {
        if ("false".equalsIgnoreCase(configured(args, "server.ssl.enabled", "SERVER_SSL_ENABLED"))) return;
        String explicitStore = configured(args, "server.ssl.key-store", "SERVER_SSL_KEY_STORE", "MAILAPP_KEYSTORE_PATH");
        // Let Spring validate an explicit file/classpath URI and password; never silently replace it.
        if (explicitStore != null && !explicitStore.isBlank()) return;
        String profiles = configured(args, "spring.profiles.active", "SPRING_PROFILES_ACTIVE");
        if (profiles == null || !java.util.Arrays.asList(profiles.split(",")).contains("dev")) return;
        String keyStorePassword = configured(args, "server.ssl.key-store-password",
                "SERVER_SSL_KEY_STORE_PASSWORD", "MAILAPP_KEYSTORE_PASS", "KEYSTORE_PASSWORD");
        if (keyStorePassword == null) keyStorePassword = "";

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
                System.setProperty("server.ssl.key-store", candidate.toURI().toString());
                return;
            }
        }

        if (keyStorePassword.isEmpty()) {
            keyStorePassword = java.util.UUID.randomUUID().toString();
            System.setProperty("server.ssl.key-store-password", keyStorePassword);
            System.setProperty("KEYSTORE_PASSWORD", keyStorePassword);
        }
        try {
            File tempDir = java.nio.file.Files.createTempDirectory("mailapp-dev-keystore-").toFile();
            tempDir.deleteOnExit();
            File tempKeystore = new File(tempDir, "keystore.p12");
            tempKeystore.deleteOnExit();
            generateSelfSignedKeystore(tempKeystore, keyStorePassword);
            System.setProperty("server.ssl.key-store", tempKeystore.toURI().toString());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create isolated development keystore", e);
        }
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
            if (p.waitFor() != 0 || !targetFile.isFile()) {
                throw new IllegalStateException("keytool failed to generate development keystore");
            }
            log.info("[DEV SSL] Đã tự động sinh keystore SSL tự ký tại: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Không thể sinh keystore tạm thời", e);
        }
    }
}
