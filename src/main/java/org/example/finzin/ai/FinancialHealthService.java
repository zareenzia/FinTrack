package org.example.finzin.ai;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.NetWorthSnapshotEntity;
import org.example.finzin.repository.NetWorthSnapshotRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.FinancialSummaryService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the user's financial health from existing calculation services — never re-derives a
 * number that {@link FinancialSummaryService} or {@link BudgetPlanService} already owns. The only
 * new state this service introduces is a monthly net-worth snapshot (needed for growth metrics,
 * since neither service tracks history), upserted as a side effect of each call so growth becomes
 * available automatically once a second calendar month has been observed.
 */
@Service
public class FinancialHealthService {

    private static final int TRAILING_MONTHS = 6;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final FinancialSummaryService financialSummaryService;
    private final BudgetPlanService budgetPlanService;
    private final TransactionRepository transactionRepository;
    private final NetWorthSnapshotRepository netWorthSnapshotRepository;
    private final TtlCache<Long, FinancialHealth> cache = new TtlCache<>(CACHE_TTL);

    public FinancialHealthService(FinancialSummaryService financialSummaryService, BudgetPlanService budgetPlanService,
                                   TransactionRepository transactionRepository, NetWorthSnapshotRepository netWorthSnapshotRepository) {
        this.financialSummaryService = financialSummaryService;
        this.budgetPlanService = budgetPlanService;
        this.transactionRepository = transactionRepository;
        this.netWorthSnapshotRepository = netWorthSnapshotRepository;
    }

    public record FinancialHealth(
            double monthlySavingsRatePercent,
            double expenseRatioPercent,
            double incomeStabilityScore,
            double cashFlowStabilityScore,
            Double budgetUtilizationPercent,
            Double assetGrowthPercent,
            Double netWorthGrowthPercent,
            String growthNote,
            int overallHealthScore,
            Map<String, Double> scoreBreakdown
    ) {}

    /** Cached per user for {@link #CACHE_TTL} — repeat dashboard loads / chat turns don't re-run the 6-month stability loop each time. */
    public FinancialHealth calculate(Long userId) {
        return cache.getOrCompute(userId, () -> calculateInternal(userId));
    }

