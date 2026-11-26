import com.mailapp.security.AesCipher;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/**
 * Synthetic legacy-schema verification using the migrations in the final bootJar.
 * Only test-client-e2e.ps1 may provision the owned, freshly created MySQL instance.
 * No application login, real Gmail access, or raw record/credential output.
 */
class LegacyUpgradeProbe {
    private static String url, user, password;
    private static Path root;
    private static final String APP_MARKER = "synthetic-app-data";
    private static final String OAUTH_MARKER = "synthetic-oauth-data";
    private static final String CONFLICT_USER = "mailapp_conflict_fixture";

    public static void main(String[] args) throws Exception {
        root = Path.of("").toRealPath();
        if (!"1".equals(System.getenv("MAILAPP_LEGACY_FIXTURE"))
                || !root.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                || !root.getFileName().toString().matches("mailapp-e2e-[0-9a-f]{32}")
                || !Files.isDirectory(root.resolve("mysql-data"))
                || args.length != 1) throw new IllegalStateException("Disposable legacy fixture required");
        url = Objects.requireNonNull(System.getenv("MAILAPP_LEGACY_URL"));
        user = "mailapp_fixture";
        password = Objects.requireNonNull(System.getenv("SPRING_DATASOURCE_PASSWORD"));
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[2-3][0-9]{4}/mailapp_db\\?.+"))
            throw new IllegalStateException("Owned loopback fixture URL required");
        if (args[0].equals("prepare")) prepare();
        else if (args[0].equals("verify-startup")) verifyStartup();
        else if (args[0].equals("prepare-audit-upgrade")) prepareAuditUpgrade();
        else if (args[0].equals("verify-audit-upgrade")) verifyAuditUpgrade();
        else if (args[0].equals("prepare-account-revision")) prepareAccountRevision();
        else if (args[0].equals("verify-account-revision")) verifyAccountRevision();
        else throw new IllegalArgumentException("Unknown verification phase");
    }

