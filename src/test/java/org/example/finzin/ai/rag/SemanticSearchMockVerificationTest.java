package org.example.finzin.ai.rag;

import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AiDocumentEmbeddingRepository;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the whole RAG pipeline (indexing, storage, retrieval, isolation, entity filtering) against
 * {@link MockEmbeddingClient} — no OpenAI dependency, so unlike {@link SemanticSearchPhase2AVerificationTest}
 * this always runs, including in CI without OPENAI_API_KEY. Mock vectors carry no semantic meaning,
 * so this deliberately does not assert on retrieval *quality* (fuzzy/reworded-query ranking) — only
 * the real-API-gated test class covers that.
 */
@SpringBootTest(properties = "ai.embedding.provider=mock")
class SemanticSearchMockVerificationTest {

    private static final long USER_A = 900_000_101L;
    private static final long USER_B = 900_000_102L;

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
    void embeddingClientResolvesToMockImplementation() {
        assertInstanceOf(MockEmbeddingClient.class, embeddingClient,
                "ai.embedding.provider=mock must select MockEmbeddingClient, not OpenAIEmbeddingClient");
    }

    @Test
    void embeddingIs1536DimensionalAndDeterministic() {
        float[] first = embeddingClient.embed("grocery shopping");
        float[] second = embeddingClient.embed("grocery shopping");
        float[] different = embeddingClient.embed("car maintenance");

        assertEquals(1536, first.length);
        assertArrayEquals(first, second);
        assertFalse(java.util.Arrays.equals(first, different));
    }

    @Test
    void semanticRetrievalFindsNewlyCreatedTransaction() {
        TransactionEntity tx = new TransactionEntity(USER_A, 45.50, "Grocery shopping at Walmart",
                null, "EXPENSE", LocalDateTime.now(), LocalDateTime.now());
        TransactionEntity saved = transactionRepository.save(tx);
        createdTransactionId = saved.getId();

        documentIndexer.indexTransaction(saved);
        awaitIndexed(USER_A, IndexedEntityType.TRANSACTION, saved.getId());

        List<SemanticSearchService.SearchResult> results = semanticSearchService.search(USER_A, "Walmart grocery purchase", 5);

        assertTrue(results.stream().anyMatch(r ->
                "TRANSACTION".equals(r.entityType()) && saved.getId().equals(r.entityId())));
    }

    @Test
    void searchNeverReturnsAnotherUsersData() {
        String sharedContent = "UNIQUEMARKER shared content for isolation check";
        embeddingService.indexDocument(USER_A, IndexedEntityType.NOTE, 777_301L, "User A note", sharedContent, Map.of());
        embeddingService.indexDocument(USER_B, IndexedEntityType.NOTE, 777_301L, "User B note", sharedContent, Map.of());

        List<SemanticSearchService.SearchResult> resultsForA = semanticSearchService.search(USER_A, "UNIQUEMARKER shared content", 10);

        assertEquals(1, resultsForA.size(), "search must not cross the user_id boundary");
    }

    @Test
    void embeddingSyncsOnCreateUpdateAndDelete() {
        long entityId = 777_302L;

        embeddingService.indexDocument(USER_A, IndexedEntityType.TODO, entityId, "Task v1", "Finish the report", Map.of());
        var afterCreate = documentRepository.findByUserIdAndEntityTypeAndEntityId(USER_A, IndexedEntityType.TODO.name(), entityId)
                .orElseThrow(() -> new AssertionError("document should exist after create"));
        Long rowId = afterCreate.getId();
        String hashAfterCreate = afterCreate.getContentHash();

        embeddingService.indexDocument(USER_A, IndexedEntityType.TODO, entityId, "Task v2", "Draft the proposal", Map.of());
        var afterUpdate = documentRepository.findByUserIdAndEntityTypeAndEntityId(USER_A, IndexedEntityType.TODO.name(), entityId)
                .orElseThrow();
        assertEquals(rowId, afterUpdate.getId(), "update must upsert the same row");
        assertNotEquals(hashAfterCreate, afterUpdate.getContentHash());

        List<SemanticSearchService.SearchResult> afterUpdateSearch = semanticSearchService.search(USER_A, "the proposal", 5);
        assertTrue(afterUpdateSearch.stream().anyMatch(r -> Long.valueOf(entityId).equals(r.entityId())));

        embeddingService.deleteDocument(USER_A, IndexedEntityType.TODO, entityId);
        assertTrue(documentRepository.findByUserIdAndEntityTypeAndEntityId(USER_A, IndexedEntityType.TODO.name(), entityId).isEmpty());
    }

    @Test
    void entityTypeFilterRestrictsResults() {
        embeddingService.indexDocument(USER_A, IndexedEntityType.NOTE, 777_303L, "Filtered note", "budgeting thoughts", Map.of());
        embeddingService.indexDocument(USER_A, IndexedEntityType.TODO, 777_304L, "Filtered todo", "budgeting task", Map.of());

        List<SemanticSearchService.SearchResult> noteOnly = semanticSearchService.search(USER_A, "budgeting", 10, IndexedEntityType.NOTE);

        assertFalse(noteOnly.isEmpty());
        assertTrue(noteOnly.stream().allMatch(r -> "NOTE".equals(r.entityType())));
    }

    @Test
    void floatArrayOverloadSkipsReEmbeddingAndStillFindsTheDocument() {
        embeddingService.indexDocument(USER_A, IndexedEntityType.NOTE, 777_305L, "Precomputed vector note", "already embedded content", Map.of());

        float[] precomputed = embeddingClient.embed("already embedded content");
        List<SemanticSearchService.SearchResult> results = semanticSearchService.search(USER_A, precomputed, 5);

        assertTrue(results.stream().anyMatch(r -> Long.valueOf(777_305L).equals(r.entityId())));
    }

    private void awaitIndexed(long userId, IndexedEntityType type, Long entityId) {
        for (int i = 0; i < 50; i++) {
            var doc = documentRepository.findByUserIdAndEntityTypeAndEntityId(userId, type.name(), entityId);
            if (doc.isPresent()) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_document_embeddings WHERE id = ? AND embedding IS NOT NULL",
                        Integer.class, doc.get().getId());
                if (count != null && count > 0) return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for async indexing");
            }
        }
        fail("document was not asynchronously indexed within timeout");
    }
}
