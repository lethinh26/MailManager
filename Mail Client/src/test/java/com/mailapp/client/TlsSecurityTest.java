package com.mailapp.client;

import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử bảo mật TLS Client (Task 1 - Xác thực chứng chỉ & gỡ bỏ trust-all)")
class TlsSecurityTest {

    @TempDir
    static Path tempDir;

    private static File validKeystore;
    private static File validCer;
    private static File untrustedKeystore;
    private static File untrustedCer;
    private static File wrongSanKeystore;
    private static File wrongSanCer;

    private HttpsServer httpsServer;

    private static final String TEST_KEYSTORE_SECRET = java.util.UUID.randomUUID().toString().replace("-", "");

    @BeforeAll
    static void generateTestCertificates() throws Exception {
        validKeystore = tempDir.resolve("valid.p12").toFile();
        validCer = tempDir.resolve("valid.cer").toFile();
        untrustedKeystore = tempDir.resolve("untrusted.p12").toFile();
        untrustedCer = tempDir.resolve("untrusted.cer").toFile();
        wrongSanKeystore = tempDir.resolve("wrongsan.p12").toFile();
        wrongSanCer = tempDir.resolve("wrongsan.cer").toFile();

        // 1. Tạo cặp key/cert hợp lệ: SAN chứa localhost và 127.0.0.1
        runKeytool("-genkeypair", "-alias", "valid", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-keystore", validKeystore.getAbsolutePath(),
                "-storepass", TEST_KEYSTORE_SECRET, "-keypass", TEST_KEYSTORE_SECRET,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1");
        runKeytool("-exportcert", "-alias", "valid", "-keystore", validKeystore.getAbsolutePath(),
                "-storepass", TEST_KEYSTORE_SECRET, "-file", validCer.getAbsolutePath());

        // 2. Tạo cặp key/cert lạ (khác keypair, không được trust)
        runKeytool("-genkeypair", "-alias", "untrusted", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-keystore", untrustedKeystore.getAbsolutePath(),
                "-storepass", TEST_KEYSTORE_SECRET, "-keypass", TEST_KEYSTORE_SECRET,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1");
        runKeytool("-exportcert", "-alias", "untrusted", "-keystore", untrustedKeystore.getAbsolutePath(),
                "-storepass", TEST_KEYSTORE_SECRET, "-file", untrustedCer.getAbsolutePath());

        // 3. Tạo cặp key/cert sai SAN (chỉ chứa SAN cho host lạ, không có localhost)
        runKeytool("-genkeypair", "-alias", "wrongsan", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-keystore", wrongSanKeystore.getAbsolutePath(),
                "-storepass", TEST_KEYSTORE_SECRET, "-keypass", TEST_KEYSTORE_SECRET,
                "-dname", "CN=fakehost", "-ext", "SAN=dns:wronghost.example.com");
        runKeytool("-exportcert", "-alias", "wrongsan", "-keystore", wrongSanKeystore.getAbsolutePath(),
                "-storepass", TEST_KEYSTORE_SECRET, "-file", wrongSanCer.getAbsolutePath());
    }

    private static void runKeytool(String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String keytoolBin = Path.of(javaHome, "bin", "keytool").toString();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            keytoolBin += ".exe";
        }
        String[] cmd = new String[args.length + 2];
        cmd[0] = keytoolBin;
        System.arraycopy(args, 0, cmd, 1, args.length);
        cmd[cmd.length - 1] = "-noprompt";

        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        int exit = p.waitFor();
        if (exit != 0) {
            String err = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException("Keytool thất bại (code " + exit + "): " + err);
        }
    }

    private int startHttpsServer(File keystoreFile) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            ks.load(fis, TEST_KEYSTORE_SECRET.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, TEST_KEYSTORE_SECRET.toCharArray());

        SSLContext serverSsl = SSLContext.getInstance("TLS");
        serverSsl.init(kmf.getKeyManagers(), null, null);

        httpsServer = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSsl) {
            @Override
            public void configure(HttpsParameters params) {
                params.setSSLParameters(serverSsl.getDefaultSSLParameters());
            }
        });

        httpsServer.createContext("/api/public/config", exchange -> {
            String json = "{\"registrationEnabled\":true,\"maintenanceMode\":false,\"oauth2LinkEnabled\":true,\"appVersion\":\"1.0.0\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        httpsServer.start();
        return httpsServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (httpsServer != null) {
            httpsServer.stop(0);
        }
        System.clearProperty("mailclient.insecure");
    }

    @Test
    @DisplayName("TLS chuẩn: Kết nối THÀNH CÔNG khi Client nạp đúng chứng chỉ .cer của Server")
    void testValidCertificateConnectionSucceeds() throws Exception {
        int port = startHttpsServer(validKeystore);
        ApiClient client = new ApiClient("https://localhost:" + port, validCer);

        assertDoesNotThrow(() -> {
            long latency = client.pingServer();
            assertTrue(latency >= 0);
        });
    }

    @Test
    @DisplayName("TLS bảo mật: Bị TỪ CHỐI khi Server dùng chứng chỉ lạ không nằm trong .cer tin cậy")
    void testUntrustedCertificateConnectionRejected() throws Exception {
        int port = startHttpsServer(untrustedKeystore);
        // Client chỉ tin validCer, nhưng Server gửi untrusted cert
        ApiClient client = new ApiClient("https://localhost:" + port, validCer);

        Exception ex = assertThrows(Exception.class, client::pingServer);
        Throwable rootCause = ex;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertTrue(rootCause instanceof SSLHandshakeException ||
                        rootCause instanceof java.security.GeneralSecurityException ||
                        rootCause.getMessage().toLowerCase().contains("pkix") ||
                        rootCause.getMessage().toLowerCase().contains("signature") ||
                        rootCause.getMessage().toLowerCase().contains("certificate"),
                "Kết nối phải bị từ chối do chứng chỉ không được tin cậy! Thực tế: " + rootCause);
    }

    @Test
    @DisplayName("TLS bảo mật: Bị TỪ CHỐI khi hostname kết nối không khớp SAN trong chứng chỉ")
    void testWrongSanConnectionRejected() throws Exception {
        int port = startHttpsServer(wrongSanKeystore);
        // Dù Client có tin wrongSanCer, nhưng host kết nối là localhost trong khi SAN chỉ có wronghost.example.com
        ApiClient client = new ApiClient("https://localhost:" + port, wrongSanCer);

        Exception ex = assertThrows(Exception.class, client::pingServer);
        Throwable rootCause = ex;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertTrue(rootCause instanceof SSLHandshakeException ||
                        rootCause.getMessage().toLowerCase().contains("matching") ||
                        rootCause.getMessage().toLowerCase().contains("san") ||
                        rootCause.getMessage().toLowerCase().contains("name"),
                "Kết nối phải bị từ chối do sai SAN hostname! Thực tế: " + rootCause);
    }

    @Test
    @DisplayName("Chế độ insecure: Cho phép kết nối mọi chứng chỉ CHỈ khi có -Dmailclient.insecure=true")
    void testInsecureModeAllowsConnection() throws Exception {
        int port = startHttpsServer(untrustedKeystore);
        System.setProperty("mailclient.insecure", "true");

        ApiClient client = new ApiClient("https://localhost:" + port);

        assertDoesNotThrow(() -> {
            long latency = client.pingServer();
            assertTrue(latency >= 0);
        });
    }
}
