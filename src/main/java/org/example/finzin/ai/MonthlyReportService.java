package org.example.finzin.ai;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assembles the structured monthly report by composing the other Phase 2C services plus the
 * existing transaction/budget repositories for the specific month requested — no PDF rendering
 * (deferred), JSON/record data only, also exposed as an AI tool so the assistant can narrate it.
 */
@Service
public class MonthlyReportService {

    private static final int TOP_PURCHASES_LIMIT = 5;

    private final FinancialContextService financialContextService;
    private final FinancialHealthService financialHealthService;
    private final RecommendationService recommendationService;
    private final BudgetPlanService budgetPlanService;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public MonthlyReportService(FinancialContextService financialContextService, FinancialHealthService financialHealthService,
                                 RecommendationService recommendationService, BudgetPlanService budgetPlanService,
                                 TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.financialContextService = financialContextService;
        this.financialHealthService = financialHealthService;
        this.recommendationService = recommendationService;
        this.budgetPlanService = budgetPlanService;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    public record CategoryBreakdown(String categoryName, double amount) {}

    public record TopPurchase(String date, String description, double amount, String category) {}

    public record MonthlyReport(
            String month,
            Map<String, Object> incomeExpenseSummary,
            Double assetGrowthPercent,
            Map<String, Object> budgetPerformance,
            List<CategoryBreakdown> categoryAnalysis,
            List<TopPurchase> topPurchases,
            FinancialHealthService.FinancialHealth financialHealth,
            List<RecommendationService.Recommendation> recommendations,
            List<String> goalsForNextMonth
    ) {}

    public MonthlyReport generate(Long userId, String monthParam) {
        YearMonth month = parseMonth(monthParam);
        String monthStr = month.toString();
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        Map<String, Object> incomeExpenseSummary = financialContextService.getMonthlyExpenses(userId, monthStr);
        FinancialHealthService.FinancialHealth health = financialHealthService.calculate(userId);
        List<RecommendationService.Recommendation> recommendations = recommendationService.generateRecommendations(userId);

        BudgetPlanEntity plan = budgetPlanService.listForUser(userId, monthStr, "ACTIVE", null, null).stream()
                .findFirst()
                .orElse(null);
        Map<String, Object> budgetPerformance;
        List<Map<String, Object>> categoryStatuses = List.of();
        List<Map<String, Object>> savingsStatuses = List.of();
        if (plan != null) {
            categoryStatuses = budgetPlanService.computeCategoryStatuses(plan);
            savingsStatuses = budgetPlanService.computeSavingsStatuses(plan);
            budgetPerformance = new LinkedHashMap<>(budgetPlanService.computeSummary(plan, categoryStatuses));
            budgetPerformance.put("hasBudget", true);
            budgetPerformance.put("planName", plan.getName());
        } else {
            budgetPerformance = Map.of("hasBudget", false);
        }

        List<CategoryBreakdown> categoryAnalysis = new ArrayList<>();
        for (CategoryEntity category : categoryRepository.findByUserId(userId)) {
            Double sum = transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(userId, "expense", category.getId(), start, end);
            double amount = sum == null ? 0 : sum;
            if (amount > 0) categoryAnalysis.add(new CategoryBreakdown(category.getName(), amount));
        }
        categoryAnalysis.sort(Comparator.comparingDouble(CategoryBreakdown::amount).reversed());

        List<TopPurchase> topPurchases = transactionRepository.findByUserIdAndDateRange(userId, start, end).stream()
                .filter(t -> "expense".equals(t.getTransactionType()))
                .sorted(Comparator.comparingDouble(TransactionEntity::getAmount).reversed())
                .limit(TOP_PURCHASES_LIMIT)
                .map(t -> new TopPurchase(
                        t.getDate() != null ? t.getDate().toLocalDate().toString() : null,
                        t.getDescription(),
                        t.getAmount(),
                        t.getCategory() != null ? t.getCategory().getName() : null))
                .toList();

        List<String> goalsForNextMonth = buildGoalsForNextMonth(categoryStatuses, savingsStatuses, recommendations);

        return new MonthlyReport(monthStr, incomeExpenseSummary, health.assetGrowthPercent(), budgetPerformance,
                categoryAnalysis, topPurchases, health, recommendations, goalsForNextMonth);
    }

    private List<String> buildGoalsForNextMonth(List<Map<String, Object>> categoryStatuses,
                                                 List<Map<String, Object>> savingsStatuses,
                                                 List<RecommendationService.Recommendation> recommendations) {
        List<String> goals = new ArrayList<>();
        for (Map<String, Object> c : categoryStatuses) {
            if ("OVER_BUDGET".equals(c.get("status"))) {
                goals.add("Reduce " + c.get("categoryName") + " spending next month.");
            }
        }
        for (Map<String, Object> s : savingsStatuses) {
            if ("IN_PROGRESS".equals(s.get("status"))) {
                double percent = (Double) s.get("percentUsed");
                goals.add("Continue contributing toward " + s.get("categoryName") + " (" + String.format(Locale.ROOT, "%.0f", percent) + "% complete).");
            }
        }
        if (goals.isEmpty()) {
            recommendations.stream().filter(r -> "HIGH".equals(r.priority())).limit(3)
                    .forEach(r -> goals.add(r.title() + "."));
        }
        return goals;
    }

    private YearMonth parseMonth(String month) {
        if (month != null && !month.isBlank()) {
            try {
                return YearMonth.parse(month.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (Exception ignored) {
                // fall through to current month
            }
        }
        return YearMonth.now();
    }
}
