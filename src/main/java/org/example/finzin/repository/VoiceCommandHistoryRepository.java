package org.example.finzin.repository;

import org.example.finzin.entity.VoiceCommandHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoiceCommandHistoryRepository extends JpaRepository<VoiceCommandHistoryEntity, Long> {
    List<VoiceCommandHistoryEntity> findTop50ByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    Optional<VoiceCommandHistoryEntity> findByIdAndUserId(Long id, Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
