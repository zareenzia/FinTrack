package org.example.finzin.repository;

import org.example.finzin.entity.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<BudgetEntity, Long> {
    List<BudgetEntity> findByUserId(Long userId);
    List<BudgetEntity> findByUserIdAndPeriod(Long userId, String period);
    Optional<BudgetEntity> findByUserIdAndCategoryIdAndPeriod(Long userId, Long categoryId, String period);
}
