package com.mavworks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginRecoveryMigrationTest {
    @Test void existingAccountsRemainUnlockedAndTokensMustBeUnique() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:recovery-migration;MODE=PostgreSQL", "sa", "");
                var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE app_users (id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO app_users (id) VALUES (1), (2)");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/postgresql/V17__login_recovery.sql"));
            try (var rows = sql.executeQuery("SELECT * FROM app_users WHERE id = 1")) {
                assertTrue(rows.next()); assertFalse(rows.getBoolean("login_locked"));
                assertEquals(0, rows.getInt("failed_login_attempts")); assertEquals(0, rows.getLong("security_version"));
                assertNull(rows.getString("recovery_token_hash"));
            }
            sql.execute("UPDATE app_users SET recovery_token_hash = 'hash' WHERE id = 1");
            assertThrows(java.sql.SQLException.class, () -> sql.execute("UPDATE app_users SET recovery_token_hash = 'hash' WHERE id = 2"));
        }
    }
}
