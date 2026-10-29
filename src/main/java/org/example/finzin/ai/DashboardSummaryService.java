package org.example.finzin.ai;

import org.example.finzin.entity.AiSettingsEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin composition of the other Phase 2C services into the single payload the Dashboard's AI
 * Summary card renders. Each section is gated by the matching {@link AiSettingsEntity} toggle so a
 * user who disabled a feature never sees data from it here, even though the underlying service
 * still runs for chat/tool-call purposes.
 */
@Service
public class DashboardSummaryService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final AiSettingsService aiSettingsService;
    private final InsightService insightService;
    private final RecommendationService recommendationService;
    private final FinancialHealthService financialHealthService;
    private final TransactionRepository transactionRepository;
    private final BudgetPlanService budgetPlanService;
    /** Keyed by userId + the requested month ('YYYY-MM') so switching the dashboard's month
     *  filter never serves another month's cached biggest-expense/budget-status payload. */
    private final TtlCache<String, DashboardSummary> cache = new TtlCache<>(CACHE_TTL);

    public DashboardSummaryService(AiSettingsService aiSettingsService, InsightService insightService,
                                    RecommendationService recommendationService, FinancialHealthService financialHealthService,
                                    TransactionRepository transactionRepository, BudgetPlanService budgetPlanService) {
        this.aiSettingsService = aiSettingsService;
        this.insightService = insightService;
        this.recommendationService = recommendationService;
        this.financialHealthService = financialHealthService;
        this.transactionRepository = transactionRepository;
        this.budgetPlanService = budgetPlanService;
    }

    public record DashboardSummary(boolean enabled, String todaysInsight, Map<String, Object> biggestExpense,
                                    Map<String, Object> budgetStatus, Map<String, Object> savingsProgress,
                                    String aiRecommendation, int healthScore) {}

    /** Cached per user+month for {@link #CACHE_TTL} — the dashboard card re-fetches this on every
     *  page load / month change. {@code month} is an optional 'YYYY-MM' string; null/blank means
     *  the real current month. */
    public DashboardSummary summarize(Long userId, String month) {
        YearMonth targetMonth = parseMonth(month);
        String cacheKey = userId + ":" + targetMonth;
        return cache.getOrCompute(cacheKey, () -> summarizeInternal(userId, targetMonth));
    }

    private YearMonth parseMonth(String month) {
        if (month != null && !month.isBlank() && !"ALL".equalsIgnoreCase(month.trim())) {
            try {
                return YearMonth.parse(month.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (Exception ignored) {
                // fall through to current month
            }
        }
        return YearMonth.now();
    }

    private DashboardSummary summarizeInternal(Long userId, YearMonth targetMonth) {
        AiSettingsEntity settings = aiSettingsService.getOrDefault(userId);
        if (!Boolean.TRUE.equals(settings.getEnableDashboardSummary())) {
            return new DashboardSummary(false, null, null, null, null, null, 0);
        }

        FinancialHealthService.FinancialHealth health = financialHealthService.calculate(userId);

        String todaysInsight = null;
        String aiRecommendation = null;
        if (Boolean.TRUE.equals(settings.getEnableProactiveInsights())) {
            todaysInsight = insightService.generateInsights(userId).stream()
                    .max(Comparator.comparingInt(i -> "HIGH".equals(i.priority()) ? 2 : "MEDIUM".equals(i.priority()) ? 1 : 0))
                    .map(InsightService.Insight::title)
                    .orElse(null);
            aiRecommendation = recommendationService.generateRecommendations(userId).stream()
                    .max(Comparator.comparingInt(r -> "HIGH".equals(r.priority()) ? 2 : "MEDIUM".equals(r.priority()) ? 1 : 0))
                    .map(RecommendationService.Recommendation::title)
                    .orElse(null);
        }

        Map<String, Object> budgetStatus = null;
        if (Boolean.TRUE.equals(settings.getEnableBudgetCoaching())) {
            budgetStatus = buildBudgetStatus(userId, targetMonth);
        }

        Map<String, Object> savingsProgress = null;
        if (Boolean.TRUE.equals(settings.getEnableSavingsCoaching())) {
            RecommendationService.SavingsCoachAdvice advice = recommendationService.getSavingsCoachAdvice(userId);
            savingsProgress = new LinkedHashMap<>();
            savingsProgress.put("totalSavingsContributed", advice.totalSavingsContributed());
            savingsProgress.put("emergencyFundProgressPercent", advice.emergencyFundProgressPercent());
        }

        Map<String, Object> biggestExpense = findBiggestExpense(userId, targetMonth);

        return new DashboardSummary(true, todaysInsight, biggestExpense, budgetStatus, savingsProgress,
                aiRecommendation, health.overallHealthScore());
    }

    /** Budget utilization for the requested month specifically (mirrors {@link MonthlyReportService}'s
     *  pattern) rather than {@link RecommendationService#getBudgetCoachAdvice}, which only ever looks
     *  at the currently-active plan — that would show the wrong month's percentage, or none at all,
     *  once the dashboard's month filter moves away from the current month. */
    private Map<String, Object> buildBudgetStatus(Long userId, YearMonth targetMonth) {
        BudgetPlanEntity plan = budgetPlanService.listForUser(userId, targetMonth.toString(), "ACTIVE", null, null).stream()
                .findFirst()
                .orElse(null);
        Map<String, Object> budgetStatus = new LinkedHashMap<>();
        if (plan == null) {
            budgetStatus.put("hasBudget", false);
            return budgetStatus;
        }
        List<Map<String, Object>> categoryStatuses = budgetPlanService.computeCategoryStatuses(plan);
        Map<String, Object> summary = budgetPlanService.computeSummary(plan, categoryStatuses);
        budgetStatus.put("hasBudget", true);
        budgetStatus.put("planName", plan.getName());
        budgetStatus.put("utilizationPercent", summary.get("utilizationPercent"));
        return budgetStatus;
    }

    private Map<String, Object> findBiggestExpense(Long userId, YearMonth targetMonth) {
        LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        List<TransactionEntity> transactions = transactionRepository.findByUserIdAndDateRange(userId, start, end);

        TransactionEntity biggest = transactions.stream()
                .filter(t -> "expense".equals(t.getTransactionType()))
                .max(Comparator.comparingDouble(TransactionEntity::getAmount))
                .orElse(null);
        if (biggest == null) return null;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("description", biggest.getDescription());
        map.put("amount", biggest.getAmount());
        map.put("category", biggest.getCategory() != null ? biggest.getCategory().getName() : null);
        map.put("date", biggest.getDate() != null ? biggest.getDate().toLocalDate().toString() : null);
        return map;
    }
}
