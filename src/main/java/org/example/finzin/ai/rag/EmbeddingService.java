package org.example.finzin.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.AiDocumentEmbeddingEntity;
import org.example.finzin.repository.AiDocumentEmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Entity-agnostic embedding orchestration: hash-check to avoid needless re-embedding, then
 * upsert metadata + vector. Knows nothing about Transactions/Notes/etc. specifically — that
 * belongs to DocumentMapper/DocumentIndexer.
 */
@Service
public class EmbeddingService {
    private final AiDocumentEmbeddingRepository documentRepository;
    private final VectorRepository vectorRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(AiDocumentEmbeddingRepository documentRepository, VectorRepository vectorRepository,
                             EmbeddingClient embeddingClient, ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.vectorRepository = vectorRepository;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    public void indexDocument(Long userId, IndexedEntityType entityType, Long entityId,
                              String title, String content, Map<String, Object> metadata) {
        String hash = sha256(content);
        AiDocumentEmbeddingEntity existing = documentRepository
                .findByUserIdAndEntityTypeAndEntityId(userId, entityType.name(), entityId)
                .orElse(null);

        if (existing != null && hash.equals(existing.getContentHash())) {
            return; // content unchanged since last index — skip re-embedding entirely
        }

        AiDocumentEmbeddingEntity entity = existing != null ? existing : new AiDocumentEmbeddingEntity();
        entity.setUserId(userId);
        entity.setEntityType(entityType.name());
        entity.setEntityId(entityId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setContentHash(hash);
        entity.setMetadata(writeMetadataJson(metadata));
        AiDocumentEmbeddingEntity saved = documentRepository.save(entity);

        float[] vector = embeddingClient.embed(content);
        vectorRepository.upsertEmbedding(saved.getId(), vector);
    }

    @Transactional
    public void deleteDocument(Long userId, IndexedEntityType entityType, Long entityId) {
        documentRepository.deleteByUserIdAndEntityTypeAndEntityId(userId, entityType.name(), entityId);
    }

    private String writeMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }
}
