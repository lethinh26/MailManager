package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Widen the original contract columns without dropping, renaming or rewriting data.
 * Preserve the wider setting key and existing identity/index/FK semantics.
 */
public class V6__WidenContractColumns extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
        try (Statement statement = connection.createStatement()) {
            widen(statement, h2, "users", "full_name", "VARCHAR(255) NULL");
            widen(statement, h2, "users", "email", "VARCHAR(255) NULL");
            widen(statement, h2, "gmail_accounts", "email", "VARCHAR(255) NOT NULL");
            widen(statement, h2, "gmail_accounts", "email_key", "VARCHAR(255) NOT NULL");
            widen(statement, h2, "gmail_accounts", "display_name", "VARCHAR(255) NULL");
            widen(statement, h2, "system_settings", "setting_value", "TEXT NOT NULL");
            widen(statement, h2, "audit_logs", "action", "VARCHAR(64) NOT NULL");
            widen(statement, h2, "audit_logs", "detail", "TEXT NULL");
            widen(statement, h2, "audit_logs", "ip_address", "VARCHAR(64) NULL");
        }
    }

    private void widen(Statement statement, boolean h2, String table, String column, String definition)
            throws SQLException {
        statement.execute("ALTER TABLE " + table + (h2 ? " ALTER COLUMN " : " MODIFY COLUMN ")
                + column + " " + definition);
    }
}