    private static Flyway flyway(String jdbc, String name, String pass, String target) {
        var config = Flyway.configure().dataSource(jdbc, name, pass).locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private static void prepare() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(number(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()") == 0, "empty fixture");
        }
        flyway(url, user, password, "2").migrate();
        AesCipher cipher = new AesCipher(System.getenv("MAILAPP_AES_KEY"));
        String encryptedApp = cipher.encrypt(APP_MARKER);
        String encryptedOauth = cipher.encrypt(OAUTH_MARKER);
        check(!encryptedApp.equals(cipher.encrypt(APP_MARKER)), "fresh IV");
        check(!APP_MARKER.equals(encryptedApp), "ciphertext differs from plaintext");
        check(Base64.getDecoder().decode(encryptedApp).length == 12 + APP_MARKER.getBytes(StandardCharsets.UTF_8).length + 16, "IV and tag length");
        check(APP_MARKER.equals(cipher.decrypt(encryptedApp)), "round-trip");
        byte[] tampered = Base64.getDecoder().decode(encryptedApp);
        tampered[tampered.length-1] ^= 1;
        boolean rejected = false;
        try { cipher.decrypt(Base64.getEncoder().encodeToString(tampered)); }
        catch (RuntimeException expected) { rejected = true; }
        check(rejected, "modified GCM tag rejected");
        byte[] wrongKey = new byte[32];
        new java.security.SecureRandom().nextBytes(wrongKey);
        rejected = false;
        try { new AesCipher(Base64.getEncoder().encodeToString(wrongKey)).decrypt(encryptedApp); }
        catch (RuntimeException expected) { rejected = true; }
        check(rejected, "wrong key rejected");
        System.out.println("LEGACY AES round-trip, distinct IV, 12-byte IV/16-byte tag overhead, modified tag and wrong-key rejection: PASS");

        String adminHash = new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
        String userHash = new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            addUser(c, 11, "LegacyAdmin", "LegacyAdmin@test.local", "ADMIN", adminHash);
            addUser(c, 12, "LegacyUser", "LegacyUser@test.local", "USER", userHash);
            addMailbox(c, 21, 12, "legacy.fixture+one@gmail.com", "APP_PASSWORD", encryptedApp, null);
            addMailbox(c, 22, 12, "legacyfixture@googlemail.com", "OAUTH2", null, encryptedOauth);
            addMailbox(c, 23, 11, "legacyfixture@gmail.com", "APP_PASSWORD", encryptedApp, null);
            execute(c, "UPDATE system_settings SET setting_value='false',updated_by=11 WHERE setting_key='ALLOW_REGISTRATION'");
            execute(c, "UPDATE system_settings SET setting_value='0',updated_by=11 WHERE setting_key='MAX_GMAIL_PER_USER'");
            execute(c, "INSERT INTO audit_logs(id,user_id,action,detail,ip_address,created_at) VALUES (31,12,'LEGACY_MARKER','preserve history','127.0.0.1','2025-01-01 00:00:00')");
            check(number(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1") == 2, "V1-V2 history");
        }
        flyway(url, user, password, "6").migrate();
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(number(c, "SELECT COUNT(*) FROM users") == 2, "user count preserved");
            check(adminHash.equals(value(c, "SELECT password_hash FROM users WHERE id=11 AND role='ADMIN' AND username='LegacyAdmin' AND must_change_password=0")), "admin unchanged");
            check(userHash.equals(value(c, "SELECT password_hash FROM users WHERE id=12 AND role='USER' AND username='LegacyUser' AND email='legacyuser@test.local'")), "user unchanged");
            check(number(c, "SELECT COUNT(*) FROM gmail_accounts") == 2, "alias merge count");
            check(encryptedApp.equals(value(c, "SELECT app_password_encrypted FROM gmail_accounts WHERE id=21 AND user_id=12 AND email_key='legacyfixture@gmail.com' AND auth_type='OAUTH2'")), "alias app data");
            check(encryptedOauth.equals(value(c, "SELECT refresh_token_encrypted FROM gmail_accounts WHERE id=21")), "alias OAuth data");
            check(OAUTH_MARKER.equals(cipher.decrypt(value(c, "SELECT refresh_token_encrypted FROM gmail_accounts WHERE id=21"))), "OAuth decrypt after migration");
            check(encryptedApp.equals(value(c, "SELECT app_password_encrypted FROM gmail_accounts WHERE id=23 AND user_id=11")), "separate owner preserved");
            check(number(c, "SELECT COUNT(*) FROM audit_logs WHERE id=31 AND user_id=12 AND action='LEGACY_MARKER' AND detail='preserve history'") == 1, "audit preserved");
            check(number(c, "SELECT COUNT(*) FROM audit_logs WHERE action='MIGRATION_V3_DEDUP_WARNING'") == 1, "merge audit");
            check(number(c, "SELECT COUNT(*) FROM system_settings") == 9, "9 settings");
            check("false".equals(value(c, "SELECT setting_value FROM system_settings WHERE setting_key='ALLOW_REGISTRATION' AND updated_by=11")), "registration preserved");
            check("0".equals(value(c, "SELECT setting_value FROM system_settings WHERE setting_key='MAX_GMAIL_PER_USER' AND updated_by=11")), "legacy unlimited preserved");
            check(number(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1") == 6, "all migrations");
            check(number(c, "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='username'") == 64, "widen username");
            check(number(c, "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='gmail_accounts' AND COLUMN_NAME='email_key'") == 255, "widen email");
            expectConstraint(c, "INSERT INTO users(username,password_hash,email) SELECT 'legacyuser',password_hash,'different@test.local' FROM users WHERE id=12");
            expectConstraint(c, "INSERT INTO users(username,password_hash,email) SELECT 'DifferentUser',password_hash,'LEGACYUSER@test.local' FROM users WHERE id=12");
            expectConstraint(c, "INSERT INTO gmail_accounts(user_id,email,email_key,auth_type) VALUES (12,'different@gmail.com','legacyfixture@gmail.com','OAUTH2')");
            expectConstraint(c, "INSERT INTO gmail_accounts(user_id,email,email_key,auth_type) VALUES (999,'orphan@test.local','orphan@test.local','OAUTH2')");
            // Only an ephemeral comparison digest, never included in evidence.
            Files.writeString(root.resolve("legacy-snapshot.sha256"), snapshot(c));
            System.out.println("LEGACY V2->V6: users 2->2; aliases 3->2; two auth methods + separate owner preserved; audit 1->2; settings 7->9; constraints reject 4 invalid writes");
        }
        flyway(url, user, password, "7").migrate();
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(number(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1") == 7, "V7 migration");
            check(number(c, "SELECT COUNT(*) FROM mail_audit_outbox") == 0, "empty audit outbox on upgrade");
            check(Files.readString(root.resolve("legacy-snapshot.sha256")).equals(snapshot(c)), "V7 preserves existing data");
            System.out.println("LEGACY V6->V7: audit outbox created empty; existing data unchanged");
        }
        String conflictUrl = url.replace("/mailapp_db?", "/mailapp_conflict?");
        String conflictPass = Objects.requireNonNull(System.getenv("MAILAPP_LEGACY_CONFLICT_PASSWORD"));
        flyway(conflictUrl, CONFLICT_USER, conflictPass, "4").migrate();
        try (Connection c = DriverManager.getConnection(conflictUrl, CONFLICT_USER, conflictPass)) {
            addUser(c, 11, "First", "Same@test.local", "USER", userHash);
            addUser(c, 12, "Second", "same@test.local", "USER", adminHash);
        }
        boolean conflict = false;
        try { flyway(conflictUrl, CONFLICT_USER, conflictPass, null).migrate(); }
        catch (FlywayException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause())
                if (cause instanceof SQLException && cause.getMessage().contains("Duplicate user email")) conflict = true;
        }
        check(conflict, "duplicate identity migration rejected");
        try (Connection c = DriverManager.getConnection(conflictUrl, CONFLICT_USER, conflictPass)) {
            check(number(c, "SELECT COUNT(*) FROM users") == 2, "conflict users preserved");
            check(userHash.equals(value(c, "SELECT password_hash FROM users WHERE id=11 AND username='First'")), "first unchanged");
            check(adminHash.equals(value(c, "SELECT password_hash FROM users WHERE id=12 AND username='Second'")), "second unchanged");
            check(number(c, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='username_ci'") == 0, "refusal before DDL");
        }
        System.out.println("LEGACY conflicting identity: V5 refused before DDL, both users retained");
    }

    private static void verifyStartup() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(Files.readString(root.resolve("legacy-snapshot.sha256")).equals(snapshot(c)), "startup preserves migrated rows");
        }
        System.out.println("LEGACY final JAR startup: migrated users, mailbox data, settings and audit unchanged");
    }

    private static void prepareAuditUpgrade() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(number(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()") == 0, "empty fixture");
        }
        flyway(url, user, password, "7").migrate();
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            execute(c, "INSERT INTO audit_logs(action,detail,created_at) VALUES ('LEGACY_MARKER','preserve audit','2025-01-01 00:00:00')");
            execute(c, "INSERT INTO mail_audit_outbox(event_id,action,detail,occurred_at,outcome) VALUES ('11111111-1111-4111-8111-111111111111','SEND_MAIL','recipientCount=1','2025-01-01 00:00:00','PREPARED')");
        }
        System.out.println("LEGACY V7 baseline: one historical audit and one unresolved intent");
    }

    private static void verifyAuditUpgrade() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(number(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1") == 9, "V9 migration");
            check(number(c, "SELECT COUNT(*) FROM audit_logs WHERE action='LEGACY_MARKER' AND detail='preserve audit' AND mail_event_id IS NULL AND mail_actor_id IS NULL") == 1, "historical audit unchanged");
            check(number(c, "SELECT COUNT(*) FROM mail_audit_outbox WHERE event_id='11111111-1111-4111-8111-111111111111' AND action='SEND_MAIL' AND outcome='PREPARED' AND delivery_attempts=0 AND next_attempt_at IS NULL") == 1, "unresolved intent preserved");
            check(number(c, "SELECT COUNT(*) FROM audit_logs WHERE action='SEND_MAIL'") == 0, "no invented success");
        }
        System.out.println("LEGACY V7->V9 final JAR: historical audit and unresolved intent preserved; no invented success");
    }

    private static void prepareAccountRevision() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(number(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()") == 0, "empty fixture");
        }
        flyway(url, user, password, "8").migrate();
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            addUser(c, 12, "RevisionFixture", "revision@test.local", "USER", new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));
            execute(c, "INSERT INTO gmail_accounts(id,user_id,email,email_key,display_name,auth_type,app_password_encrypted,refresh_token_encrypted,status,fallback_active) VALUES (21,12,'revision@example.test','revision@example.test','Revision fixture','OAUTH2','synthetic-app-cipher','synthetic-oauth-cipher','NEEDS_REAUTH',1)");
            check(number(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1") == 8, "V8 baseline");
        }
        System.out.println("LEGACY V8 baseline: synthetic dual-method mailbox owned by ordinary fixture user");
    }

    private static void verifyAccountRevision() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            check(number(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1") == 9, "V9 migration");
            check(number(c, "SELECT COUNT(*) FROM gmail_accounts WHERE id=21 AND user_id=12 AND email_key='revision@example.test' AND display_name='Revision fixture' AND auth_type='OAUTH2' AND app_password_encrypted='synthetic-app-cipher' AND refresh_token_encrypted='synthetic-oauth-cipher' AND status='NEEDS_REAUTH' AND fallback_active=1 AND revision=0") == 1, "mailbox identity methods state preserved");
            check(number(c, "SELECT COUNT(*) FROM users WHERE id=12 AND role='USER' AND enabled=1") == 1, "owner preserved");
        }
        System.out.println("LEGACY V8->V9 final JAR: owner, dual methods, state and synthetic ciphertext preserved; revision initialized to zero");
    }

    private static String snapshot(Connection c) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String table : List.of("users", "gmail_accounts", "audit_logs", "system_settings")) {
            String columns = table.equals("audit_logs")
                    ? "id,user_id,action,detail,ip_address,created_at" : "*";
            try (Statement statement=c.createStatement(); ResultSet rs=statement.executeQuery("SELECT " + columns + " FROM " + table + " ORDER BY 1")) {
                while (rs.next()) for(int i=1; i<=rs.getMetaData().getColumnCount(); i++) {
                    if (table.equals("gmail_accounts") && rs.getMetaData().getColumnName(i).equalsIgnoreCase("revision")) continue;
                    String field = rs.getString(i);
                    byte[] bytes = field == null ? new byte[0] : field.getBytes(StandardCharsets.UTF_8);
                    digest.update((field == null ? "-1:" : bytes.length+":").getBytes(StandardCharsets.US_ASCII));
                    digest.update(bytes);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addUser(Connection c, long id, String username, String email, String role, String hash) throws SQLException {
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO users(id,username,email,role,password_hash,enabled,must_change_password) VALUES (?,?,?,?,?,1,0)")) {
            ps.setLong(1,id);ps.setString(2,username);ps.setString(3,email);ps.setString(4,role);ps.setString(5,hash);ps.executeUpdate();
        }
    }
    private static void addMailbox(Connection c,long id,long owner,String email,String type,String app,String oauth) throws SQLException {
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO gmail_accounts(id,user_id,email,display_name,auth_type,app_password_encrypted,refresh_token_encrypted,created_at) VALUES (?,?,?,?,?,?,?,'2025-01-01 00:00:00')")) {
            ps.setLong(1,id);ps.setLong(2,owner);ps.setString(3,email);ps.setString(4,"Legacy fixture");ps.setString(5,type);ps.setString(6,app);ps.setString(7,oauth);ps.executeUpdate();
        }
    }
    private static void expectConstraint(Connection c,String sql) throws SQLException {
        try { execute(c,sql); }
        catch (SQLException expected) { check("23000".equals(expected.getSQLState()), "integrity rejection"); return; }
        throw new IllegalStateException("Expected constraint rejection");
    }
    private static void execute(Connection c,String sql) throws SQLException { try (Statement s=c.createStatement()) {s.executeUpdate(sql);} }
    private static String value(Connection c,String sql) throws SQLException {
        try (Statement s=c.createStatement();ResultSet rs=s.executeQuery(sql)) {
            if(!rs.next())throw new IllegalStateException("Missing expected row");return rs.getString(1);
        }
    }
    private static long number(Connection c,String sql) throws SQLException {return Long.parseLong(value(c,sql));}
    private static void check(boolean ok,String label) {if(!ok)throw new IllegalStateException("Legacy probe failed: "+label);}
}
