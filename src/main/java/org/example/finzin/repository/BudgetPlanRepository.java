package org.example.finzin.repository;

import org.example.finzin.entity.BudgetPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetPlanRepository extends JpaRepository<BudgetPlanEntity, Long> {
    List<BudgetPlanEntity> findByUserId(Long userId);
    List<BudgetPlanEntity> findByUserIdAndStatus(Long userId, String status);
    void deleteByUserId(Long userId);
}
