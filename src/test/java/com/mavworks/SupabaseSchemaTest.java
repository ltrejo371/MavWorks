package com.mavworks;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks the migration against JPA mappings using H2 PostgreSQL compatibility mode.
 * This is not a substitute for testing a live PostgreSQL connection.
 */
@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false",
        "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:migration-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=mavworks",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.default-schema=mavworks",
        "spring.flyway.schemas=mavworks",
        "spring.flyway.create-schemas=true"
})
class SupabaseSchemaTest extends ProjectStructureTest {
}
