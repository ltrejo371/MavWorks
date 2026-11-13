package com.mavworks;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModerationMigrationTest {
    @Test void existingPasswordResetMigrationKeepsItsChecksumAndModerationUpgradesSeparately() throws Exception {
        String url = "jdbc:h2:mem:moderation-upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").schemas("mavworks").defaultSchema("mavworks")
                .locations("classpath:db/migration/postgresql").target("19").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("SELECT checksum FROM mavworks.flyway_schema_history WHERE version = '18'")) {
                assertTrue(row.next());
                assertEquals(1040040313, row.getInt(1), "Preserve the V18 checksum already applied in Supabase");
            }
            var result = Flyway.configure().dataSource(url, "sa", "").schemas("mavworks").defaultSchema("mavworks")
                    .locations("classpath:db/migration/postgresql").load().migrate();
            assertEquals(1, result.migrationsExecuted);
            assertEquals("20", result.targetSchemaVersion);
            // Both the previously deployed objects and the new notice column remain available.
            statement.executeQuery("SELECT token_hash FROM mavworks.password_reset_tokens").close();
            statement.executeQuery("SELECT username_last_changed_at FROM mavworks.app_users").close();
            statement.executeQuery("SELECT notice_acknowledged_at FROM mavworks.reports").close();
        }
    }
}
