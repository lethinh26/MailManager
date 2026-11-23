package db.migration;

import com.mailapp.mail.model.MailCredentials;
import com.mailapp.util.EmailNormalizer;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flyway Migration V3: Nâng cấp bảng gmail_accounts hỗ trợ đa phương thức đăng nhập (OAuth2 + App Password)
 * cho cùng một hộp thư Gmail và cột chuẩn hóa email_key.
 *
 * Các bước thực hiện an toàn trên cả MySQL 8.0 và H2 (MODE=MySQL):
 * 1. Bổ sung các cột avatar_url (nếu chưa có), email_key, fallback_active.
 * 2. Tính email_key bằng EmailNormalizer.normalizeKey(email) cho toàn bộ dòng hiện có.
 * 3. Nếu dữ liệu cũ tồn tại >= 2 dòng cùng (user_id, email_key):
 *    - Giữ dòng cũ nhất (created_at ASC, id ASC).
 *    - Gộp bí mật phương thức còn thiếu (app_password_encrypted, refresh_token_encrypted, avatar_url) vào dòng được giữ.
 *    - Ghi log WARN (chỉ che email, tuyệt đối không ghi bí mật) và ghi bản ghi cảnh báo vào audit_logs.
 *    - Xóa các dòng trùng dư thừa.
 * 4. Đặt email_key NOT NULL.
 * 5. Thay ràng buộc UNIQUE(user_id, email) (uk_user_gmail) bằng UNIQUE(user_id, email_key) (uk_user_gmail_key).
 */
