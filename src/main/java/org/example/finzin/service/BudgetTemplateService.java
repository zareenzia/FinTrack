package org.example.finzin.service;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.BudgetTemplateCategoryEntity;
import org.example.finzin.entity.BudgetTemplateEntity;
import org.example.finzin.repository.BudgetTemplateCategoryRepository;
import org.example.finzin.repository.BudgetTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BudgetTemplateService {

    private final BudgetTemplateRepository budgetTemplateRepository;
    private final BudgetTemplateCategoryRepository budgetTemplateCategoryRepository;
    private final BudgetPlanService budgetPlanService;

    public BudgetTemplateService(BudgetTemplateRepository budgetTemplateRepository,
                                  BudgetTemplateCategoryRepository budgetTemplateCategoryRepository,
                                  BudgetPlanService budgetPlanService) {
        this.budgetTemplateRepository = budgetTemplateRepository;
        this.budgetTemplateCategoryRepository = budgetTemplateCategoryRepository;
        this.budgetPlanService = budgetPlanService;
    }

    public List<BudgetTemplateEntity> listForUser(Long userId) {
        return budgetTemplateRepository.findByUserId(userId);
    }

    public BudgetTemplateEntity findOwnedById(Long id, Long userId) {
        BudgetTemplateEntity entity = budgetTemplateRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return null;
        }
        return entity;
    }

    public List<BudgetTemplateCategoryEntity> getRows(Long templateId) {
        return budgetTemplateCategoryRepository.findByTemplateId(templateId);
    }

    @Transactional
    public BudgetTemplateEntity save(BudgetTemplateEntity entity, List<BudgetTemplateCategoryEntity> rows) {
        BudgetTemplateEntity saved = budgetTemplateRepository.save(entity);
        budgetTemplateCategoryRepository.deleteByTemplateId(saved.getId());
        if (rows != null) {
            for (BudgetTemplateCategoryEntity row : rows) {
                row.setId(null);
                row.setTemplateId(saved.getId());
                budgetTemplateCategoryRepository.save(row);
            }
        }
        return saved;
    }

    @Transactional
    public void delete(Long templateId) {
        budgetTemplateCategoryRepository.deleteByTemplateId(templateId);
        budgetTemplateRepository.deleteById(templateId);
    }

    /** Creates a new BudgetPlan for the given period, seeded from this template's planned income/savings/category rows. */
    public BudgetPlanEntity applyTemplate(BudgetTemplateEntity template, String name, String periodType, String period,
                                           LocalDate startDate, LocalDate endDate) {
        BudgetPlanEntity plan = new BudgetPlanEntity();
        plan.setUserId(template.getUserId());
        plan.setName(name != null && !name.isBlank() ? name : template.getName());
        plan.setPeriodType(periodType);
        plan.setPeriod(period);
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        plan.setPlannedIncome(template.getPlannedIncome());
        plan.setPlannedSavings(template.getPlannedSavings());
        plan.setNotes(template.getNotes());
        plan.setStatus("ACTIVE");
        BudgetPlanEntity saved = budgetPlanService.save(plan);

        for (BudgetTemplateCategoryEntity row : getRows(template.getId())) {
            if (Boolean.TRUE.equals(row.getIsSavings())) {
                budgetPlanService.upsertSavingsBudget(saved, row.getCategoryId(), row.getPlannedAmount());
            } else {
                budgetPlanService.upsertCategoryBudget(saved, row.getCategoryId(), row.getPlannedAmount());
            }
        }
        return saved;
    }
}
