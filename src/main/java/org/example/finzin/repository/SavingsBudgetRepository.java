package org.example.finzin.repository;

import org.example.finzin.entity.SavingsBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsBudgetRepository extends JpaRepository<SavingsBudgetEntity, Long> {
    List<SavingsBudgetEntity> findByBudgetPlanId(Long budgetPlanId);
    Optional<SavingsBudgetEntity> findByBudgetPlanIdAndCategoryId(Long budgetPlanId, Long categoryId);
}
