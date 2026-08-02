package org.example.finzin;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for any test that needs a real Postgres instance (repository tests, full
 * {@code @SpringBootTest} integration tests). Never point tests at the datasource configured in
 * application.properties — that is a live, shared Neon database, not a disposable test fixture.
 *
 * Uses the pgvector image (not plain postgres) so DatabaseMigration's {@code CREATE EXTENSION
 * vector} / {@code ai_document_embeddings.embedding vector(1536)} raw-SQL steps succeed for tests
 * that exercise the AI/RAG path. {@code disabledWithoutDocker = true} makes every subclass
 * auto-skip (not fail) in environments with no Docker daemon, e.g. this sandbox.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("finzin_test")
            .withUsername("finzin_test")
            .withPassword("finzin_test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
