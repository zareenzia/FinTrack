package org.example.finzin.repository;

import org.example.finzin.entity.AiDocumentEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiDocumentEmbeddingRepository extends JpaRepository<AiDocumentEmbeddingEntity, Long> {
    Optional<AiDocumentEmbeddingEntity> findByUserIdAndEntityTypeAndEntityId(Long userId, String entityType, Long entityId);
    void deleteByUserIdAndEntityTypeAndEntityId(Long userId, String entityType, Long entityId);
    void deleteByUserId(Long userId);
}
