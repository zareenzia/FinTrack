package org.example.finzin.repository;

import org.example.finzin.entity.HouseholdGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HouseholdGoalRepository extends JpaRepository<HouseholdGoalEntity, Long> {
    List<HouseholdGoalEntity> findByHouseholdIdOrderByCreatedAtDesc(Long householdId);
    void deleteByHouseholdId(Long householdId);
}
