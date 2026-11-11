package com.mavworks;

import java.sql.DriverManager;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalendarMigrationTest {
    @Test void upgradesLegacyBookingsAndCreatesTheirMissingConversation() throws Exception {
        String url = "jdbc:h2:mem:calendar-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).schemas("mavworks").defaultSchema("mavworks")
                    .locations("classpath:db/migration/postgresql").target("10").load().migrate();
            sql.execute("INSERT INTO mavworks.app_users (username, email, hashed_password) VALUES ('buyer', 'buyer@mavs.uta.edu', 'test'), ('seller', 'seller@mavs.uta.edu', 'test')");
            sql.execute("INSERT INTO mavworks.listings (owner_id, name, description, price, created_at, expires_at, type, category, status) VALUES (2, 'Service', 'Test', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SERVICE', 'OTHER', 'ACTIVE')");
            for (String status : List.of("PENDING", "APPROVED", "DECLINED", "CANCELLED")) {
                sql.execute("INSERT INTO mavworks.bookings (buyer_id, listing_id, start_at, end_at, status, created_at) VALUES (1, 1, TIMESTAMP WITH TIME ZONE '2030-01-01 12:00:00+00', TIMESTAMP WITH TIME ZONE '2030-01-01 13:00:00+00', '" + status + "', CURRENT_TIMESTAMP)");
            }
            var migration = Flyway.configure().dataSource(source).schemas("mavworks").defaultSchema("mavworks")
                    .locations("classpath:db/migration/postgresql").target("11").load();
            assertEquals(1, migration.migrate().migrationsExecuted);
            try (var rows = sql.executeQuery("SELECT status, requester_id, conversation_id FROM mavworks.bookings ORDER BY id")) {
                for (String status : List.of("REQUESTED", "CONFIRMED", "CANCELLED", "CANCELLED")) {
                    assertTrue(rows.next()); assertEquals(status, rows.getString(1));
                    assertEquals(1L, rows.getLong(2)); assertTrue(rows.getLong(3) > 0);
                }
                assertFalse(rows.next());
            }
            try (var rows = sql.executeQuery("SELECT count(*) FROM mavworks.conversations")) { rows.next(); assertEquals(1, rows.getInt(1)); }
            assertEquals(0, migration.migrate().migrationsExecuted);
            for (int slot : List.of(0, 1, 2, 3, 49, 94, 95)) {
                sql.execute("INSERT INTO mavworks.availability (user_id, available_date, slot) VALUES (1, DATE '2030-01-01', " + slot + ")");
            }
            sql.execute("INSERT INTO mavworks.availability (user_id, available_date, slot) VALUES (1, DATE '2030-01-02', 48), (2, DATE '2030-01-01', 48)");
            var halfHours = Flyway.configure().dataSource(source).schemas("mavworks").defaultSchema("mavworks")
                    .locations("classpath:db/migration/postgresql").target("12").load();
            assertEquals(1, halfHours.migrate().migrationsExecuted);
            try (var rows = sql.executeQuery("SELECT user_id, available_date, slot FROM mavworks.availability ORDER BY slot")) {
                for (int slot : List.of(0, 1, 47)) {
                    assertTrue(rows.next()); assertEquals(1L, rows.getLong(1));
                    assertEquals("2030-01-01", rows.getString(2)); assertEquals(slot, rows.getInt(3));
                }
                assertFalse(rows.next());
            }
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO mavworks.availability (user_id, available_date, slot) VALUES (1, DATE '2030-01-01', 48)"));
            try (var rows = sql.executeQuery("SELECT count(*) FROM mavworks.bookings WHERE start_at = TIMESTAMP WITH TIME ZONE '2030-01-01 12:00:00+00' AND end_at = TIMESTAMP WITH TIME ZONE '2030-01-01 13:00:00+00'")) {
                rows.next(); assertEquals(4, rows.getInt(1));
            }
        }
    }
}
