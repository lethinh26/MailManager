package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Widen usernames, preserve original casing, and enforce uniqueness of login identities.
 * Refuse ambiguous legacy data rather than guessing which user account to merge or delete.
 */
public class V5__UserIdentityConstraints extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
        try (Statement statement = connection.createStatement()) {
            rejectDuplicates(statement, "SELECT LOWER(username) FROM users GROUP BY LOWER(username) HAVING COUNT(*) > 1",
                    "username (case-insensitive)");
            rejectDuplicates(statement, "SELECT LOWER(TRIM(email)) FROM users WHERE email IS NOT NULL GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1",
                    "email (case-insensitive)");
            statement.execute(h2
                    ? "ALTER TABLE users ALTER COLUMN username VARCHAR(64) NOT NULL"
                    : "ALTER TABLE users MODIFY COLUMN username VARCHAR(64) NOT NULL");
            statement.execute("ALTER TABLE users ADD COLUMN username_ci VARCHAR(64) GENERATED ALWAYS AS (LOWER(username))"
                    + (h2 ? "" : " STORED"));
            statement.execute("CREATE UNIQUE INDEX uk_users_username_ci ON users(username_ci)");
            statement.executeUpdate("UPDATE users SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL");
            statement.execute("CREATE UNIQUE INDEX uk_users_email ON users(email)");
        }
    }

    private void rejectDuplicates(Statement statement, String sql, String field) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            if (rows.next()) {
                throw new SQLException("Duplicate user " + field
                        + " exists. Resolve ambiguous login identities before migrating; no accounts were merged.");
            }
        }
    }
}
