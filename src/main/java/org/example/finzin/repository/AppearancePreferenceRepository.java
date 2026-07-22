package org.example.finzin.repository;

import org.example.finzin.entity.AppearancePreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppearancePreferenceRepository extends JpaRepository<AppearancePreferenceEntity, Long> {
    Optional<AppearancePreferenceEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
