package com.mailapp.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Flyway V3 thật trên H2 (chế độ MySQL):
 * - Chạy V1 + V2
 * - Chèn dữ liệu cũ gồm 2 dòng trùng email_key (dấu chấm + +tag + googlemail.com) cho cùng user,
 *   cùng 1 Gmail cho user khác, và 2 email Workspace khác dấu chấm
 * - Chạy V3
 * - Kiểm tra giữ dòng cũ nhất, gộp bí mật OAuth2 + App Password, ghi cảnh báo audit_logs,
 *   và ràng buộc UNIQUE(user_id, email_key) hoạt động chính xác.
 */
class FlywayV3MigrationTest {

    @Test
    @DisplayName("Flyway V3: điền email_key, gộp an toàn dữ liệu cũ trùng lặp (giữ dòng cũ nhất + cảnh báo) và thay UNIQUE")
    void migratesLegacyDuplicatesSafelyAndEnforcesUniqueEmailKey() throws Exception {
        String dbName = "flyway_v3_test_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

        // 1. Chạy Flyway đến V2
        Flyway flywayV2 = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("2")
                .load();
        flywayV2.migrate();

        String cipherApp10 = "cipher-a-" + UUID.randomUUID();
        String cipherOAuth11 = "cipher-o-" + UUID.randomUUID();
        String cipherWs1 = "cipher-w1-" + UUID.randomUUID();
        String cipherWs2 = "cipher-w2-" + UUID.randomUUID();
        String cipherBob20 = "cipher-b-" + UUID.randomUUID();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement st = conn.createStatement()) {
            // Tạo 2 user
            st.execute("INSERT INTO users (id, username, password_hash, email, role, enabled, must_change_password) "
                    + "VALUES (1, 'alice', 'h1', 'alice@example.com', 'USER', true, false)");
            st.execute("INSERT INTO users (id, username, password_hash, email, role, enabled, must_change_password) "
                    + "VALUES (2, 'bob', 'h2', 'bob@example.com', 'USER', true, false)");

            String insertGmail = "INSERT INTO gmail_accounts "
                    + "(id, user_id, email, display_name, auth_type, app_password_encrypted, refresh_token_encrypted, status, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)";
            try (PreparedStatement ins = conn.prepareStatement(insertGmail)) {
                // Dữ liệu cũ của user 1: 2 dòng trùng email_key
                ins.setLong(1, 10L); ins.setLong(2, 1L); ins.setString(3, "Alice.Smith@gmail.com");
                ins.setString(4, "Alice Primary"); ins.setString(5, "APP_PASSWORD");
                ins.setString(6, cipherApp10); ins.setString(7, null); ins.setString(8, "2026-01-01 08:00:00");
                ins.executeUpdate();

                ins.setLong(1, 11L); ins.setLong(2, 1L); ins.setString(3, "alicesmith+work@googlemail.com");
                ins.setString(4, "Alice Dup"); ins.setString(5, "OAUTH2");
                ins.setString(6, null); ins.setString(7, cipherOAuth11); ins.setString(8, "2026-01-02 09:00:00");
                ins.executeUpdate();

                // 2 email Workspace khác dấu chấm (KHÔNG được gộp)
                ins.setLong(1, 12L); ins.setLong(2, 1L); ins.setString(3, "a.b@ptit.edu.vn");
                ins.setString(4, "WS 1"); ins.setString(5, "APP_PASSWORD");
                ins.setString(6, cipherWs1); ins.setString(7, null); ins.setString(8, "2026-01-03 08:00:00");
                ins.executeUpdate();

                ins.setLong(1, 13L); ins.setLong(2, 1L); ins.setString(3, "ab@ptit.edu.vn");
                ins.setString(4, "WS 2"); ins.setString(5, "APP_PASSWORD");
                ins.setString(6, cipherWs2); ins.setString(7, null); ins.setString(8, "2026-01-03 09:00:00");
                ins.executeUpdate();

                // User 2 cùng liên kết Gmail alicesmith@gmail.com
                ins.setLong(1, 20L); ins.setLong(2, 2L); ins.setString(3, "alicesmith@gmail.com");
                ins.setString(4, "Bob Shared"); ins.setString(5, "APP_PASSWORD");
                ins.setString(6, cipherBob20); ins.setString(7, null); ins.setString(8, "2026-01-04 10:00:00");
                ins.executeUpdate();
            }
        }

        // 2. Nâng cấp lên V3
        Flyway flywayV3 = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .load();
        flywayV3.migrate();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            // Kiểm tra user 1 chỉ còn 1 dòng alicesmith@gmail.com là dòng cũ nhất (id=10), giữ nguyên email gốc & displayName, gộp cả 2 bí mật!
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, email, email_key, display_name, auth_type, app_password_encrypted, refresh_token_encrypted, fallback_active "
                            + "FROM gmail_accounts WHERE user_id = 1 AND email_key = 'alicesmith@gmail.com'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Phải giữ lại bản ghi cũ nhất");
                    assertEquals(10L, rs.getLong("id"));
                    assertEquals("Alice.Smith@gmail.com", rs.getString("email"));
                    assertEquals("alicesmith@gmail.com", rs.getString("email_key"));
                    assertEquals("Alice Primary", rs.getString("display_name"));
                    assertEquals("OAUTH2", rs.getString("auth_type"));
                    assertEquals(cipherApp10, rs.getString("app_password_encrypted"));
                    assertEquals(cipherOAuth11, rs.getString("refresh_token_encrypted"));
                    assertFalse(rs.getBoolean("fallback_active"));
                    assertFalse(rs.next(), "Không được còn bản ghi trùng thứ 2");
                }
            }

            // Kiểm tra 2 email Workspace của user 1 vẫn còn nguyên cả 2 dòng
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM gmail_accounts WHERE user_id = 1 AND email_key IN ('a.b@ptit.edu.vn', 'ab@ptit.edu.vn')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1));
                }
            }

            // Kiểm tra user 2 vẫn còn nguyên bản ghi cùng email_key
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, email_key FROM gmail_accounts WHERE user_id = 2")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(20L, rs.getLong("id"));
                    assertEquals("alicesmith@gmail.com", rs.getString("email_key"));
                }
            }

            // Kiểm tra audit_logs có bản ghi cảnh báo MIGRATION_V3_DEDUP_WARNING và KHÔNG chứa bí mật
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT detail FROM audit_logs WHERE action = 'MIGRATION_V3_DEDUP_WARNING'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Phải ghi cảnh báo vào audit_logs khi gộp dòng trùng");
                    String detail = rs.getString("detail");
                    assertTrue(detail.contains("a***@gmail.com"));
                    assertFalse(detail.contains(cipherApp10));
                    assertFalse(detail.contains(cipherOAuth11));
                }
            }

            // Kiểm tra ràng buộc UNIQUE(user_id, email_key) chặn chèn trùng email_key cho cùng user
            try (Statement st = conn.createStatement()) {
                assertThrows(SQLException.class, () -> st.execute(
                        "INSERT INTO gmail_accounts (user_id, email, email_key, auth_type, status) "
                                + "VALUES (1, 'a.l.i.c.e.smith@gmail.com', 'alicesmith@gmail.com', 'APP_PASSWORD', 'ACTIVE')"),
                        "Ràng buộc UNIQUE(user_id, email_key) phải chặn dòng trùng của cùng user");
            }
        }
    }
}
