package org.example.finzin.repository;

import org.example.finzin.entity.AiMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessageEntity, Long> {
    List<AiMessageEntity> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    void deleteByConversationId(Long conversationId);
    void deleteByUserId(Long userId);
}
