package org.example.finzin.ai.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for SemanticSearchService, complementing SemanticSearchMockVerificationTest
 * and SemanticSearchPhase2AVerificationTest. Those two boot the real Spring context against a real
 * (mock-provider or OpenAI-backed) embedding pipeline and Postgres-backed VectorRepository, and
 * assert on end-to-end retrieval *behavior* (does the right document come back). Neither exercises
 * this class's own arithmetic in isolation: the {@code safeLimit} clamp (limit <= 0, limit > 50) and
 * the exact delegation/field-mapping between {@link VectorRepository.SimilarityResult} and
 * {@link SemanticSearchService.SearchResult} — including that the float-array overload never
 * re-embeds. Both collaborators are mocked here so that logic is what's actually under test.
 */
@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private EmbeddingClient embeddingClient;
    @Mock private VectorRepository vectorRepository;

    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        service = new SemanticSearchService(embeddingClient, vectorRepository);
    }

    private VectorRepository.SimilarityResult similarity(String entityType, long entityId, double score) {
        return new VectorRepository.SimilarityResult(entityType, entityId, "Title", "Content", "{\"k\":\"v\"}", score);
    }

    // ================================================================================
    // search(query) — embeds the query then delegates with entityType=null
    // ================================================================================

    @Test
    void searchByQueryEmbedsTheQueryThenDelegatesWithNullEntityType() {
        float[] vector = {0.1f, 0.2f};
        when(embeddingClient.embed("groceries")).thenReturn(vector);
        when(vectorRepository.searchSimilar(eq(USER_ID), eq(vector), eq(5), eq(null)))
                .thenReturn(List.of(similarity("TRANSACTION", 10L, 0.9)));

        List<SemanticSearchService.SearchResult> results = service.search(USER_ID, "groceries", 5);

        assertEquals(1, results.size());
        verify(vectorRepository).searchSimilar(USER_ID, vector, 5, null);
    }

    @Test
    void searchByQueryWithEntityTypePassesItsNameToTheRepository() {
        float[] vector = {0.3f};
        when(embeddingClient.embed("task")).thenReturn(vector);
        when(vectorRepository.searchSimilar(eq(USER_ID), eq(vector), anyInt(), eq("NOTE")))
                .thenReturn(List.of());

        service.search(USER_ID, "task", 10, IndexedEntityType.NOTE);

        verify(vectorRepository).searchSimilar(USER_ID, vector, 10, "NOTE");
    }

    // ================================================================================
    // search(vector) — never re-embeds
    // ================================================================================

    @Test
    void searchByPrecomputedVectorNeverCallsTheEmbeddingClient() {
        float[] precomputed = {0.7f, 0.8f};
        when(vectorRepository.searchSimilar(eq(USER_ID), eq(precomputed), anyInt(), eq(null)))
                .thenReturn(List.of());

        service.search(USER_ID, precomputed, 5);

        verifyNoInteractions(embeddingClient);
        verify(vectorRepository).searchSimilar(USER_ID, precomputed, 5, null);
    }

    // ================================================================================
    // safeLimit clamping: 1..50 inclusive
    // ================================================================================

    @Test
    void limitOfZeroOrNegativeIsClampedUpToOne() {
        float[] vector = {0f};
        when(embeddingClient.embed(any())).thenReturn(vector);
        when(vectorRepository.searchSimilar(any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search(USER_ID, "q", 0);
        service.search(USER_ID, "q", -5);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(vectorRepository, org.mockito.Mockito.times(2))
                .searchSimilar(eq(USER_ID), eq(vector), limitCaptor.capture(), eq(null));
        assertEquals(List.of(1, 1), limitCaptor.getAllValues());
    }

    @Test
    void limitAboveFiftyIsClampedDownToFifty() {
        float[] vector = {0f};
        when(embeddingClient.embed(any())).thenReturn(vector);
        when(vectorRepository.searchSimilar(any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search(USER_ID, "q", 1000);

        verify(vectorRepository).searchSimilar(USER_ID, vector, 50, null);
    }

    @Test
    void limitWithinRangeIsPassedThroughUnchanged() {
        float[] vector = {0f};
        when(embeddingClient.embed(any())).thenReturn(vector);
        when(vectorRepository.searchSimilar(any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search(USER_ID, "q", 25);

        verify(vectorRepository).searchSimilar(USER_ID, vector, 25, null);
    }

    // ================================================================================
    // Field mapping from VectorRepository.SimilarityResult to SearchResult
    // ================================================================================

    @Test
    void mapsEverySimilarityResultFieldOntoSearchResultInOrder() {
        when(embeddingClient.embed(any())).thenReturn(new float[]{1f});
        when(vectorRepository.searchSimilar(any(), any(), anyInt(), any()))
                .thenReturn(List.of(similarity("PURCHASE_ITEM", 77L, 0.42)));

        List<SemanticSearchService.SearchResult> results = service.search(USER_ID, "q", 5);

        SemanticSearchService.SearchResult result = results.get(0);
        assertEquals("PURCHASE_ITEM", result.entityType());
        assertEquals(77L, result.entityId());
        assertEquals("Title", result.title());
        assertEquals("Content", result.content());
        assertEquals("{\"k\":\"v\"}", result.metadata());
        assertEquals(0.42, result.score());
    }

    @Test
    void emptyRepositoryResultProducesEmptyList() {
        when(embeddingClient.embed(any())).thenReturn(new float[]{1f});
        when(vectorRepository.searchSimilar(any(), any(), anyInt(), any())).thenReturn(List.of());

        List<SemanticSearchService.SearchResult> results = service.search(USER_ID, "q", 5);

        assertEquals(0, results.size());
    }
}
