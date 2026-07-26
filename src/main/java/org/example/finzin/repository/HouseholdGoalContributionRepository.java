package org.example.finzin.repository;

import org.example.finzin.entity.HouseholdGoalContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseholdGoalContributionRepository extends JpaRepository<HouseholdGoalContributionEntity, Long> {
    List<HouseholdGoalContributionEntity> findByHouseholdGoalIdOrderByContributedAtDesc(Long householdGoalId);
    List<HouseholdGoalContributionEntity> findByHouseholdGoalIdIn(List<Long> householdGoalIds);
    Optional<HouseholdGoalContributionEntity> findByTransactionId(Long transactionId);
    void deleteByHouseholdGoalId(Long householdGoalId);
    void deleteByTransactionId(Long transactionId);
}
