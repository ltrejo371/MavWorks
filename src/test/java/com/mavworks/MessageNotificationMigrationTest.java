package com.mavworks;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageNotificationMigrationTest {
    @Test void oldMessagesStartReadAndNewMessagesStartUnread() throws Exception {
        String url = "jdbc:h2:mem:notification-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).schemas("mavworks").defaultSchema("mavworks")
                    .locations("classpath:db/migration/postgresql").target("14").load().migrate();
            sql.execute("INSERT INTO mavworks.app_users (username, email, hashed_password) VALUES ('buyer', 'buyer@mavs.uta.edu', 'test'), ('seller', 'seller@mavs.uta.edu', 'test')");
            sql.execute("INSERT INTO mavworks.listings (owner_id, name, description, price, created_at, expires_at, type, category, status) VALUES (2, 'Book', 'Test', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PRODUCT', 'OTHER', 'ACTIVE')");
            sql.execute("INSERT INTO mavworks.conversations (buyer_id, listing_id) VALUES (1, 1)");
            sql.execute("INSERT INTO mavworks.messages (conversation_id, sender_id, body, sent_at) VALUES (1, 1, 'Old', CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(source).schemas("mavworks").defaultSchema("mavworks")
                    .locations("classpath:db/migration/postgresql").target("15").load().migrate();
            sql.execute("INSERT INTO mavworks.messages (conversation_id, sender_id, body, sent_at) VALUES (1, 2, 'New', CURRENT_TIMESTAMP)");
            try (var rows = sql.executeQuery("SELECT body, read_at FROM mavworks.messages ORDER BY id")) {
                assertTrue(rows.next()); assertEquals("Old", rows.getString(1)); assertNotNull(rows.getObject(2));
                assertTrue(rows.next()); assertEquals("New", rows.getString(1)); assertNull(rows.getObject(2));
            }
        }
    }
}
