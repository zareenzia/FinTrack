package org.example.finzin.service;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.repository.BudgetPlanRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily backstop for budget alerts; per-login catch-up in AuthController covers the common case (mirrors RecurringTransactionScheduler). */
@Component
public class BudgetScheduler {

    private final BudgetPlanRepository budgetPlanRepository;
    private final BudgetPlanService budgetPlanService;

    public BudgetScheduler(BudgetPlanRepository budgetPlanRepository, BudgetPlanService budgetPlanService) {
        this.budgetPlanRepository = budgetPlanRepository;
        this.budgetPlanService = budgetPlanService;
    }

    @Scheduled(cron = "0 10 0 * * *")
    public void runDailyThresholdCheck() {
        checkAllActivePlans();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        new Thread(this::checkAllActivePlans, "budget-alert-init-sync").start();
    }

    public void checkAllActivePlans() {
        for (BudgetPlanEntity plan : budgetPlanRepository.findAll()) {
            if (!"ACTIVE".equals(plan.getStatus())) continue;
            if (plan.getEndDate().isBefore(java.time.LocalDate.now())) continue;
            budgetPlanService.checkAndNotifyThresholds(plan);
        }
    }

    public void checkPlansForUser(Long userId) {
        for (BudgetPlanEntity plan : budgetPlanRepository.findByUserIdAndStatus(userId, "ACTIVE")) {
            if (plan.getEndDate().isBefore(java.time.LocalDate.now())) continue;
            budgetPlanService.checkAndNotifyThresholds(plan);
        }
    }
}
