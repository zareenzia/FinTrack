package org.example.finzin.ai;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.FinancialSummaryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates evidence-cited recommendations, plus the Budget Coach and Savings Coach flows — all
 * three read from {@link FinancialHealthService}, {@link InsightService}, and {@link BudgetPlanService}
 * rather than re-deriving numbers. Every {@link Recommendation#evidence()} names the figure it's
 * based on so the AI (and the UI) never states advice without a number behind it.
 */
@Service
public class RecommendationService {

    private static final double LOW_SAVINGS_RATE_THRESHOLD = 10.0;
    private static final double EXCELLENT_SAVINGS_RATE_THRESHOLD = 20.0;
    private static final double EMERGENCY_FUND_TARGET_MONTHS = 3.0;
    private static final int TREND_MONTHS = 3;

    private final FinancialHealthService financialHealthService;
    private final InsightService insightService;
    private final BudgetPlanService budgetPlanService;
    private final FinancialSummaryService financialSummaryService;
    private final TransactionRepository transactionRepository;

    public RecommendationService(FinancialHealthService financialHealthService, InsightService insightService,
                                  BudgetPlanService budgetPlanService, FinancialSummaryService financialSummaryService,
                                  TransactionRepository transactionRepository) {
        this.financialHealthService = financialHealthService;
        this.insightService = insightService;
        this.budgetPlanService = budgetPlanService;
        this.financialSummaryService = financialSummaryService;
        this.transactionRepository = transactionRepository;
    }

    public record Recommendation(String title, String description, String priority, String category, String evidence) {}

    public record CategoryAdvice(String categoryName, double percentUsed, double remainingAmount, String status, Double safeDailySpend) {}

    public record BudgetCoachAdvice(boolean hasBudget, String planName, String period, Double overallUtilizationPercent,
                                     Integer daysRemainingInPeriod, List<CategoryAdvice> categories) {}

    public record SavingsCoachAdvice(double totalSavingsContributed, Double monthlySavingsTrendPercent,
                                      double emergencyFundTarget, double emergencyFundProgressPercent,
                                      List<Map<String, Object>> savingsGoals) {}

    public List<Recommendation> generateRecommendations(Long userId) {
        List<Recommendation> recommendations = new ArrayList<>();
        FinancialHealthService.FinancialHealth health = financialHealthService.calculate(userId);

        if (health.monthlySavingsRatePercent() < LOW_SAVINGS_RATE_THRESHOLD) {
            recommendations.add(new Recommendation(
                    "Increase your savings rate",
                    "Consider setting aside more of your income each month — even a small increase compounds over time.",
                    "HIGH", "Savings",
                    "This month's savings rate is " + fmt(health.monthlySavingsRatePercent()) + "%, below the recommended 20%."));
        } else if (health.monthlySavingsRatePercent() >= EXCELLENT_SAVINGS_RATE_THRESHOLD) {
            recommendations.add(new Recommendation(
                    "Your savings rate is excellent",
                    "Keep it up — you're saving a healthy share of your income.",
                    "LOW", "Savings",
                    "This month's savings rate is " + fmt(health.monthlySavingsRatePercent()) + "%."));
        }

        BudgetCoachAdvice budgetAdvice = getBudgetCoachAdvice(userId);
        for (CategoryAdvice c : budgetAdvice.categories()) {
            if ("OVER_BUDGET".equals(c.status())) {
                recommendations.add(new Recommendation(
                        "Reduce " + c.categoryName() + " spending",
                        "You've exceeded your " + c.categoryName() + " budget for this period — consider cutting back for the rest of the month.",
                        "HIGH", c.categoryName(),
                        c.categoryName() + " is at " + fmt(c.percentUsed()) + "% of its budget (over by " + fmt(Math.abs(c.remainingAmount())) + ")."));
            } else if ("NEAR_LIMIT".equals(c.status())) {
                recommendations.add(new Recommendation(
                        "You are close to exceeding your " + c.categoryName() + " budget",
                        "Consider lowering discretionary " + c.categoryName().toLowerCase(Locale.ROOT) + " expenses for the rest of the period.",
                        "MEDIUM", c.categoryName(),
                        c.categoryName() + " is at " + fmt(c.percentUsed()) + "% of its budget, " + fmt(c.remainingAmount()) + " remaining."));
            }
        }

        for (InsightService.Insight insight : insightService.generateInsights(userId)) {
            if ("HIGH".equals(insight.priority()) && !"Income".equals(insight.category()) && !"Savings".equals(insight.category())) {
                recommendations.add(new Recommendation(
                        "Review your " + insight.category() + " spending",
                        insight.description(),
                        "MEDIUM", insight.category(),
                        insight.title()));
            }
        }

        SavingsCoachAdvice savingsAdvice = getSavingsCoachAdvice(userId);
        if (savingsAdvice.emergencyFundProgressPercent() < 100) {
            recommendations.add(new Recommendation(
                    "Increase your emergency savings",
                    "Aim to build a reserve covering " + (int) EMERGENCY_FUND_TARGET_MONTHS + " months of expenses for unexpected costs.",
                    "MEDIUM", "Savings",
                    "Current balance covers " + fmt(savingsAdvice.emergencyFundProgressPercent()) + "% of your "
                            + fmt(savingsAdvice.emergencyFundTarget()) + " emergency fund target."));
        }

        return recommendations;
    }

    public BudgetCoachAdvice getBudgetCoachAdvice(Long userId) {
        BudgetPlanEntity plan = budgetPlanService.getCurrentPlan(userId);
        if (plan == null) {
            return new BudgetCoachAdvice(false, null, null, null, null, List.of());
        }

        List<Map<String, Object>> categoryStatuses = budgetPlanService.computeCategoryStatuses(plan);
        Map<String, Object> summary = budgetPlanService.computeSummary(plan, categoryStatuses);

        LocalDate today = LocalDate.now();
        int daysRemaining = plan.getEndDate().isBefore(today) ? 0 : (int) today.until(plan.getEndDate()).getDays() + 1;

        List<CategoryAdvice> categories = new ArrayList<>();
        for (Map<String, Object> c : categoryStatuses) {
            double remaining = (Double) c.get("remainingAmount");
            Double safeDailySpend = (daysRemaining > 0 && remaining > 0) ? remaining / daysRemaining : null;
            categories.add(new CategoryAdvice((String) c.get("categoryName"), (Double) c.get("percentUsed"),
                    remaining, (String) c.get("status"), safeDailySpend));
        }

        return new BudgetCoachAdvice(true, plan.getName(), plan.getPeriod(),
                (Double) summary.get("utilizationPercent"), daysRemaining, categories);
    }

    public SavingsCoachAdvice getSavingsCoachAdvice(Long userId) {
        double totalSavings = financialSummaryService.getTotalSavings(userId);
        double balance = financialSummaryService.getBalance(userId);

        YearMonth current = YearMonth.now();
        double currentMonthSavings = monthSum(userId, current);
        double previousMonthSavings = monthSum(userId, current.minusMonths(1));
        Double trendPercent = previousMonthSavings <= 0 ? null
                : ((currentMonthSavings - previousMonthSavings) / previousMonthSavings) * 100;

        double avgMonthlyExpense = 0;
        for (int i = 1; i <= TREND_MONTHS; i++) {
            avgMonthlyExpense += expenseSum(userId, current.minusMonths(i));
        }
        avgMonthlyExpense /= TREND_MONTHS;

        double emergencyFundTarget = avgMonthlyExpense * EMERGENCY_FUND_TARGET_MONTHS;
        double emergencyFundProgress = emergencyFundTarget <= 0 ? 100 : Math.min(100, (balance / emergencyFundTarget) * 100);

        BudgetPlanEntity plan = budgetPlanService.getCurrentPlan(userId);
        List<Map<String, Object>> savingsGoals = plan == null ? List.of() : budgetPlanService.computeSavingsStatuses(plan);

        return new SavingsCoachAdvice(totalSavings, trendPercent, emergencyFundTarget, emergencyFundProgress, savingsGoals);
    }

    private double monthSum(Long userId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        Double sum = transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "savings", start, end);
        return sum == null ? 0 : sum;
    }

    private double expenseSum(Long userId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        Double sum = transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "expense", start, end);
        return sum == null ? 0 : sum;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
