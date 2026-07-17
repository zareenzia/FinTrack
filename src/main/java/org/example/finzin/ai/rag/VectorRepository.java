package org.example.finzin.ai.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Handles the `embedding vector(1536)` column directly via JDBC — deliberately outside JPA's
 * type system (see AiDocumentEmbeddingEntity for why). Every query here is scoped by user_id;
 * this is what keeps semantic search from ever crossing user boundaries.
 */
@Repository
public class VectorRepository {
    private final JdbcTemplate jdbcTemplate;

    public VectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertEmbedding(Long documentId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE ai_document_embeddings SET embedding = ?::vector WHERE id = ?",
                toVectorLiteral(embedding), documentId
        );
    }

    public record SimilarityResult(String entityType, Long entityId, String title, String content, String metadata, double score) {}

    public List<SimilarityResult> searchSimilar(Long userId, float[] queryEmbedding, int limit) {
        String literal = toVectorLiteral(queryEmbedding);
        String sql = "SELECT entity_type, entity_id, title, content, metadata, " +
                "1 - (embedding <=> ?::vector) AS similarity " +
                "FROM ai_document_embeddings " +
                "WHERE user_id = ? AND embedding IS NOT NULL " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SimilarityResult(
                rs.getString("entity_type"),
                rs.getLong("entity_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getDouble("similarity")
        ), literal, userId, literal, limit);
    }

    /** Same as {@link #searchSimilar(Long, float[], int)}, additionally restricted to one entity type when non-null. */
    public List<SimilarityResult> searchSimilar(Long userId, float[] queryEmbedding, int limit, String entityTypeOrNull) {
        if (entityTypeOrNull == null) {
            return searchSimilar(userId, queryEmbedding, limit);
        }
        String literal = toVectorLiteral(queryEmbedding);
        String sql = "SELECT entity_type, entity_id, title, content, metadata, " +
                "1 - (embedding <=> ?::vector) AS similarity " +
                "FROM ai_document_embeddings " +
                "WHERE user_id = ? AND entity_type = ? AND embedding IS NOT NULL " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SimilarityResult(
                rs.getString("entity_type"),
                rs.getLong("entity_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getDouble("similarity")
        ), literal, userId, entityTypeOrNull, literal, limit);
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
