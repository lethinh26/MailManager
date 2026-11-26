package com.mailapp.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlywayUserIdentityMigrationTest {
    @Test
    void migrationSeedsMissingSettingsAndWidensAndProtectsLoginIdentities() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:identity_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        Flyway.configure().dataSource(jdbcUrl, "sa", "").locations("classpath:db/migration")
                .target("3").load().migrate();
        try (var conn = DriverManager.getConnection(jdbcUrl, "sa", ""); var st = conn.createStatement()) {
            st.executeUpdate("UPDATE system_settings SET setting_value = 'false' WHERE setting_key = 'ALLOW_REGISTRATION'");
            st.executeUpdate("INSERT INTO users (username,password_hash,email) VALUES ('ExistingUser','hash','Existing@Example.com')");
            st.executeUpdate("INSERT INTO gmail_accounts (user_id,email,email_key,auth_type,app_password_encrypted) SELECT id,'legacy@gmail.com','legacy@gmail.com','APP_PASSWORD','cipher' FROM users WHERE username='ExistingUser'");
        }
        Flyway.configure().dataSource(jdbcUrl, "sa", "").locations("classpath:db/migration").load().migrate();
        try (var conn = DriverManager.getConnection(jdbcUrl, "sa", ""); var st = conn.createStatement()) {
            try (var rs = st.executeQuery("SELECT COUNT(*) FROM system_settings")) {
                assertTrue(rs.next());
                assertEquals(9, rs.getInt(1));
            }
            try (var rs = st.executeQuery("SELECT setting_value FROM system_settings WHERE setting_key='ALLOW_REGISTRATION'")) {
                assertTrue(rs.next());
                assertEquals("false", rs.getString(1));
            }
            try (var rs = st.executeQuery("SELECT setting_value FROM system_settings WHERE setting_key='MAIL_PAGE_SIZE'")) {
                assertTrue(rs.next());
                assertEquals("20", rs.getString(1));
            }
            String name = "a".repeat(64);
            assertEquals(1, st.executeUpdate("INSERT INTO users (username,password_hash,email) VALUES ('" + name + "','hash','new@example.com')"));
            assertThrows(SQLException.class, () -> st.executeUpdate("INSERT INTO users (username,password_hash,email) VALUES ('existinguser','hash','other@example.com')"));
            assertThrows(SQLException.class, () -> st.executeUpdate("INSERT INTO users (username,password_hash,email) VALUES ('another','hash','existing@example.com')"));
            String longEmail = "a".repeat(60) + "@" + "b".repeat(63) + "." + "c".repeat(50) + ".example";
            String longName = "n".repeat(255);
            assertEquals(1, st.executeUpdate("INSERT INTO users (username,password_hash,email,full_name) VALUES ('long_user','hash','" + longEmail + "','" + longName + "')"));
            assertEquals(1, st.executeUpdate("INSERT INTO gmail_accounts (user_id,email,email_key,display_name,auth_type,status) SELECT id,'" + longEmail + "','" + longEmail + "','" + longName + "','OAUTH2','ERROR' FROM users WHERE username='long_user'"));
            try (var rs = st.executeQuery("SELECT app_password_encrypted FROM gmail_accounts WHERE email='legacy@gmail.com'")) {
                assertTrue(rs.next());
                assertEquals("cipher", rs.getString(1));
            }
            assertEquals(1, st.executeUpdate("UPDATE system_settings SET description='preserved',setting_value='" + "x".repeat(600) + "' WHERE setting_key='MAIL_PAGE_SIZE'"));
            assertEquals(1, st.executeUpdate("INSERT INTO audit_logs(action,detail,ip_address) VALUES ('" + "a".repeat(64) + "','" + "d".repeat(700) + "','" + "i".repeat(64) + "')"));
        }
    }
}
