package db.migration.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Replaces enum checks without relying on database-generated constraint names. */
public class V3__align_listing_enums extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String schema = context.getConfiguration().getDefaultSchema();
        String table = quote(schema) + ".\"listings\"";
        // Resolve both before making changes, and preserve all unrelated constraints.
        String categoryCheck = findCheck(connection, schema, "category");
        String statusCheck = findCheck(connection, schema, "status");
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + quote(categoryCheck));
            statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT listings_category_check "
                    + "CHECK (category IN ('ART', 'BEAUTY', 'CLOTHING', 'JEWELRY', 'TUTORING', 'GRAPHIC_DESIGN', 'OTHER'))");
            statement.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + quote(statusCheck));
            // Keep legacy drafts and unavailable listings hidden from the market.
            statement.executeUpdate("UPDATE " + table + " SET status = CASE status "
                    + "WHEN 'PUBLISHED' THEN 'ACTIVE' WHEN 'DRAFT' THEN 'REMOVED' "
                    + "WHEN 'UNAVAILABLE' THEN 'REMOVED' ELSE status END");
            statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT listings_status_check "
                    + "CHECK (status IN ('ACTIVE', 'SOLD', 'EXPIRED', 'REMOVED'))");
        }
    }

    private String findCheck(Connection connection, String schema, String column) throws SQLException {
        var matches = new ArrayList<String>();
        var columnName = Pattern.compile("\\b" + column + "\\b", Pattern.CASE_INSENSITIVE);
        try (var query = connection.prepareStatement("""
                SELECT tc.constraint_name, cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON tc.constraint_catalog = cc.constraint_catalog
                 AND tc.constraint_schema = cc.constraint_schema
                 AND tc.constraint_name = cc.constraint_name
                WHERE tc.table_schema = ? AND tc.table_name = 'listings'
                  AND tc.constraint_type = 'CHECK'
                """)) {
            query.setString(1, schema);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    String clause = rows.getString(2);
                    // PostgreSQL can also expose NOT NULL checks here; those must stay intact.
                    if (columnName.matcher(clause).find()
                            && !clause.toUpperCase(java.util.Locale.ROOT).contains("IS NOT NULL")) {
                        matches.add(rows.getString(1));
                    }
                }
            }
        }
        if (matches.size() != 1) {
            throw new SQLException("Expected one enum check for listings." + column + ", found " + matches.size());
        }
        return matches.getFirst();
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
