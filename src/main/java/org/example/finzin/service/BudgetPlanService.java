package org.example.finzin.service;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.BudgetEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.SavingsBudgetEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.BudgetPlanRepository;
import org.example.finzin.repository.BudgetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.SavingsBudgetRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BudgetPlanService {

    private static final List<String> VALID_PERIOD_TYPES = List.of("MONTH", "QUARTER", "YEAR");

    private final BudgetPlanRepository budgetPlanRepository;
    private final BudgetRepository budgetRepository;
    private final SavingsBudgetRepository savingsBudgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;
    private final DocumentIndexer documentIndexer;

    public BudgetPlanService(BudgetPlanRepository budgetPlanRepository, BudgetRepository budgetRepository,
                             SavingsBudgetRepository savingsBudgetRepository, CategoryRepository categoryRepository,
                             TransactionRepository transactionRepository, AccountRepository accountRepository,
                             NotificationService notificationService, DocumentIndexer documentIndexer) {
        this.budgetPlanRepository = budgetPlanRepository;
        this.budgetRepository = budgetRepository;
        this.savingsBudgetRepository = savingsBudgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
        this.documentIndexer = documentIndexer;
    }

    // ============== CRUD ==============

    public List<BudgetPlanEntity> listForUser(Long userId, String period, String status, String search, String sortBy) {
        List<BudgetPlanEntity> plans = budgetPlanRepository.findByUserId(userId).stream()
                .filter(p -> period == null || period.isBlank() || p.getPeriod().equals(period))
                .filter(p -> status == null || status.isBlank() || p.getStatus().equalsIgnoreCase(status))
                .filter(p -> search == null || search.isBlank() || p.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        Comparator<BudgetPlanEntity> comparator = switch (sortBy == null ? "" : sortBy) {
            case "name" -> Comparator.comparing(BudgetPlanEntity::getName, String.CASE_INSENSITIVE_ORDER);
            case "startDate" -> Comparator.comparing(BudgetPlanEntity::getStartDate);
            default -> Comparator.comparing(BudgetPlanEntity::getStartDate).reversed();
        };
        plans.sort(comparator);
        return plans;
    }

    public BudgetPlanEntity findOwnedById(Long id, Long userId) {
        BudgetPlanEntity entity = budgetPlanRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return null;
        }
        return entity;
    }

    private static final Map<String, Integer> PERIOD_TYPE_SPECIFICITY = Map.of("MONTH", 0, "QUARTER", 1, "YEAR", 2);

    /**
     * When multiple active plans cover today (e.g. a monthly, a quarterly, and a yearly budget all
     * overlapping the current date), prefer the most specific one (MONTH over QUARTER over YEAR) —
     * that's the one a user actually wants to see as "today's budget", not whichever was created last.
     */
    public BudgetPlanEntity getCurrentPlan(Long userId) {
        LocalDate today = LocalDate.now();
        List<BudgetPlanEntity> matches = budgetPlanRepository
                .findByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(userId, "ACTIVE", today, today);
        return matches.stream()
                .min(Comparator.comparingInt(p -> PERIOD_TYPE_SPECIFICITY.getOrDefault(p.getPeriodType(), 99)))
                .orElse(null);
    }

    public String validate(String name, String periodType, String period, LocalDate startDate, LocalDate endDate) {
        if (name == null || name.isBlank()) return "Budget name is required";
        if (periodType == null || !VALID_PERIOD_TYPES.contains(periodType.toUpperCase(Locale.ROOT))) return "periodType must be MONTH, QUARTER, or YEAR";
        if (period == null || period.isBlank()) return "period is required";
        if (startDate == null || endDate == null) return "startDate and endDate are required";
        if (endDate.isBefore(startDate)) return "endDate must be on or after startDate";
        return null;
    }

    public BudgetPlanEntity save(BudgetPlanEntity entity) {
        BudgetPlanEntity saved = budgetPlanRepository.save(entity);
        documentIndexer.indexBudgetPlan(saved);
        return saved;
    }

    public void archive(BudgetPlanEntity plan) {
        plan.setStatus("ARCHIVED");
        budgetPlanRepository.save(plan);
        documentIndexer.indexBudgetPlan(plan);
    }

    public void delete(BudgetPlanEntity plan) {
        budgetRepository.findByBudgetPlanId(plan.getId()).forEach(b -> budgetRepository.deleteById(b.getId()));
        savingsBudgetRepository.findByBudgetPlanId(plan.getId()).forEach(s -> savingsBudgetRepository.deleteById(s.getId()));
        budgetPlanRepository.deleteById(plan.getId());
        documentIndexer.deleteBudgetPlan(plan.getUserId(), plan.getId());
    }

    public BudgetPlanEntity duplicate(BudgetPlanEntity source, String newName, String periodType, String period,
                                       LocalDate startDate, LocalDate endDate) {
        BudgetPlanEntity copy = new BudgetPlanEntity();
        copy.setUserId(source.getUserId());
        copy.setName(newName != null && !newName.isBlank() ? newName : source.getName() + " (Copy)");
        copy.setPeriodType(periodType);
        copy.setPeriod(period);
        copy.setStartDate(startDate);
        copy.setEndDate(endDate);
        copy.setPlannedIncome(source.getPlannedIncome());
        copy.setPlannedSavings(source.getPlannedSavings());
        copy.setNotes(source.getNotes());
        copy.setStatus("ACTIVE");
        BudgetPlanEntity saved = budgetPlanRepository.save(copy);
        copyCategoriesAndSavings(source.getId(), saved.getId());
        documentIndexer.indexBudgetPlan(saved);
        return saved;
    }

    /** "Copy Last Month" — finds the most recent prior plan ending before the new plan starts, and copies its allocations in. */
    public BudgetPlanEntity copyFromPreviousPlan(BudgetPlanEntity newPlan) {
        BudgetPlanEntity previous = budgetPlanRepository.findByUserId(newPlan.getUserId()).stream()
                .filter(p -> !p.getId().equals(newPlan.getId()))
                .filter(p -> p.getEndDate().isBefore(newPlan.getStartDate()))
                .max(Comparator.comparing(BudgetPlanEntity::getEndDate))
                .orElse(null);
        if (previous == null) {
            return newPlan;
        }
        newPlan.setPlannedIncome(previous.getPlannedIncome());
        newPlan.setPlannedSavings(previous.getPlannedSavings());
        budgetPlanRepository.save(newPlan);
        copyCategoriesAndSavings(previous.getId(), newPlan.getId());
        documentIndexer.indexBudgetPlan(newPlan);
        return newPlan;
    }

    private void copyCategoriesAndSavings(Long fromPlanId, Long toPlanId) {
        for (BudgetEntity b : budgetRepository.findByBudgetPlanId(fromPlanId)) {
            BudgetEntity copy = new BudgetEntity();
            copy.setUserId(b.getUserId());
            copy.setBudgetPlanId(toPlanId);
            copy.setCategoryId(b.getCategoryId());
            copy.setPeriod(b.getPeriod());
            copy.setBudgetAmount(b.getBudgetAmount());
            budgetRepository.save(copy);
        }
        for (SavingsBudgetEntity s : savingsBudgetRepository.findByBudgetPlanId(fromPlanId)) {
            SavingsBudgetEntity copy = new SavingsBudgetEntity();
            copy.setBudgetPlanId(toPlanId);
            copy.setCategoryId(s.getCategoryId());
            copy.setTargetAmount(s.getTargetAmount());
            copy.setInitialAmount(s.getInitialAmount());
            copy.setStorageAccountId(s.getStorageAccountId());
            copy.setSourceAccountId(s.getSourceAccountId());
            copy.setSourceDescription(s.getSourceDescription());
            savingsBudgetRepository.save(copy);
        }
    }

    // ============== Category & Savings allocations ==============

    public BudgetEntity upsertCategoryBudget(BudgetPlanEntity plan, Long categoryId, Double amount) {
        BudgetEntity entity = budgetRepository.findByBudgetPlanIdAndCategoryId(plan.getId(), categoryId).orElseGet(BudgetEntity::new);
        entity.setUserId(plan.getUserId());
        entity.setBudgetPlanId(plan.getId());
        entity.setCategoryId(categoryId);
        entity.setPeriod(plan.getPeriod());
        entity.setBudgetAmount(amount);
        BudgetEntity saved = budgetRepository.save(entity);
        documentIndexer.indexBudgetPlan(plan);
        return saved;
    }

    public SavingsBudgetEntity upsertSavingsBudget(BudgetPlanEntity plan, Long categoryId, Double targetAmount) {
        return upsertSavingsBudget(plan, categoryId, targetAmount, 0.0, null, null, null);
    }

    public SavingsBudgetEntity upsertSavingsBudget(BudgetPlanEntity plan, Long categoryId, Double targetAmount,
                                                    Double initialAmount, Long storageAccountId, Long sourceAccountId,
                                                    String sourceDescription) {
        SavingsBudgetEntity entity = savingsBudgetRepository.findByBudgetPlanIdAndCategoryId(plan.getId(), categoryId).orElseGet(SavingsBudgetEntity::new);
        entity.setBudgetPlanId(plan.getId());
        entity.setCategoryId(categoryId);
        entity.setTargetAmount(targetAmount);
        entity.setInitialAmount(initialAmount != null ? initialAmount : 0.0);
        entity.setStorageAccountId(storageAccountId);
        // An external source (bonus, gift, etc.) and a linked account are mutually exclusive — a real
        // account id always wins so a stale free-text description can't linger once one is picked.
        entity.setSourceAccountId(sourceAccountId);
        entity.setSourceDescription(sourceAccountId == null ? sourceDescription : null);
        SavingsBudgetEntity saved = savingsBudgetRepository.save(entity);
        documentIndexer.indexBudgetPlan(plan);
        return saved;
    }

    public void deleteCategoryBudgetById(Long budgetId) {
        budgetRepository.findById(budgetId).ifPresent(b -> budgetPlanRepository.findById(b.getBudgetPlanId())
                .ifPresent(plan -> {
                    budgetRepository.deleteById(budgetId);
                    documentIndexer.indexBudgetPlan(plan);
                }));
    }

    public void deleteSavingsBudgetById(Long savingsId) {
        savingsBudgetRepository.findById(savingsId).ifPresent(s -> budgetPlanRepository.findById(s.getBudgetPlanId())
                .ifPresent(plan -> {
                    savingsBudgetRepository.deleteById(savingsId);
                    documentIndexer.indexBudgetPlan(plan);
                }));
    }

    // ============== Computed views (always live — never cached) ==============

    private LocalDateTime rangeStart(BudgetPlanEntity plan) { return plan.getStartDate().atStartOfDay(); }
    private LocalDateTime rangeEnd(BudgetPlanEntity plan) { return plan.getEndDate().plusDays(1).atStartOfDay(); }

    public Map<String, Object> computeSummary(BudgetPlanEntity plan, List<Map<String, Object>> categoryStatuses) {
        Long userId = plan.getUserId();
        double plannedExpense = categoryStatuses.stream().mapToDouble(c -> (Double) c.get("budgetAmount")).sum();
        Double actualIncome = transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "income", rangeStart(plan), rangeEnd(plan));
        Double actualExpense = transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "expense", rangeStart(plan), rangeEnd(plan));
        Double actualSavings = transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "savings", rangeStart(plan), rangeEnd(plan));

        double income = actualIncome == null ? 0 : actualIncome;
        double expense = actualExpense == null ? 0 : actualExpense;
        double savings = actualSavings == null ? 0 : actualSavings;

        double remaining = plan.getPlannedIncome() - plannedExpense - plan.getPlannedSavings();
        double utilization = plan.getPlannedIncome() == 0 ? 0 : ((expense + savings) / plan.getPlannedIncome()) * 100;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("plannedIncome", plan.getPlannedIncome());
        summary.put("plannedExpense", plannedExpense);
        summary.put("plannedSavings", plan.getPlannedSavings());
        summary.put("actualIncome", income);
        summary.put("actualExpense", expense);
        summary.put("actualSavings", savings);
        summary.put("remaining", remaining);
        summary.put("utilizationPercent", utilization);
        summary.put("activeBudgetsCount", categoryStatuses.size());
        return summary;
    }

    public List<Map<String, Object>> computeCategoryStatuses(BudgetPlanEntity plan) {
        LocalDate today = LocalDate.now();
        boolean periodEnded = plan.getEndDate().isBefore(today);
        long totalDays = Math.max(1, plan.getStartDate().until(plan.getEndDate()).getDays() + 1);
        long elapsedDays = Math.max(0, Math.min(totalDays, plan.getStartDate().until(today.isBefore(plan.getStartDate()) ? plan.getStartDate() : today).getDays()));
        double elapsedFraction = Math.min(1.0, (double) elapsedDays / totalDays);

        return budgetRepository.findByBudgetPlanId(plan.getId()).stream().map(b -> {
            CategoryEntity category = categoryRepository.findById(b.getCategoryId()).orElse(null);
            Double actualSum = transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(
                    plan.getUserId(), "expense", b.getCategoryId(), rangeStart(plan), rangeEnd(plan));
            double actual = actualSum == null ? 0 : actualSum;
            double remaining = b.getBudgetAmount() - actual;
            double percentUsed = b.getBudgetAmount() == 0 ? 0 : (actual / b.getBudgetAmount()) * 100;

            String status;
            if (periodEnded) status = "COMPLETED";
            else if (percentUsed > 100) status = "OVER_BUDGET";
            else if (percentUsed >= 80) status = "NEAR_LIMIT";
            else status = "ON_TRACK";

            String suggestion = null;
            String categoryName = category != null ? category.getName() : "This category";
            if (percentUsed > 100) {
                suggestion = categoryName + " is over budget by " + String.format(Locale.ROOT, "%.0f", Math.abs(remaining)) + ".";
            } else if (percentUsed >= 80) {
                suggestion = "You've already spent " + String.format(Locale.ROOT, "%.0f", percentUsed) + "% of your " + categoryName + " budget. Consider reducing spending here.";
            } else if (!periodEnded && elapsedFraction > 0.5 && percentUsed < elapsedFraction * 100 * 0.6) {
                suggestion = categoryName + " spending is lower than planned. You still have " + String.format(Locale.ROOT, "%.0f", remaining) + " remaining.";
            }

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", b.getId());
            map.put("categoryId", b.getCategoryId());
            map.put("categoryName", category != null ? category.getName() : "Unknown");
            map.put("categoryColor", category != null ? category.getColor() : "#6c757d");
            map.put("categoryIcon", category != null ? category.getIcon() : "tag");
            map.put("budgetAmount", b.getBudgetAmount());
            map.put("actualAmount", actual);
            map.put("remainingAmount", remaining);
            map.put("percentUsed", percentUsed);
            map.put("status", status);
            map.put("suggestion", suggestion);
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> computeSavingsStatuses(BudgetPlanEntity plan) {
        return savingsBudgetRepository.findByBudgetPlanId(plan.getId()).stream().map(s -> {
            CategoryEntity category = categoryRepository.findById(s.getCategoryId()).orElse(null);
            Double contributedSum = transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(
                    plan.getUserId(), "savings", s.getCategoryId(), rangeStart(plan), rangeEnd(plan));
            double contributed = contributedSum == null ? 0 : contributedSum;
            double initial = s.getInitialAmount() == null ? 0 : s.getInitialAmount();
            double current = initial + contributed;
            double percent = s.getTargetAmount() == 0 ? 0 : (current / s.getTargetAmount()) * 100;
            String status = current >= s.getTargetAmount() ? "GOAL_ACHIEVED" : "IN_PROGRESS";

            AccountEntity storageAccount = s.getStorageAccountId() != null ? accountRepository.findById(s.getStorageAccountId()).orElse(null) : null;
            AccountEntity sourceAccount = s.getSourceAccountId() != null ? accountRepository.findById(s.getSourceAccountId()).orElse(null) : null;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("categoryId", s.getCategoryId());
            map.put("categoryName", category != null ? category.getName() : "Unknown");
            map.put("categoryColor", category != null ? category.getColor() : "#6c757d");
            map.put("targetAmount", s.getTargetAmount());
            map.put("initialAmount", initial);
            map.put("contributedAmount", contributed);
            map.put("currentAmount", current);
            map.put("percentUsed", percent);
            map.put("status", status);
            map.put("storageAccountId", s.getStorageAccountId());
            map.put("storageAccountName", storageAccount != null ? storageAccount.getAccountNickname() : null);
            map.put("sourceAccountId", s.getSourceAccountId());
            map.put("sourceAccountName", sourceAccount != null ? sourceAccount.getAccountNickname() : null);
            map.put("sourceDescription", s.getSourceDescription());
            return map;
        }).collect(Collectors.toList());
    }

    /** Weighted 0-100 heuristic: category discipline (40) + savings achievement (30) + income target met (20) + no-overspend bonus (10). */
    public int computeBudgetScore(BudgetPlanEntity plan, List<Map<String, Object>> categoryStatuses,
                                   List<Map<String, Object>> savingsStatuses, Map<String, Object> summary) {
        // Use the underlying percentage rather than the display "status" — a COMPLETED (past) budget
        // still shows status=COMPLETED even if it went over, so status alone can't tell over/under apart.
        double categoryScore;
        boolean anyOverBudget = categoryStatuses.stream().anyMatch(c -> (Double) c.get("percentUsed") > 100);
        if (categoryStatuses.isEmpty()) {
            categoryScore = 40;
        } else {
            long notOver = categoryStatuses.stream().filter(c -> (Double) c.get("percentUsed") <= 100).count();
            categoryScore = ((double) notOver / categoryStatuses.size()) * 40;
        }

        double savingsScore;
        double targetSum = savingsStatuses.stream().mapToDouble(s -> (Double) s.get("targetAmount")).sum();
        if (targetSum == 0) {
            savingsScore = 30;
        } else {
            double achievedSum = savingsStatuses.stream()
                    .mapToDouble(s -> Math.min((Double) s.get("currentAmount"), (Double) s.get("targetAmount"))).sum();
            savingsScore = Math.min(1.0, achievedSum / targetSum) * 30;
        }

        double plannedIncome = (Double) summary.get("plannedIncome");
        double actualIncome = (Double) summary.get("actualIncome");
        double incomeScore = plannedIncome == 0 ? 20 : Math.min(1.0, actualIncome / plannedIncome) * 20;

        double bonus = anyOverBudget ? 0 : 10;

        int score = (int) Math.round(categoryScore + savingsScore + incomeScore + bonus);
        return Math.max(0, Math.min(100, score));
    }

    // ============== Alerts ==============

    public void checkAndNotifyThresholds(BudgetPlanEntity plan) {
        Long userId = plan.getUserId();
        for (Map<String, Object> c : computeCategoryStatuses(plan)) {
            String status = (String) c.get("status");
            Long budgetId = (Long) c.get("id");
            String categoryName = (String) c.get("categoryName");
            if ("OVER_BUDGET".equals(status)) {
                double over = Math.abs((Double) c.get("remainingAmount"));
                notificationService.createIfNotRecent(userId, "BUDGET_EXCEEDED",
                        categoryName + " Budget Exceeded",
                        categoryName + " is over budget by " + String.format(Locale.ROOT, "%.0f", over) + ".",
                        "BUDGET_CATEGORY", budgetId);
            } else if ("NEAR_LIMIT".equals(status)) {
                notificationService.createIfNotRecent(userId, "BUDGET_THRESHOLD",
                        categoryName + " Budget: " + String.format(Locale.ROOT, "%.0f", (Double) c.get("percentUsed")) + "% Used",
                        "You've used " + String.format(Locale.ROOT, "%.0f", (Double) c.get("percentUsed")) + "% of your " + categoryName + " budget.",
                        "BUDGET_CATEGORY", budgetId);
            }
        }
        for (Map<String, Object> s : computeSavingsStatuses(plan)) {
            if ("GOAL_ACHIEVED".equals(s.get("status"))) {
                Long savingsId = (Long) s.get("id");
                String categoryName = (String) s.get("categoryName");
                notificationService.createIfNotRecent(userId, "SAVINGS_GOAL_ACHIEVED",
                        categoryName + " Goal Achieved",
                        "You've reached your " + categoryName + " savings goal!",
                        "SAVINGS_BUDGET", savingsId);
            }
        }
    }
}
