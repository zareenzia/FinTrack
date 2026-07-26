package org.example.finzin.repository;

import org.example.finzin.entity.XpHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface XpHistoryRepository extends JpaRepository<XpHistoryEntity, Long> {
    List<XpHistoryEntity> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndSourceTypeAndSourceIdAndReason(Long userId, String sourceType, String sourceId, String reason);
    void deleteByUserId(Long userId);
}
