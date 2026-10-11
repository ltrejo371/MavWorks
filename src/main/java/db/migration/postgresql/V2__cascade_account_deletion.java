package db.migration.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Updates existing foreign keys, including names generated differently by H2 and PostgreSQL. */
public class V2__cascade_account_deletion extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String schema = context.getConfiguration().getDefaultSchema();
        cascade(connection, schema, "tos_agreements", "user_id", "app_users");
        cascade(connection, schema, "listings", "owner_id", "app_users");
        cascade(connection, schema, "listing_images", "listing_id", "listings");
    }

    private void cascade(Connection connection, String schema, String table,
                         String column, String parent) throws SQLException {
        var names = new ArrayList<String>();
        try (var keys = connection.getMetaData().getImportedKeys(connection.getCatalog(), schema, table)) {
            while (keys.next()) {
                if (column.equals(keys.getString("FKCOLUMN_NAME"))) {
                    if (!parent.equals(keys.getString("PKTABLE_NAME"))
                            || !schema.equals(keys.getString("PKTABLE_SCHEM"))
                            || !"id".equals(keys.getString("PKCOLUMN_NAME"))
                            || keys.getShort("KEY_SEQ") != 1) {
                        throw new SQLException("Unexpected foreign key on " + table + "." + column);
                    }
                    names.add(keys.getString("FK_NAME"));
                }
            }
        }
        if (names.isEmpty()) {
            throw new SQLException("Missing foreign key on " + table + "." + column);
        }
        String qualifiedTable = quote(schema) + "." + quote(table);
        try (var statement = connection.createStatement()) {
            for (String name : names) {
                statement.execute("ALTER TABLE " + qualifiedTable + " DROP CONSTRAINT " + quote(name));
            }
            statement.execute("ALTER TABLE " + qualifiedTable + " ADD CONSTRAINT "
                    + quote(table + "_" + column + "_fkey") + " FOREIGN KEY (" + quote(column)
                    + ") REFERENCES " + quote(schema) + "." + quote(parent) + " (id) ON DELETE CASCADE");
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
