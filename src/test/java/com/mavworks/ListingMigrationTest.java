package com.mavworks;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListingMigrationTest {
    @Test
    void upgradesExistingV2ListingsWithoutPublishingDrafts() throws Exception {
        String url = "jdbc:h2:mem:upgrade-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var sql = connection.createStatement()) {
            // Keep the H2 session that creates the check constraints alive during the upgrade.
            var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).schemas("mavworks")
                    .defaultSchema("mavworks").locations("classpath:db/migration/postgresql")
                    .target("2").load().migrate();
            sql.execute("INSERT INTO mavworks.app_users (username, email, hashed_password) "
                    + "VALUES ('seller', 'seller@mavs.uta.edu', 'test-only')");
            for (String status : new String[] {"PUBLISHED", "DRAFT", "UNAVAILABLE"}) {
                sql.execute("INSERT INTO mavworks.listings "
                        + "(owner_id, name, description, price, created_at, type, category, status) "
                        + "VALUES (1, '" + status + "', 'Test', 10, CURRENT_TIMESTAMP, 'PRODUCT', 'OTHER', '" + status + "')");
            }
            var flyway = Flyway.configure().dataSource(source).schemas("mavworks")
                    .defaultSchema("mavworks").locations("classpath:db/migration/postgresql").target("3").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var rows = sql.executeQuery("SELECT name, status FROM mavworks.listings ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals("ACTIVE", rows.getString("status"));
                assertTrue(rows.next());
                assertEquals("REMOVED", rows.getString("status"));
                assertTrue(rows.next());
                assertEquals("REMOVED", rows.getString("status"));
                assertFalse(rows.next());
            }
            assertEquals(1, sql.executeUpdate("UPDATE mavworks.listings SET category = 'ART', status = 'SOLD' WHERE id = 1"));
            assertThrows(SQLException.class, () -> sql.executeUpdate("UPDATE mavworks.listings SET category = 'INVALID' WHERE id = 1"));
            assertThrows(SQLException.class, () -> sql.executeUpdate("UPDATE mavworks.listings SET status = 'DRAFT' WHERE id = 1"));
        }
    }
}
