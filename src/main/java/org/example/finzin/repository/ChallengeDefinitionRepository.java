package org.example.finzin.repository;

import org.example.finzin.entity.ChallengeDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChallengeDefinitionRepository extends JpaRepository<ChallengeDefinitionEntity, Long> {
    List<ChallengeDefinitionEntity> findByActiveTrue();
    List<ChallengeDefinitionEntity> findByMetricKeyAndActiveTrue(String metricKey);
}