    private FinancialHealth calculateInternal(Long userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        double monthlyIncome = nz(transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "income", monthStart, monthEnd));
        double monthlyExpense = nz(transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "expense", monthStart, monthEnd));
        double monthlySavings = nz(transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "savings", monthStart, monthEnd));

        double monthlySavingsRatePercent = monthlyIncome == 0 ? 0 : (monthlySavings / monthlyIncome) * 100;
        double expenseRatioPercent = monthlyIncome == 0 ? 0 : (monthlyExpense / monthlyIncome) * 100;

        List<Double> trailingIncome = trailingMonthlySums(userId, "income");
        List<Double> trailingExpense = trailingMonthlySums(userId, "expense");
        List<Double> trailingCashFlow = new ArrayList<>();
        for (int i = 0; i < trailingIncome.size(); i++) {
            trailingCashFlow.add(trailingIncome.get(i) - trailingExpense.get(i));
        }
        double incomeStabilityScore = stabilityScore(trailingIncome);
        double cashFlowStabilityScore = stabilityScore(trailingCashFlow);

        Double budgetUtilizationPercent = null;
        Integer budgetScore = null;
        BudgetPlanEntity currentPlan = budgetPlanService.getCurrentPlan(userId);
        if (currentPlan != null) {
            List<Map<String, Object>> categoryStatuses = budgetPlanService.computeCategoryStatuses(currentPlan);
            List<Map<String, Object>> savingsStatuses = budgetPlanService.computeSavingsStatuses(currentPlan);
            Map<String, Object> summary = budgetPlanService.computeSummary(currentPlan, categoryStatuses);
            budgetUtilizationPercent = (Double) summary.get("utilizationPercent");
            budgetScore = budgetPlanService.computeBudgetScore(currentPlan, categoryStatuses, savingsStatuses, summary);
        }

        double netWorth = financialSummaryService.getNetWorth(userId);
        double totalAssets = financialSummaryService.getTotalAssets(userId);
        double balance = financialSummaryService.getBalance(userId);
        double totalSavingsContributed = financialSummaryService.getTotalSavings(userId);

        NetWorthSnapshotEntity priorSnapshot = netWorthSnapshotRepository
                .findTopByUserIdAndSnapshotMonthLessThanOrderBySnapshotMonthDesc(userId, currentMonth.toString())
                .orElse(null);
        upsertCurrentSnapshot(userId, currentMonth.toString(), netWorth, totalAssets, balance, totalSavingsContributed);

        Double assetGrowthPercent = null;
        Double netWorthGrowthPercent = null;
        String growthNote = "Not enough history yet — asset/net worth growth becomes available starting next month.";
        if (priorSnapshot != null) {
            growthNote = null;
            assetGrowthPercent = priorSnapshot.getTotalAssets() == 0 ? null
                    : ((totalAssets - priorSnapshot.getTotalAssets()) / priorSnapshot.getTotalAssets()) * 100;
            netWorthGrowthPercent = priorSnapshot.getNetWorth() == 0 ? null
                    : ((netWorth - priorSnapshot.getNetWorth()) / priorSnapshot.getNetWorth()) * 100;
        }

        double avgMonthlyExpense = trailingExpense.isEmpty() ? 0
                : trailingExpense.stream().mapToDouble(Double::doubleValue).sum() / trailingExpense.size();

        Map<String, Double> breakdown = new LinkedHashMap<>();
        double savingsComponent = clamp(monthlySavingsRatePercent, 0, 100) * 0.25;
        double budgetComponent = (budgetScore != null ? budgetScore : 70) * 0.25; // 70 = neutral baseline when no active plan exists
        double stabilityComponent = ((incomeStabilityScore + cashFlowStabilityScore) / 2.0) * 0.20;
        double cashReserveMonths = avgMonthlyExpense == 0 ? (balance > 0 ? 3 : 0) : balance / avgMonthlyExpense;
        double cashReserveComponent = clamp((cashReserveMonths / 3.0) * 100, 0, 100) * 0.20; // 3 months' runway = full marks
        double netWorthTrendComponent = (netWorthGrowthPercent != null ? clamp(50 + netWorthGrowthPercent * 5, 0, 100) : 50) * 0.10;

        breakdown.put("savingsRate", round2(savingsComponent));
        breakdown.put("budgetAdherence", round2(budgetComponent));
        breakdown.put("stability", round2(stabilityComponent));
        breakdown.put("cashReserve", round2(cashReserveComponent));
        breakdown.put("netWorthTrend", round2(netWorthTrendComponent));

        int overallHealthScore = (int) Math.round(savingsComponent + budgetComponent + stabilityComponent + cashReserveComponent + netWorthTrendComponent);
        overallHealthScore = (int) clamp(overallHealthScore, 0, 100);

        return new FinancialHealth(monthlySavingsRatePercent, expenseRatioPercent, incomeStabilityScore, cashFlowStabilityScore,
                budgetUtilizationPercent, assetGrowthPercent, netWorthGrowthPercent, growthNote, overallHealthScore, breakdown);
    }

    private void upsertCurrentSnapshot(Long userId, String month, double netWorth, double totalAssets, double balance, double totalSavings) {
        NetWorthSnapshotEntity snapshot = netWorthSnapshotRepository.findByUserIdAndSnapshotMonth(userId, month)
                .orElseGet(NetWorthSnapshotEntity::new);
        snapshot.setUserId(userId);
        snapshot.setSnapshotMonth(month);
        snapshot.setNetWorth(netWorth);
        snapshot.setTotalAssets(totalAssets);
        snapshot.setBalance(balance);
        snapshot.setTotalSavingsContributed(totalSavings);
        netWorthSnapshotRepository.save(snapshot);
    }

    /** Oldest-to-newest monthly sums for the trailing {@link #TRAILING_MONTHS} months, including the current (partial) month. */
    private List<Double> trailingMonthlySums(Long userId, String transactionType) {
        List<Double> sums = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(TRAILING_MONTHS - 1L);
        for (int i = 0; i < TRAILING_MONTHS; i++) {
            LocalDateTime start = cursor.atDay(1).atStartOfDay();
            LocalDateTime end = cursor.plusMonths(1).atDay(1).atStartOfDay();
            sums.add(nz(transactionRepository.sumByUserIdAndTypeAndDateRange(userId, transactionType, start, end)));
            cursor = cursor.plusMonths(1);
        }
        return sums;
    }

    /**
     * 0-100, higher = more stable. Based on the coefficient of variation (stddev / mean) of the
     * trailing monthly values: a CV of 0 (identical every month) scores 100, a CV of 1+ (as volatile
     * as the mean itself) scores 0. Returns 0 when there's no usable signal (empty or zero-mean data).
     */
    private double stabilityScore(List<Double> values) {
        if (values.isEmpty()) return 0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (mean == 0) return 0;
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stddev = Math.sqrt(variance);
        double cv = stddev / Math.abs(mean);
        return clamp(100 * (1 - cv), 0, 100);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }
}
