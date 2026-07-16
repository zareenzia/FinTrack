package org.example.finzin.ai.rag;

import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AiDocumentEmbeddingRepository;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 2A verification: exercises the RAG embedding/retrieval stack against the real OpenAI
 * Embeddings API and the real pgvector-backed store (same Neon DB the app uses). Skipped
 * automatically wherever OPENAI_API_KEY isn't set, e.g. CI without the secret.
 *
 * Uses reserved high userIds (900000001/900000002) that don't collide with real users, and a
 * single real TransactionEntity row to prove the actual create-path wiring; everything else goes
 * through EmbeddingService directly with synthetic entityIds so no other tables are touched.
 * All test rows are removed in @AfterEach.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SemanticSearchPhase2AVerificationTest {

    private static final long USER_A = 900_000_001L;
    private static final long USER_B = 900_000_002L;

    @Autowired private EmbeddingClient embeddingClient;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private SemanticSearchService semanticSearchService;
    @Autowired private DocumentIndexer documentIndexer;
    @Autowired private AiDocumentEmbeddingRepository documentRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long createdTransactionId;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_document_embeddings WHERE user_id IN (?, ?)", USER_A, USER_B);
        if (createdTransactionId != null) {
            transactionRepository.deleteById(createdTransactionId);
            createdTransactionId = null;
        }
    }

    @Test
    void embeddingIs1536DimensionalAndNonTrivial() {
        long start = System.nanoTime();
        float[] vector = embeddingClient.embed("grocery shopping at the supermarket");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(1536, vector.length, "text-embedding-3-small must return 1536 dimensions");
        boolean allZero = true;
        for (float f : vector) {
            if (f != 0f) { allZero = false; break; }
        }
        assertFalse(allZero, "embedding vector should not be all zeros");

        System.out.println("[Phase2A] embedding generation latency: " + elapsedMs + "ms");
    }

    @Test
    void semanticRetrievalFindsNewlyCreatedTransaction() throws InterruptedException {
        TransactionEntity tx = new TransactionEntity(USER_A, 45.50, "Grocery shopping at Walmart",
                null, "EXPENSE", LocalDateTime.now(), LocalDateTime.now());
        TransactionEntity saved = transactionRepository.save(tx);
        createdTransactionId = saved.getId();

        documentIndexer.indexTransaction(saved);
        awaitIndexed(USER_A, IndexedEntityType.TRANSACTION, saved.getId());

        long start = System.nanoTime();
        List<SemanticSearchService.SearchResult> results =
                semanticSearchService.search(USER_A, "Walmart grocery purchase", 5);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[Phase2A] semantic search latency: " + elapsedMs + "ms");

        assertTrue(results.stream().anyMatch(r ->
                        "TRANSACTION".equals(r.entityType()) && saved.getId().equals(r.entityId())),
                "expected the newly created transaction to be retrieved by semantic search");
    }

    @Test
    void fuzzySemanticSearchMatchesDifferentWording() {
        embeddingService.indexDocument(USER_A, IndexedEntityType.NOTE, 777_001L, "Weekly groceries",
                "Bought vegetables, rice, and fish at the local wet market for the week", Map.of());
        embeddingService.indexDocument(USER_A, IndexedEntityType.NOTE, 777_002L, "Car service",
                "Took the sedan in for an oil change and brake inspection at the garage", Map.of());

        List<SemanticSearchService.SearchResult> results =
                semanticSearchService.search(USER_A, "food and grocery expenses", 5);

        int groceryRank = indexOf(results, 777_001L);
        int carRank = indexOf(results, 777_002L);
        assertTrue(groceryRank >= 0, "grocery note should be retrieved for a differently-worded query");
        assertTrue(carRank == -1 || groceryRank < carRank,
                "grocery note should rank above the unrelated car-service note for a food-related query");
    }

    @Test
    void searchNeverReturnsAnotherUsersData() {
        String sharedContent = "UNIQUEMARKER private salary negotiation notes for this account";
        embeddingService.indexDocument(USER_A, IndexedEntityType.NOTE, 777_101L, "User A note", sharedContent, Map.of());
        embeddingService.indexDocument(USER_B, IndexedEntityType.NOTE, 777_101L, "User B note", sharedContent, Map.of());

        List<SemanticSearchService.SearchResult> resultsForA =
                semanticSearchService.search(USER_A, "UNIQUEMARKER private salary negotiation", 10);

        // Both users hold identical content under the same entityId; only USER_A's own row may
        // ever come back for a USER_A query, despite USER_B's row being an equally strong match.
        assertEquals(1, resultsForA.size(), "search must not cross the user_id boundary");
    }

    @Test
    void embeddingSyncsOnCreateUpdateAndDelete() {
        long entityId = 777_201L;

        embeddingService.indexDocument(USER_A, IndexedEntityType.TODO, entityId, "Task v1",
                "Finish the quarterly report", Map.of());
        var afterCreate = documentRepository
                .findByUserIdAndEntityTypeAndEntityId(USER_A, IndexedEntityType.TODO.name(), entityId)
                .orElseThrow(() -> new AssertionError("document should exist after create"));
        String hashAfterCreate = afterCreate.getContentHash();
        Long rowId = afterCreate.getId();

        embeddingService.indexDocument(USER_A, IndexedEntityType.TODO, entityId, "Task v1",
                "Finish the quarterly report", Map.of());
        var afterNoopReindex = documentRepository
                .findByUserIdAndEntityTypeAndEntityId(USER_A, IndexedEntityType.TODO.name(), entityId)
                .orElseThrow();
        assertEquals(hashAfterCreate, afterNoopReindex.getContentHash(), "unchanged content must keep the same hash");
        assertEquals(rowId, afterNoopReindex.getId(), "unchanged content must not create a duplicate row");

        embeddingService.indexDocument(USER_A, IndexedEntityType.TODO, entityId, "Task v2",
                "Draft the annual budget proposal", Map.of());
        var afterUpdate = documentRepository
                .findByUserIdAndEntityTypeAndEntityId(USER_A, IndexedEntityType.TODO.name(), entityId)
                .orElseThrow();
        assertEquals(rowId, afterUpdate.getId(), "update must upsert the same row, not create a new one");
        assertNotEquals(hashAfterCreate, afterUpdate.getContentHash(), "changed content must produce a new hash");

        List<SemanticSearchService.SearchResult> afterUpdateSearch =
                semanticSearchService.search(USER_A, "annual budget proposal", 5);
        assertTrue(afterUpdateSearch.stream().anyMatch(r -> Long.valueOf(entityId).equals(r.entityId())),
                "updated content must be searchable under its new wording");

        embeddingService.deleteDocument(USER_A, IndexedEntityType.TODO, entityId);
        assertTrue(documentRepository.findByUserIdAndEntityTypeAndEntityId(USER_A, IndexedEntityType.TODO.name(), entityId).isEmpty(),
                "document row must be gone after delete");
    }

    private void awaitIndexed(long userId, IndexedEntityType type, Long entityId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var doc = documentRepository.findByUserIdAndEntityTypeAndEntityId(userId, type.name(), entityId);
            if (doc.isPresent()) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_document_embeddings WHERE id = ? AND embedding IS NOT NULL",
                        Integer.class, doc.get().getId());
                if (count != null && count > 0) return;
            }
            Thread.sleep(200);
        }
        fail("document was not asynchronously indexed within timeout");
    }

    private int indexOf(List<SemanticSearchService.SearchResult> results, Long entityId) {
        for (int i = 0; i < results.size(); i++) {
            if (entityId.equals(results.get(i).entityId())) return i;
        }
        return -1;
    }
}
