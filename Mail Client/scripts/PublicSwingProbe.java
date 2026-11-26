import com.mailapp.client.Main;
import com.mailapp.client.ui.frames.LoginFrame;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.imageio.ImageIO;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Independent public-only observation console. No account, auth endpoint,
 * credentials, session injection, private fixture manifest or personal config.
 * The endpoint exercises the real Client TLS/HTTP transport, not the BE.
 * Individual commands drive only the public LoginFrame controls.
 */
class PublicSwingProbe {
    private static Path root, evidence;
    private static SSLContext trusted, foreign;
    private static HttpsServer server;
    private static int port;
    private static final AtomicInteger publicRequests = new AtomicInteger();
    private static final AtomicInteger rejectedRequests = new AtomicInteger();
    private static final AtomicBoolean cleaned = new AtomicBoolean();
    private static volatile boolean slowResponse;
    private static long launchAt;
    private static final byte[] CONFIG = """
            {"registrationEnabled":true,"oauth2LinkEnabled":true,
             "appPasswordLinkEnabled":true,"maintenanceMode":false,
             "sendMailEnabled":true,"maxGmailPerUser":5,
             "maxAttachmentSizeMb":25,"mailPageSize":20}
            """.getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || GraphicsEnvironment.isHeadless()
                || Boolean.getBoolean("mailclient.insecure"))
            throw new IllegalStateException("Public probe requires desktop, secure TLS and new evidence directory");
        evidence = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectory(evidence);
        root = Files.createTempDirectory("mailapp-public-").toRealPath();
        try {
            System.setProperty("user.home", root.toString());
            System.setProperty("mailclient.config.dir", root.resolve("config").toString());
            trusted = generateCertificate("trusted");
            foreign = generateCertificate("foreign");
            startServer(trusted);
            System.setProperty("mailapp.server.url", "https://127.0.0.1:" + port);
            Files.createDirectory(root.resolve("config"));
            Files.writeString(root.resolve("config/config.properties"),
                    "cert.path=" + root.resolve("trusted.cer").toString().replace('\\', '/') + "\n");
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event instanceof WindowEvent w && (w.getID() == WindowEvent.WINDOW_OPENED
                        || w.getID() == WindowEvent.WINDOW_CLOSED)) {
                    String type = w.getWindow().getClass().getSimpleName();
                    report("WINDOW " + type + " " + (w.getID() == WindowEvent.WINDOW_OPENED ? "OPEN" : "CLOSE")
                            + " elapsedMs=" + (System.nanoTime() - launchAt) / 1_000_000);
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
            launch();
            // Bound the console lifetime even if the operator disconnects.
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(420_000); } catch (InterruptedException ignored) {}
                report("TIME LIMIT");
                cleanup();
                System.exit(2);
            });
            report("READY public-only loopback HTTPS; commands: inspect check narrow wide top bottom eye focus enter offline foreign online slow normal bad-startup quit");
            try (Scanner input = new Scanner(System.in, StandardCharsets.UTF_8)) {
                while (input.hasNextLine()) {
                    String command = input.nextLine().trim();
                    if (command.equals("quit")) break;
                    try {
                        switch (command) {
                            case "inspect" -> report(edt(PublicSwingProbe::inspect));
                            case "check" -> report(edt(() -> invokeButton("Kiểm tra kết nối máy chủ", false)));
                            case "narrow", "wide" -> report(edt(() -> {
                                LoginFrame f = login();
                                f.setSize(command.equals("narrow") ? new Dimension(440, 580) : new Dimension(940, 650));
                                f.validate();
                                return "RESIZE requested=" + f.getSize();
                            }));
                            case "top", "bottom" -> report(edt(() -> {
                                LoginFrame f = login();
                                JComponent control = (JComponent) f.getExpectedTabOrder().get(command.equals("top") ? 0 : 6);
                                control.scrollRectToVisible(new Rectangle(0, 0, control.getWidth(), control.getHeight()));
                                return "SCROLL " + command + " visible=" + control.getVisibleRect();
                            }));
                            case "eye" -> report(edt(() -> invokeButton("Hiện / ẩn mật khẩu", true)));
                            case "focus" -> report(edt(() -> {
                                JTextField u = (JTextField) login().getExpectedTabOrder().get(0);
                                return "FOCUS username requested=" + u.requestFocusInWindow();
                            }));
                            case "enter" -> report(edt(() -> {
                                JTextField u = (JTextField) login().getExpectedTabOrder().get(0);
                                if (!u.isFocusOwner() || !u.getText().isEmpty())
                                    throw new IllegalStateException("Only focused empty username allowed");
                                u.postActionEvent();
                                return "USERNAME action dispatched (Swing, not native key)";
                            }));
                            case "offline" -> { stopServer(); report("HTTPS listener stopped"); }
                            case "foreign" -> { stopServer(); startServer(foreign); report("HTTPS restarted with unrelated certificate"); }
                            case "online" -> { stopServer(); startServer(trusted); report("HTTPS restarted with original certificate"); }
                            case "slow" -> { slowResponse = true; report("Public response delay 7000 ms"); }
                            case "normal" -> { slowResponse = false; report("Public response delay disabled"); }
                            case "bad-startup" -> {
                                edt(() -> { login().dispose(); return null; });
                                Files.writeString(root.resolve("trusted.cer"), "invalid public certificate");
                                launch();
                                report("STARTUP with malformed fixture certificate");
                            }
                            default -> report("REJECTED unknown public command");
                        }
                    } catch (Exception ex) {
                        // Never dump stack traces, request bodies or environment.
                        report("ACTION FAILED " + ex.getClass().getSimpleName()
                                + (ex.getCause() == null ? "" : "/" + ex.getCause().getClass().getSimpleName()));
                    }
                }
            }
        } finally {
            cleanup();
        }
        System.exit(0);
    }

    private static SSLContext generateCertificate(String alias) throws Exception {
        char[] passphrase = UUID.randomUUID().toString().toCharArray();
        Path store = root.resolve(alias + ".p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        try {
            // Keep the ephemeral keystore passphrase out of process command-line arguments.
            runKeytool(passphrase, keytool, "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "1", "-storetype", "PKCS12", "-keystore", store.toString(),
                    "-storepass:env", KEYTOOL_ENV_NAME, "-keypass:env", KEYTOOL_ENV_NAME,
                    "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-noprompt");
            runKeytool(passphrase, keytool, "-exportcert", "-alias", alias, "-keystore", store.toString(),
                    "-storepass:env", KEYTOOL_ENV_NAME, "-file", root.resolve(alias + ".cer").toString());
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(store)) { ks.load(in, passphrase); }
            KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            km.init(ks, passphrase);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(km.getKeyManagers(), null, null);
            return ssl;
        } finally {
            java.util.Arrays.fill(passphrase, '\0');
            Files.deleteIfExists(store);
        }
    }
    private static final String KEYTOOL_ENV_NAME = "MAILAPP_PROBE_KEYSTORE_PASSPHRASE";

    private static void runKeytool(char[] passphrase, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p;
        builder.environment().put(KEYTOOL_ENV_NAME, new String(passphrase));
        try {
            p = builder.start();
        } finally {
            builder.environment().remove(KEYTOOL_ENV_NAME);
        }
        if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly(); throw new IllegalStateException("Fixture certificate timeout");
        }
        if (p.exitValue() != 0) throw new IllegalStateException("Fixture certificate creation failed");
    }
    private static void startServer(SSLContext ssl) throws Exception {
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        port = server.getAddress().getPort();
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("GET")
                        || !exchange.getRequestURI().toString().equals("/api/public/config")
                        || exchange.getRequestHeaders().containsKey("Authorization")) {
                    rejectedRequests.incrementAndGet();
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                publicRequests.incrementAndGet();
                if (slowResponse) {
                    try { Thread.sleep(7000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, CONFIG.length);
                exchange.getResponseBody().write(CONFIG);
            } finally { exchange.close(); }
        });
        server.start();
    }
    private static void stopServer() {
        if (server != null) { server.stop(0); server = null; }
    }
    private static void launch() {
        launchAt = System.nanoTime();
        Main.main(new String[0]);
    }
    private static LoginFrame login() {
        for (Window w : Window.getWindows())
            if (w instanceof LoginFrame f && f.isShowing()) return f;
        throw new IllegalStateException("No showing public login");
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeLater(task);
        return task.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    private static List<Component> all(Component root) {
        List<Component> result = new ArrayList<>();
        result.add(root);
        if (root instanceof Container c) for (Component child : c.getComponents()) result.addAll(all(child));
        return result;
    }
    private static String invokeButton(String name, boolean tooltip) {
        for (Component c : all(login())) if (c instanceof AbstractButton b && b.isShowing() && b.isEnabled()
                && name.equals(tooltip ? b.getToolTipText() : b.getText())) {
            if (tooltip && all(login()).stream().filter(JPasswordField.class::isInstance)
                    .map(JPasswordField.class::cast).anyMatch(p -> p.getPassword().length != 0))
                throw new IllegalStateException("Password must remain empty");
            var a = b.getAccessibleContext().getAccessibleAction();
            if (a == null || !a.doAccessibleAction(0)) throw new IllegalStateException("No accessible action");
            return "PUBLIC ACTION " + name;
        }
        throw new IllegalStateException("Public control unavailable");
    }
    private static String inspect() throws Exception {
        StringBuilder result = new StringBuilder("OBSERVE publicRequests=" + publicRequests
                + " rejectedRequests=" + rejectedRequests + " ");
        for (Window w : Window.getWindows()) if (w.isShowing()) {
            result.append("\n").append(w.getClass().getSimpleName()).append(" size=").append(w.getSize())
                    .append(" active=").append(w.isActive());
            if (w instanceof LoginFrame f) {
                result.append(" min=").append(f.getMinimumSize())
                        .append(" scale=").append(f.getGraphicsConfiguration().getDefaultTransform().getScaleX())
                        .append(" L&F=").append(UIManager.getLookAndFeel().getName());
                for (Component c : all(f)) if (c.isShowing()) {
                    if (c instanceof JPasswordField p)
                        result.append("\npassword empty=").append(p.getPassword().length == 0)
                                .append(" masked=").append(p.echoCharIsSet()).append(" focus=").append(p.isFocusOwner());
                    else if (c instanceof JTextField t)
                        result.append("\nusername empty=").append(t.getText().isEmpty()).append(" focus=").append(t.isFocusOwner());
                    else if (c instanceof AbstractButton b)
                        result.append("\nbutton ").append(b.getText()).append(" enabled=").append(b.isEnabled())
                                .append(" bounds=").append(b.getSize()).append(" visible=").append(b.getVisibleRect());
                    else if (c instanceof JLabel l && l.getText() != null && (l.getText().startsWith("[OK]") || l.getText().startsWith("[X]")
                            || l.getText().startsWith("Đang kiểm tra"))) {
                        result.append("\nstatus ").append(l.getText()).append(" preferred=").append(l.getPreferredSize())
                                .append(" actual=").append(l.getSize()).append(" visible=").append(l.getVisibleRect());
                    }
                }
                if (all(f).stream().filter(JTextField.class::isInstance).map(JTextField.class::cast)
                        .allMatch(t -> t instanceof JPasswordField p ? p.getPassword().length == 0 : t.getText().isEmpty())) {
                    BufferedImage img = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    try { f.printAll(g); } finally { g.dispose(); }
                    String name = "public-" + System.nanoTime() + ".png";
                    ImageIO.write(img, "png", evidence.resolve(name).toFile());
                    result.append("\nwindow-only render ").append(name);
                }
            } else if (w instanceof JDialog d) {
                // Only startup error; do not read arbitrary dialog text.
                result.append(" startupError=").append(d.getTitle().equals("Lỗi Khởi Động Client"));
            }
        }
        return result.toString();
    }
    private static synchronized void report(String line) {
        System.out.println(line);
        try { Files.writeString(evidence.resolve("observations.txt"), line + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception ignored) {}
    }
    private static void cleanup() {
        if (!cleaned.compareAndSet(false, true)) return;
        stopServer();
        try { edt(() -> { for (Window w : Window.getWindows()) w.dispose(); return null; }); }
        catch (Exception ignored) {}
        report("FINAL publicRequests=" + publicRequests + " rejectedRequests=" + rejectedRequests);
        if (root != null && root.getFileName().toString().startsWith("mailapp-public-")) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                report("TEMP CLEANUP OK");
            } catch (Exception e) { report("TEMP CLEANUP FAILED " + e.getClass().getSimpleName()); }
        }
    }
}
