package org.example.finzin.repository;

import org.example.finzin.entity.GamificationSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GamificationSettingsRepository extends JpaRepository<GamificationSettingsEntity, Long> {
    Optional<GamificationSettingsEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
