import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.mailapp.mail.DefaultMailConnectionFactory;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.security.AesCipher;
import jakarta.mail.Folder;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * Interactive, local-only observer for caller-owned factory resources.
 * No application login, database, environment credential, token, live
 * provider, SMTP send, deletion, or mutation of an existing fixture.
 * Each command reports observed state, not an automated PASS verdict.
 */
class ManualRawMail {
    private Store store;
    private Transport transport;
    private final GreenMail mail;
    private final DefaultMailConnectionFactory factory;
    private final GmailAccount account;

    private ManualRawMail(GreenMail mail) throws Exception {
        this.mail = mail;
        String email = "raw-fixture@example.test";
        String syntheticPassword = UUID.randomUUID().toString();
        mail.setUser(email, syntheticPassword);
        for (int i = 0; i < 2; i++) {
            MimeMessage msg = new MimeMessage(mail.getSmtp().createSession());
            msg.setFrom(new InternetAddress("source@example.test"));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
            msg.setSubject("Synthetic raw lifecycle");
            msg.setText("Synthetic fixture only");
            msg.saveChanges();
            mail.getUserManager().getUser(email).deliver(msg);
        }
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesCipher cipher = new AesCipher(Base64.getEncoder().encodeToString(key));
        Arrays.fill(key, (byte) 0);
        account = new GmailAccount();
        account.setEmail(email);
        account.setAuthType(AuthType.APP_PASSWORD);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAppPasswordEncrypted(cipher.encrypt(syntheticPassword));

        MailConnectionProperties props = new MailConnectionProperties();
        props.setImapHost("127.0.0.1");
        props.setImapPort(mail.getImaps().getPort());
        props.setSmtpHost("127.0.0.1");
        props.setSmtpPort(mail.getSmtp().getPort());
        props.setLocalTestTransport(true);
        props.setPoolEnabled(true); // raw adapters must still be caller-owned
        props.setRetryEnabled(false);
        props.setConnectionTimeoutMs(3000);
        props.setTimeoutMs(3000);
        props.setWriteTimeoutMs(3000);
        factory = new DefaultMailConnectionFactory(props, cipher, null, null);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"LOCAL-RAW-ONLY".equals(args[0])) {
            throw new IllegalArgumentException("Explicit local-only mode required");
        }
        // Do not emit third-party provider errors or raw debug data.
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        GreenMail mail = new GreenMail(new ServerSetup[]{
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS),
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)
        });
        ManualRawMail observer = null;
        try {
            mail.start();
            observer = new ManualRawMail(mail);
            System.out.println("RAW_READY local-only, fresh in-memory provider, IMAPS/SMTP loopback");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String command;
            while ((command = reader.readLine()) != null && !"quit".equals(command.trim())) {
                try {
                    observer.observe(command.trim());
                } catch (Exception e) {
                    System.out.println("COMMAND_ERROR type=" + e.getClass().getSimpleName());
                    // No retry after an unexpected failure.
                    break;
                }
            }
        } finally {
            try {
                if (observer != null) observer.close();
            } finally {
                mail.stop();
                System.out.println("RAW_STOPPED");
            }
        }
    }

    private void observe(String command) throws Exception {
        switch (command) {
            case "create-store" -> {
                if (store != null) throw new IllegalStateException("Close existing object first");
                store = factory.createImapStore(account);
                System.out.println("store created connected=" + store.isConnected());
            }
            case "connect-store" -> {
                if (store == null || store.isConnected()) throw new IllegalStateException("Invalid state");
                store.connect();
                System.out.println("store connected=" + store.isConnected());
            }
            case "read-folders" -> {
                if (store == null || !store.isConnected()) throw new IllegalStateException("Invalid state");
                Folder[] listed = store.getDefaultFolder().list("*");
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);
                try {
                    System.out.println("folders listed=" + listed.length
                            + " inbox readOnly=" + (inbox.getMode() == Folder.READ_ONLY)
                            + " total=" + inbox.getMessageCount() + " unread=" + inbox.getUnreadMessageCount());
                } finally {
                    if (inbox.isOpen()) inbox.close(false);
                }
                System.out.println("folder closed=" + !inbox.isOpen() + " callerStoreConnected=" + store.isConnected());
            }
            case "close-store" -> {
                if (store == null) throw new IllegalStateException("No store");
                store.close();
                System.out.println("store after close connected=" + store.isConnected());
                store = null;
            }
            case "create-transport" -> {
                if (transport != null) throw new IllegalStateException("Close existing object first");
                transport = factory.createSmtpTransport(account);
                System.out.println("transport created connected=" + transport.isConnected());
            }
            case "connect-transport" -> {
                if (transport == null || transport.isConnected()) throw new IllegalStateException("Invalid state");
                transport.connect();
                System.out.println("transport connected=" + transport.isConnected());
            }
            case "close-transport" -> {
                if (transport == null) throw new IllegalStateException("No transport");
                transport.close();
                System.out.println("transport after close connected=" + transport.isConnected());
                transport = null;
            }
            case "missing-folder-scope" -> {
                if (store != null || transport != null) throw new IllegalStateException("Close objects first");
                Store own = factory.createImapStore(account);
                boolean missing = false;
                try (own) {
                    own.connect();
                    Folder absent = own.getFolder("absent-" + UUID.randomUUID());
                    try {
                        absent.open(Folder.READ_ONLY);
                    } finally {
                        if (absent.isOpen()) absent.close(false);
                    }
                } catch (FolderNotFoundException expected) {
                    missing = true;
                }
                System.out.println("missing-folder observed=" + missing + " storeAfterScopeConnected=" + own.isConnected());
            }
            case "transport-caller-error-scope" -> {
                if (store != null || transport != null) throw new IllegalStateException("Close objects first");
                Transport own = factory.createSmtpTransport(account);
                boolean callerFailure = false;
                try (own) {
                    own.connect();
                    // Deliberately abort before any SMTP message submission.
                    throw new CallerValidationFailure();
                } catch (CallerValidationFailure expected) {
                    callerFailure = true;
                }
                System.out.println("caller-validation-failure=" + callerFailure
                        + " transportAfterScopeConnected=" + own.isConnected() + " noSendInvoked=true");
            }
            case "status" -> System.out.println("storeOpen=" + (store != null && store.isConnected())
                    + " transportOpen=" + (transport != null && transport.isConnected())
                    + " fixtureMessages=" + mail.getReceivedMessages().length);
            default -> throw new IllegalArgumentException("Unknown observer command");
        }
    }

    private void close() throws Exception {
        try {
            if (store != null) store.close();
        } finally {
            try {
                if (transport != null) transport.close();
            } finally {
                factory.shutdownPool();
            }
        }
    }

    private static class CallerValidationFailure extends Exception {}
}
