package org.example.finzin.repository;

import org.example.finzin.entity.BudgetTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetTemplateRepository extends JpaRepository<BudgetTemplateEntity, Long> {
    List<BudgetTemplateEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
