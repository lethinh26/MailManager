import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.server.AbstractServer;
import com.icegreen.greenmail.Managers;
import com.icegreen.greenmail.imap.ImapHostManager;
import com.icegreen.greenmail.user.UserManager;
import com.icegreen.greenmail.smtp.SmtpManager;
import com.icegreen.greenmail.smtp.SmtpState;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone, bounded by the parent fixture process. Never opens an external mail socket. */
class ManualGreenMail {
    private static final String[] USERS = {"ui_manual", "ui_other", "http_manual"};
    public static void main(String[] args) throws Exception {
        if (!"1".equals(System.getenv("MAILAPP_MANUAL_FIXTURE")) || args.length != 2)
            throw new IllegalStateException("Disposable fixture only");
        int imaps = Integer.parseInt(args[0]), smtp = Integer.parseInt(args[1]);
        if (imaps < 20000 || imaps >= 40000 || smtp < 20000 || smtp >= 40000 || imaps == smtp)
            throw new IllegalArgumentException("Isolated ports required");
        String pass = Objects.requireNonNull(System.getenv("MAILAPP_MANUAL_APP_PASSWORD"));
        ControlledGreenMail gm = new ControlledGreenMail(new ServerSetup[] {
            new ServerSetup(imaps, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS),
            new ServerSetup(smtp, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)
        });
        gm.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gm.closeStalls();
            gm.stop();
        }));
        for (String name : USERS) {
            String email = name + "@test.local";
            gm.setUser(email, pass);
            int count = name.equals("ui_other") ? 12 : 60;
            for (int i = 0; i < count; i++) {
                MimeMessage msg = new MimeMessage(gm.getSmtp().createSession());
                msg.setFrom(new InternetAddress("sender@test.local"));
                msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                msg.setSubject(String.format(Locale.ROOT, "manual-fixture %02d", i), "UTF-8");
                msg.setSentDate(new Date(System.currentTimeMillis() - (count - i) * 60000L));
                if (i == 5 || i == 7) {
                    MimeMultipart parts = new MimeMultipart("mixed");
                    MimeBodyPart body = new MimeBodyPart();
                    body.setText("N\u1ed9i dung fixture " + i, "UTF-8");
                    parts.addBodyPart(body);
                    parts.addBodyPart(file("fixture.txt", "text/plain", "first fixture bytes\n".getBytes(StandardCharsets.UTF_8)));
                    if (i == 5) {
                        parts.addBodyPart(file("fixture.txt", "text/plain", "second distinct bytes\n".getBytes(StandardCharsets.UTF_8)));
                        parts.addBodyPart(file("fixture.png", "image/png", Base64.getDecoder().decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aR8YAAAAASUVORK5CYII=")));
                    }
                    msg.setContent(parts);
                } else if (i == 6) {
                    msg.setContent("<html><body><h2>Fixture HTML</h2><p>Safe local content</p>"
                        + "<img src=\"http://127.0.0.1:1/blocked.png\"></body></html>", "text/html; charset=UTF-8");
                } else {
                    msg.setText("N\u1ed9i dung fixture " + i, "UTF-8");
                }
                msg.saveChanges();
                gm.getUserManager().getUser(email).deliver(msg);
            }
        }
        System.out.println("MANUAL_GREENMAIL_READY");
        if ("1".equals(System.getenv("MAILAPP_MANUAL_FAULTS"))) {
            control(gm);
        } else {
            new CountDownLatch(1).await();
        }
    }

    /**
     * Stop/recreate a single listener, retaining the same in-memory Managers.
     * GreenMail.stop()/reset() would discard mailboxes. Never use either to
     * simulate a transient outage; a new server thread is needed after stop.
     */
    private static final class ControlledGreenMail extends GreenMail {
        private final Map<String, ServerSetup> setups = new HashMap<>();
        private final Map<String, StalledListener> stalls = new HashMap<>();
        private final FaultManagers faultManagers;
        ControlledGreenMail(ServerSetup[] configuration) {
            super(configuration);
            for (ServerSetup setup : configuration) setups.put(setup.getProtocol(), setup);
            // Only this disposable provider may simulate an auth-policy denial.
            // Never modify a stored password/token or read it for diagnostics.
            faultManagers = "1".equals(System.getenv("MAILAPP_MANUAL_FAULTS"))
                    ? new FaultManagers() : null;
            if (faultManagers != null) managers = faultManagers;
        }
        private FaultManagers faultManager() {
            return Objects.requireNonNull(faultManagers, "Explicit provider fault fixture required");
        }
        synchronized void pause(String protocol) {
            AbstractServer server = services.get(protocol);
            if (server == null) throw new IllegalStateException("Already paused");
            server.stopService();
            if (server.isRunning() || server.isAlive()) throw new IllegalStateException("Listener not stopped");
            services.remove(protocol);
        }
        synchronized void resume(String protocol) throws Exception {
            if (services.containsKey(protocol)) throw new IllegalStateException("Already running");
            StalledListener stalled = stalls.remove(protocol);
            if (stalled != null) stalled.close();
            ServerSetup setup = Objects.requireNonNull(setups.get(protocol));
            AbstractServer server = createServices(new ServerSetup[]{setup}, managers).get(protocol);
            services.put(protocol, server); // shutdown hook owns it even if startup fails
            server.startService();
            if (!server.waitTillRunning(5000) || !server.isRunning())
                throw new IllegalStateException("Listener not ready");
        }
        synchronized void stall(String protocol) throws Exception {
            if (!"1".equals(System.getenv("MAILAPP_MANUAL_STALLS")))
                throw new IllegalStateException("Explicit timeout fixture required");
            pause(protocol);
            stalls.put(protocol, new StalledListener(setups.get(protocol).getPort()));
        }
        synchronized String stallStatus(String protocol) {
            StalledListener stalled = Objects.requireNonNull(stalls.get(protocol), "Not stalled");
            return stalled.status();
        }
        synchronized void closeStalls() {
            for (StalledListener listener : stalls.values()) listener.close();
            stalls.clear();
        }
    }

    /** Actual GreenMail protocol exchanges; only aggregate counters leave this
     * fixture. No authentication secret or submitted mail data is inspected. */
    private static final class FaultManagers extends Managers {
        private final AtomicBoolean denyAuth = new AtomicBoolean();
        private final AtomicBoolean failAfterDelivery = new AtomicBoolean();
        private final AtomicInteger authRejected = new AtomicInteger();
        private final AtomicInteger afterDeliveryFailures = new AtomicInteger();
        private final AtomicInteger deliveries = new AtomicInteger();
        private final UserManager users;
        private final SmtpManager smtp;
        FaultManagers() {
            ImapHostManager host = super.getImapHostManager();
            users = new UserManager(host) {
                @Override public boolean test(String login, String password) {
                    if (denyAuth.get() && "http_manual@test.local".equalsIgnoreCase(login)) {
                        authRejected.incrementAndGet();
                        return false; // provider denies this existing mailbox; no credential substitution
                    }
                    return super.test(login, password);
                }
            };
            smtp = new SmtpManager(host, users) {
                @Override public synchronized void send(SmtpState state) {
                    super.send(state);
                    deliveries.incrementAndGet();
                    // GreenMail's DATA handler responds 451 rather than 250
                    // if this callback throws. The delivery has happened.
                    if (failAfterDelivery.compareAndSet(true, false)) {
                        afterDeliveryFailures.incrementAndGet();
                        throw new IllegalStateException("Synthetic provider post-delivery fault");
                    }
                }
            };
        }
        @Override public UserManager getUserManager() { return users; }
        @Override public SmtpManager getSmtpManager() { return smtp; }
        String status() {
            return "authRejected=" + authRejected.get() + " deliveries=" + deliveries.get()
                    + " postDeliveryFailures=" + afterDeliveryFailures.get()
                    + " authDenied=" + denyAuth.get() + " armed=" + failAfterDelivery.get();
        }
    }

    /** Accept loopback TCP, but never send a greeting or perform TLS/auth.
     * Discard pre-handshake bytes without inspecting or storing them.
     * Counts distinguish peer closure from the fixture's own bounded timeout. */
    private static final class StalledListener implements AutoCloseable {
        private final ServerSocket listener;
        private final Set<Socket> active = ConcurrentHashMap.newKeySet();
        private final AtomicInteger accepted = new AtomicInteger(), peerClosed = new AtomicInteger(),
                fixtureClosed = new AtomicInteger();
        private final Thread thread;
        StalledListener(int port) throws IOException {
            listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress("127.0.0.1", port), 4);
            thread = Thread.ofVirtual().start(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket socket = listener.accept();
                        if (active.size() >= 4) { socket.close(); continue; }
                        accepted.incrementAndGet();
                        active.add(socket);
                        Thread.ofVirtual().start(() -> observe(socket));
                    } catch (IOException e) { break; }
                }
            });
        }
        private void observe(Socket socket) {
            boolean peer = false;
            try (socket) {
                socket.setSoTimeout(12_000);
                byte[] discarded = new byte[1024];
                int remaining = 65536;
                while (remaining > 0) {
                    int n = socket.getInputStream().read(discarded);
                    if (n < 0) { peer = true; break; }
                    remaining -= n;
                }
            } catch (SocketTimeoutException ignored) {
                // Fixture's own upper bound is not evidence of a client close.
            } catch (SocketException e) {
                // A reset before the listener is stopped also ends the peer's
                // connection; our try-with-resources has already closed it.
                peer = !listener.isClosed();
            } catch (IOException ignored) {
            } finally {
                if (peer) peerClosed.incrementAndGet(); else fixtureClosed.incrementAndGet();
                active.remove(socket);
            }
        }
        String status() {
            return "accepted=" + accepted.get() + " peerClosed=" + peerClosed.get()
                    + " fixtureClosed=" + fixtureClosed.get() + " active=" + active.size();
        }
        @Override public void close() {
            try { listener.close(); } catch (IOException ignored) {}
            try { thread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            for (Socket socket : active) try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void control(ControlledGreenMail gm) throws Exception {
        Path root = Path.of("").toRealPath();
        Path temp = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        if (!root.getParent().equals(temp)
                || !root.getFileName().toString().matches("mailapp-e2e-[a-f0-9]{32}"))
            throw new IllegalStateException("Fault control outside disposable fixture");
        Path command = root.resolve("mail-fixture-command.txt");
        Path ack = root.resolve("mail-fixture-ack.txt");
        while (!Files.exists(root.resolve("stop-manual"))) {
            if (Files.isRegularFile(command, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.size(command) > 128) throw new IllegalStateException("Invalid control request");
                String action = Files.readString(command, StandardCharsets.US_ASCII).trim();
                Files.delete(command);
                try {
                    String detail = "";
                    switch (action) {
                        case "pause-imaps" -> gm.pause("imaps");
                        case "resume-imaps" -> gm.resume("imaps");
                        case "pause-smtp" -> gm.pause("smtp");
                        case "resume-smtp" -> gm.resume("smtp");
                        case "stall-imaps" -> gm.stall("imaps");
                        case "stall-smtp" -> gm.stall("smtp");
                        case "status-imaps" -> detail = " " + gm.stallStatus("imaps");
                        case "status-smtp" -> detail = " " + gm.stallStatus("smtp");
                        case "deny-mail-auth" -> gm.faultManager().denyAuth.set(true);
                        case "restore-mail-auth" -> gm.faultManager().denyAuth.set(false);
                        case "fail-after-delivery" -> {
                            if (!gm.faultManager().failAfterDelivery.compareAndSet(false, true))
                                throw new IllegalStateException("Post-delivery fault already armed");
                        }
                        case "provider-fault-status" -> detail = " " + gm.faultManager().status();
                        default -> throw new IllegalArgumentException("Unknown fixture fault");
                    }
                    Files.writeString(ack, action + " OK" + detail, StandardOpenOption.CREATE_NEW);
                } catch (Exception ex) {
                    Files.writeString(ack, "ERROR " + ex.getClass().getSimpleName(), StandardOpenOption.CREATE_NEW);
                }
            }
            Thread.sleep(100);
        }
        // Let the harness stop this process after it copies evidence. Exiting
        // here would race the harness's child-health check.
        new CountDownLatch(1).await();
    }
    private static MimeBodyPart file(String name, String type, byte[] bytes) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(bytes, type)));
        part.setFileName(name);
        part.setDisposition(Part.ATTACHMENT);
        part.setHeader("Content-Transfer-Encoding", "base64");
        return part;
    }
}
