package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AiDocumentEmbeddingEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiDocumentEmbeddingEntity deliberately does not map the pgvector {@code embedding vector(1536)}
 * column (see its Javadoc) - Hibernate schema management never touches it, and reads/writes go
 * through a separate JDBC-based VectorRepository. This test therefore only exercises the entity's
 * plain metadata columns, which is all AiDocumentEmbeddingRepository's derived queries touch anyway.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiDocumentEmbeddingRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AiDocumentEmbeddingRepository repository;

    private AiDocumentEmbeddingEntity embedding(Long userId, String entityType, Long entityId) {
        AiDocumentEmbeddingEntity e = new AiDocumentEmbeddingEntity();
        e.setUserId(userId);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setTitle("Transaction summary");
        e.setContent("Groceries at SuperMart for 45.00");
        e.setContentHash("hash-1");
        return e;
    }

    @Test
    void findByUserIdAndEntityTypeAndEntityIdReturnsMatchingRow() {
        entityManager.persistAndFlush(embedding(1L, "TRANSACTION", 100L));
        entityManager.persistAndFlush(embedding(1L, "TRANSACTION", 101L));
        entityManager.persistAndFlush(embedding(2L, "TRANSACTION", 100L));
        entityManager.clear();

        Optional<AiDocumentEmbeddingEntity> result =
                repository.findByUserIdAndEntityTypeAndEntityId(1L, "TRANSACTION", 100L);

        assertTrue(result.isPresent());
        assertEquals("Transaction summary", result.get().getTitle());
    }

    @Test
    void findByUserIdAndEntityTypeAndEntityIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(embedding(1L, "TRANSACTION", 100L));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndEntityTypeAndEntityId(1L, "BUDGET", 100L).isEmpty());
        assertTrue(repository.findByUserIdAndEntityTypeAndEntityId(1L, "TRANSACTION", 999L).isEmpty());
        assertTrue(repository.findByUserIdAndEntityTypeAndEntityId(999L, "TRANSACTION", 100L).isEmpty());
    }

    @Test
    void deleteByUserIdAndEntityTypeAndEntityIdRemovesOnlyThatSpecificRow() {
        AiDocumentEmbeddingEntity e1 = entityManager.persistAndFlush(embedding(1L, "TRANSACTION", 100L));
        AiDocumentEmbeddingEntity e2 = entityManager.persistAndFlush(embedding(1L, "TRANSACTION", 101L));
        entityManager.clear();

        repository.deleteByUserIdAndEntityTypeAndEntityId(1L, "TRANSACTION", 100L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(e1.getId()).isEmpty());
        assertTrue(repository.findById(e2.getId()).isPresent());
    }

    @Test
    void deleteByUserIdRemovesAllOfThatUsersEmbeddings() {
        AiDocumentEmbeddingEntity e1 = entityManager.persistAndFlush(embedding(1L, "TRANSACTION", 100L));
        AiDocumentEmbeddingEntity e2 = entityManager.persistAndFlush(embedding(1L, "BUDGET", 200L));
        AiDocumentEmbeddingEntity e3 = entityManager.persistAndFlush(embedding(2L, "TRANSACTION", 100L));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(e1.getId()).isEmpty());
        assertTrue(repository.findById(e2.getId()).isEmpty());
        assertTrue(repository.findById(e3.getId()).isPresent());
    }
}
