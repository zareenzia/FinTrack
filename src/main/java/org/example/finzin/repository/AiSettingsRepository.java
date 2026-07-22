package org.example.finzin.repository;

import org.example.finzin.entity.AiSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiSettingsRepository extends JpaRepository<AiSettingsEntity, Long> {
    Optional<AiSettingsEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
