package org.example.finzin.ai.rag;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Given an authenticated user and a natural-language query, returns the most relevant indexed
 * documents ordered by similarity. Wired into the chat flow in Phase 2B via {@code AIService}.
 */
@Service
public class SemanticSearchService {
    private final EmbeddingClient embeddingClient;
    private final VectorRepository vectorRepository;

    public SemanticSearchService(EmbeddingClient embeddingClient, VectorRepository vectorRepository) {
        this.embeddingClient = embeddingClient;
        this.vectorRepository = vectorRepository;
    }

    public record SearchResult(String entityType, Long entityId, String title, String content, String metadata, double score) {}

    public List<SearchResult> search(Long userId, String query, int limit) {
        float[] queryEmbedding = embeddingClient.embed(query);
        return searchByVector(userId, queryEmbedding, limit, null);
    }

    /** Same as {@link #search(Long, String, int)}, restricted to one entity type. */
    public List<SearchResult> search(Long userId, String query, int limit, IndexedEntityType entityType) {
        float[] queryEmbedding = embeddingClient.embed(query);
        return searchByVector(userId, queryEmbedding, limit, entityType);
    }

    /** Same as {@link #search(Long, String, int)}, but skips re-embedding for an already-computed query vector. */
    public List<SearchResult> search(Long userId, float[] queryEmbedding, int limit) {
        return searchByVector(userId, queryEmbedding, limit, null);
    }

    private List<SearchResult> searchByVector(Long userId, float[] queryEmbedding, int limit, IndexedEntityType entityType) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String entityTypeOrNull = entityType != null ? entityType.name() : null;
        return vectorRepository.searchSimilar(userId, queryEmbedding, safeLimit, entityTypeOrNull).stream()
                .map(r -> new SearchResult(r.entityType(), r.entityId(), r.title(), r.content(), r.metadata(), r.score()))
                .collect(Collectors.toList());
    }
}
