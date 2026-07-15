package org.example.finzin.repository;

import org.example.finzin.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndIsReadFalse(Long userId);
    boolean existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(Long userId, String type, Long relatedEntityId, LocalDateTime after);
}