public class V3__GmailEmailKeyMultiAuth extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V3__GmailEmailKeyMultiAuth.class);

    private record LegacyRow(
            long id,
            long userId,
            String email,
            String emailKey,
            String displayName,
            String avatarUrl,
            String authType,
            String appPasswordEncrypted,
            String refreshTokenEncrypted,
            Timestamp tokenExpiresAt,
            Timestamp lastVerifiedAt,
            String status
    ) {}

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        boolean isH2 = dbProduct.contains("h2");

        // 1. Bổ sung cột mới một cách an toàn (idempotent)
        if (!columnExists(conn, "gmail_accounts", "avatar_url")) {
            executeSql(conn, "ALTER TABLE gmail_accounts ADD COLUMN avatar_url VARCHAR(512) NULL");
        }
        if (!columnExists(conn, "gmail_accounts", "email_key")) {
            executeSql(conn, "ALTER TABLE gmail_accounts ADD COLUMN email_key VARCHAR(120) NULL");
        }
        if (!columnExists(conn, "gmail_accounts", "fallback_active")) {
            executeSql(conn, "ALTER TABLE gmail_accounts ADD COLUMN fallback_active BOOLEAN NOT NULL DEFAULT FALSE");
        }

        // 2. Đọc toàn bộ bản ghi cũ theo thứ tự cũ nhất trước (created_at ASC, id ASC)
        List<LegacyRow> rows = new ArrayList<>();
        String selectSql = "SELECT id, user_id, email, display_name, avatar_url, auth_type, "
                + "app_password_encrypted, refresh_token_encrypted, token_expires_at, last_verified_at, status "
                + "FROM gmail_accounts ORDER BY created_at ASC, id ASC";
        try (PreparedStatement ps = conn.prepareStatement(selectSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String email = rs.getString("email");
                rows.add(new LegacyRow(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        email,
                        EmailNormalizer.normalizeKey(email),
                        rs.getString("display_name"),
                        rs.getString("avatar_url"),
                        rs.getString("auth_type"),
                        rs.getString("app_password_encrypted"),
                        rs.getString("refresh_token_encrypted"),
                        rs.getTimestamp("token_expires_at"),
                        rs.getTimestamp("last_verified_at"),
                        rs.getString("status")
                ));
            }
        }

        // 3. Nhóm theo (user_id, email_key), giữ dòng cũ nhất, gộp phương thức và xóa dòng trùng
        Map<String, List<LegacyRow>> grouped = new LinkedHashMap<>();
        for (LegacyRow row : rows) {
            String groupKey = row.userId() + "|" + row.emailKey();
            grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(row);
        }

        String updateRowSql = "UPDATE gmail_accounts SET email_key = ?, auth_type = ?, "
                + "app_password_encrypted = ?, refresh_token_encrypted = ?, token_expires_at = ?, "
                + "avatar_url = ?, last_verified_at = ?, status = ? WHERE id = ?";
        String deleteRowSql = "DELETE FROM gmail_accounts WHERE id = ?";
        String insertAuditSql = "INSERT INTO audit_logs (user_id, action, detail, ip_address, created_at) "
                + "VALUES (?, ?, ?, NULL, CURRENT_TIMESTAMP)";

        try (PreparedStatement updatePs = conn.prepareStatement(updateRowSql);
             PreparedStatement deletePs = conn.prepareStatement(deleteRowSql);
             PreparedStatement auditPs = conn.prepareStatement(insertAuditSql)) {

            for (List<LegacyRow> group : grouped.values()) {
                LegacyRow keeper = group.get(0);
                String mergedAppPass = keeper.appPasswordEncrypted();
                String mergedRefresh = keeper.refreshTokenEncrypted();
                Timestamp mergedTokenExp = keeper.tokenExpiresAt();
                String mergedAvatar = keeper.avatarUrl();
                Timestamp mergedVerified = keeper.lastVerifiedAt();
                String mergedAuthType = keeper.authType();
                String mergedStatus = keeper.status();

                List<Long> duplicateIds = new ArrayList<>();
                for (int i = 1; i < group.size(); i++) {
                    LegacyRow dup = group.get(i);
                    duplicateIds.add(dup.id());

                    if (isBlank(mergedAppPass) && !isBlank(dup.appPasswordEncrypted())) {
                        mergedAppPass = dup.appPasswordEncrypted();
                    }
                    if (isBlank(mergedRefresh) && !isBlank(dup.refreshTokenEncrypted())) {
                        mergedRefresh = dup.refreshTokenEncrypted();
                        mergedTokenExp = dup.tokenExpiresAt();
                        if ("ACTIVE".equalsIgnoreCase(dup.status())) {
                            mergedStatus = "ACTIVE";
                        }
                    }
                    if (isBlank(mergedAvatar) && !isBlank(dup.avatarUrl())) {
                        mergedAvatar = dup.avatarUrl();
                    }
                    if (mergedVerified == null && dup.lastVerifiedAt() != null) {
                        mergedVerified = dup.lastVerifiedAt();
                    }
                }

                // Nếu sau khi gộp có OAuth2 thì ưu tiên mặc định OAUTH2 làm phương thức chính
                if (!isBlank(mergedRefresh) && !duplicateIds.isEmpty()) {
                    mergedAuthType = "OAUTH2";
                }

                updatePs.setString(1, keeper.emailKey());
                updatePs.setString(2, mergedAuthType);
                updatePs.setString(3, mergedAppPass);
                updatePs.setString(4, mergedRefresh);
                updatePs.setTimestamp(5, mergedTokenExp);
                updatePs.setString(6, mergedAvatar);
                updatePs.setTimestamp(7, mergedVerified);
                updatePs.setString(8, mergedStatus);
                updatePs.setLong(9, keeper.id());
                updatePs.executeUpdate();

                if (!duplicateIds.isEmpty()) {
                    String masked = MailCredentials.maskEmail(keeper.emailKey());
                    String warningMsg = "Flyway V3 phát hiện " + duplicateIds.size()
                            + " dòng gmail_accounts trùng email_key (" + masked + ") cho userId=" + keeper.userId()
                            + "; giữ bản ghi cũ nhất id=" + keeper.id() + ", đã gộp bí mật và xóa id=" + duplicateIds;
                    log.warn(warningMsg);

                    for (Long dupId : duplicateIds) {
                        deletePs.setLong(1, dupId);
                        deletePs.executeUpdate();
                    }

                    if (tableExists(conn, "audit_logs")) {
                        auditPs.setLong(1, keeper.userId());
                        auditPs.setString(2, "MIGRATION_V3_DEDUP_WARNING");
                        auditPs.setString(3, warningMsg.length() > 500 ? warningMsg.substring(0, 500) : warningMsg);
                        auditPs.executeUpdate();
                    }
                }
            }
        }

        // 4. Đặt email_key NOT NULL
        if (isH2) {
            executeSql(conn, "ALTER TABLE gmail_accounts ALTER COLUMN email_key VARCHAR(120) NOT NULL");
        } else {
            executeSql(conn, "ALTER TABLE gmail_accounts MODIFY COLUMN email_key VARCHAR(120) NOT NULL");
        }

        // 5. Thay UNIQUE(user_id, email) bằng UNIQUE(user_id, email_key)
        dropOldUniqueConstraint(conn, isH2);
        if (!constraintOrIndexExists(conn, "gmail_accounts", "uk_user_gmail_key")) {
            executeSql(conn, "ALTER TABLE gmail_accounts ADD CONSTRAINT uk_user_gmail_key UNIQUE (user_id, email_key)");
        }
    }

    private void dropOldUniqueConstraint(Connection conn, boolean isH2) throws SQLException {
        if (!constraintOrIndexExists(conn, "gmail_accounts", "uk_user_gmail")) {
            return;
        }
        if (isH2) {
            try {
                executeSql(conn, "ALTER TABLE gmail_accounts DROP CONSTRAINT uk_user_gmail");
            } catch (SQLException e) {
                executeSql(conn, "DROP INDEX IF EXISTS uk_user_gmail");
            }
        } else {
            executeSql(conn, "ALTER TABLE gmail_accounts DROP INDEX uk_user_gmail");
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        for (String t : List.of(tableName, tableName.toUpperCase(Locale.ROOT))) {
            for (String c : List.of(columnName, columnName.toUpperCase(Locale.ROOT))) {
                try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, t, c)) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        for (String t : List.of(tableName, tableName.toUpperCase(Locale.ROOT))) {
            try (ResultSet rs = meta.getTables(conn.getCatalog(), null, t, new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean constraintOrIndexExists(Connection conn, String tableName, String indexName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        for (String t : List.of(tableName, tableName.toUpperCase(Locale.ROOT))) {
            try (ResultSet rs = meta.getIndexInfo(conn.getCatalog(), null, t, false, false)) {
                while (rs.next()) {
                    String idx = rs.getString("INDEX_NAME");
                    if (idx != null && idx.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
            }
        }
        // Kiểm tra thêm bảng constraints (H2 có thể đặt tên constraint khác index)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.table_constraints "
                        + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)")) {
            ps.setString(1, tableName);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    private void executeSql(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
