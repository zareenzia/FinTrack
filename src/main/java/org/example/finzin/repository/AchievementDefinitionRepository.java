package org.example.finzin.repository;

import org.example.finzin.entity.AchievementDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AchievementDefinitionRepository extends JpaRepository<AchievementDefinitionEntity, Long> {
    List<AchievementDefinitionEntity> findByActiveTrue();
    List<AchievementDefinitionEntity> findByMetricKeyAndActiveTrue(String metricKey);
    Optional<AchievementDefinitionEntity> findByCode(String code);
}
