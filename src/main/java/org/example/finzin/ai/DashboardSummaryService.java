package org.example.finzin.ai;

import org.example.finzin.entity.AiSettingsEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
    private final TtlCache<Long, DashboardSummary> cache = new TtlCache<>(CACHE_TTL);

    public DashboardSummaryService(AiSettingsService aiSettingsService, InsightService insightService,
                                    RecommendationService recommendationService, FinancialHealthService financialHealthService,
                                    TransactionRepository transactionRepository) {
        this.aiSettingsService = aiSettingsService;
        this.insightService = insightService;
        this.recommendationService = recommendationService;
        this.financialHealthService = financialHealthService;
        this.transactionRepository = transactionRepository;
    }

    public record DashboardSummary(boolean enabled, String todaysInsight, Map<String, Object> biggestExpense,
                                    Map<String, Object> budgetStatus, Map<String, Object> savingsProgress,
                                    String aiRecommendation, int healthScore) {}

    /** Cached per user for {@link #CACHE_TTL} — the dashboard card re-fetches this on every page load. */
    public DashboardSummary summarize(Long userId) {
        return cache.getOrCompute(userId, () -> summarizeInternal(userId));
    }

    private DashboardSummary summarizeInternal(Long userId) {
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
            RecommendationService.BudgetCoachAdvice advice = recommendationService.getBudgetCoachAdvice(userId);
            budgetStatus = new LinkedHashMap<>();
            budgetStatus.put("hasBudget", advice.hasBudget());
            budgetStatus.put("planName", advice.planName());
            budgetStatus.put("utilizationPercent", advice.overallUtilizationPercent());
        }

        Map<String, Object> savingsProgress = null;
        if (Boolean.TRUE.equals(settings.getEnableSavingsCoaching())) {
            RecommendationService.SavingsCoachAdvice advice = recommendationService.getSavingsCoachAdvice(userId);
            savingsProgress = new LinkedHashMap<>();
            savingsProgress.put("totalSavingsContributed", advice.totalSavingsContributed());
            savingsProgress.put("emergencyFundProgressPercent", advice.emergencyFundProgressPercent());
        }

        Map<String, Object> biggestExpense = findBiggestExpense(userId);

        return new DashboardSummary(true, todaysInsight, biggestExpense, budgetStatus, savingsProgress,
                aiRecommendation, health.overallHealthScore());
    }

    private Map<String, Object> findBiggestExpense(Long userId) {
        YearMonth month = YearMonth.now();
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
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
