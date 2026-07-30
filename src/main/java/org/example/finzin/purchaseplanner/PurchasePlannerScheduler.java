package org.example.finzin.purchaseplanner;

import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.repository.PurchaseItemRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.NotificationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Daily backstop for purchase-planner reminders (mirrors BudgetScheduler/RecurringTransactionScheduler). */
@Component
public class PurchasePlannerScheduler {

    private static final List<String> ACTIVE_STATUSES = List.of("PLANNING", "WAITING", "READY");

    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseAffordabilityService affordabilityService;
    private final BudgetPlanService budgetPlanService;
    private final NotificationService notificationService;

    public PurchasePlannerScheduler(PurchaseItemRepository purchaseItemRepository, PurchaseAffordabilityService affordabilityService,
                                     BudgetPlanService budgetPlanService, NotificationService notificationService) {
        this.purchaseItemRepository = purchaseItemRepository;
        this.affordabilityService = affordabilityService;
        this.budgetPlanService = budgetPlanService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 20 0 * * *")
    public void runDailyCheck() {
        checkAll();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        new Thread(this::checkAll, "purchase-planner-reminder-init-sync").start();
    }

    public void checkAll() {
        String currentMonth = YearMonth.now().toString();
        List<PurchaseItemEntity> active = purchaseItemRepository.findByStatusIn(ACTIVE_STATUSES);
        Map<Long, List<PurchaseItemEntity>> byUser = active.stream().collect(Collectors.groupingBy(PurchaseItemEntity::getUserId));

        for (Map.Entry<Long, List<PurchaseItemEntity>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            List<PurchaseItemEntity> items = entry.getValue();
            PurchaseAffordabilityService.AffordabilityContext ctx = affordabilityService.buildContext(userId, items);

            for (PurchaseItemEntity item : items) {
                checkTargetMonthReminder(item, currentMonth);
                checkAffordabilityTransition(item, ctx);
                checkSavingsGoalReached(item);
            }
        }
    }

    private void checkTargetMonthReminder(PurchaseItemEntity item, String currentMonth) {
        if (!currentMonth.equals(item.getTargetMonth())) return;
        notificationService.createIfNotRecent(item.getUserId(), "PURCHASE_PLANNED_THIS_MONTH",
                "Planned Purchase This Month",
                "You planned to buy " + item.getItemName() + " this month.",
                "PURCHASE_ITEM", item.getId());
    }

    /** Only notifies on the transition INTO "Can Buy Now" — the persisted lastAffordabilityStatus field
     * makes this idempotent (compares live vs. last-seen state), unlike a time-window dedupe. */
    private void checkAffordabilityTransition(PurchaseItemEntity item, PurchaseAffordabilityService.AffordabilityContext ctx) {
        String newStatus = affordabilityService.computeAffordability(item, ctx).status();
        String oldStatus = item.getLastAffordabilityStatus();
        if (!newStatus.equals(oldStatus)) {
            if ("CAN_BUY_NOW".equals(newStatus) && !"CAN_BUY_NOW".equals(oldStatus)) {
                notificationService.create(item.getUserId(), "PURCHASE_AFFORDABLE_NOW",
                        "Now Affordable",
                        item.getItemName() + " is now affordable.",
                        "PURCHASE_ITEM", item.getId());
            }
            item.setLastAffordabilityStatus(newStatus);
            purchaseItemRepository.save(item);
        }
    }

    private void checkSavingsGoalReached(PurchaseItemEntity item) {
        if (item.getLinkedSavingsGoalId() == null) return;
        if ("READY".equals(item.getStatus())) return;
        budgetPlanService.findSavingsBudgetStatus(item.getUserId(), item.getLinkedSavingsGoalId()).ifPresent(status -> {
            if ("GOAL_ACHIEVED".equals(status.get("status"))) {
                notificationService.createIfNotRecent(item.getUserId(), "PURCHASE_SAVINGS_GOAL_REACHED",
                        "Savings Goal Reached",
                        "Savings goal reached. Ready to purchase " + item.getItemName() + ".",
                        "PURCHASE_ITEM", item.getId());
            }
        });
    }
}
