package org.example.finzin.repository;

import org.example.finzin.entity.AiConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversationEntity, Long> {
    List<AiConversationEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
