package org.example.finzin.repository;

import org.example.finzin.entity.VoiceSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoiceSettingsRepository extends JpaRepository<VoiceSettingsEntity, Long> {
    Optional<VoiceSettingsEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
