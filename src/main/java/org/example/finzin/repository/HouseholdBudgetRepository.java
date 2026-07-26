package org.example.finzin.repository;

import org.example.finzin.entity.HouseholdBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HouseholdBudgetRepository extends JpaRepository<HouseholdBudgetEntity, Long> {
    List<HouseholdBudgetEntity> findByHouseholdIdOrderByCategoryNameAsc(Long householdId);
    void deleteByHouseholdId(Long householdId);
}
