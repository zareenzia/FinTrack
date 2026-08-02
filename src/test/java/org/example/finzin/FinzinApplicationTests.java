package org.example.finzin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Extends AbstractIntegrationTest so the full application context (including
 * DatabaseMigration's startup SQL) is verified against a disposable Testcontainers Postgres
 * instance instead of the live datasource configured in application.properties.
 */
@SpringBootTest
class FinzinApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}
