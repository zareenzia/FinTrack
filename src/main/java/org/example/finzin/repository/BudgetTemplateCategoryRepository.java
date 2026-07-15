package org.example.finzin.repository;

import org.example.finzin.entity.BudgetTemplateCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetTemplateCategoryRepository extends JpaRepository<BudgetTemplateCategoryEntity, Long> {
    List<BudgetTemplateCategoryEntity> findByTemplateId(Long templateId);
    void deleteByTemplateId(Long templateId);
}
