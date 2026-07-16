package org.example.finzin.ai;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.FinancialSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private FinancialHealthService financialHealthService;
    @Mock private InsightService insightService;
    @Mock private BudgetPlanService budgetPlanService;
    @Mock private FinancialSummaryService financialSummaryService;
    @Mock private TransactionRepository transactionRepository;

    private RecommendationService service;
    private BudgetPlanEntity plan;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(financialHealthService, insightService, budgetPlanService, financialSummaryService, transactionRepository);

        plan = new BudgetPlanEntity();
        plan.setName("Test Plan");
        plan.setPeriod("2026-07");
        plan.setEndDate(LocalDate.now().plusDays(10));
        lenient().when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(plan);

        Map<String, Object> overBudgetCategory = new LinkedHashMap<>();
        overBudgetCategory.put("categoryName", "Dining");
        overBudgetCategory.put("percentUsed", 110.0);
        overBudgetCategory.put("remainingAmount", -50.0);
        overBudgetCategory.put("status", "OVER_BUDGET");
        lenient().when(budgetPlanService.computeCategoryStatuses(plan)).thenReturn(List.of(overBudgetCategory));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("utilizationPercent", 95.0);
        lenient().when(budgetPlanService.computeSummary(eq(plan), any())).thenReturn(summary);
        lenient().when(budgetPlanService.computeSavingsStatuses(plan)).thenReturn(List.of());

        lenient().when(financialSummaryService.getTotalSavings(USER_ID)).thenReturn(1000.0);
        lenient().when(financialSummaryService.getBalance(USER_ID)).thenReturn(500.0);
        lenient().when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("savings"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(300.0);
        lenient().when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("expense"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(600.0);

        lenient().when(insightService.generateInsights(USER_ID)).thenReturn(List.of());

        FinancialHealthService.FinancialHealth lowSavingsHealth = new FinancialHealthService.FinancialHealth(
                5.0, 80.0, 90.0, 90.0, 95.0, null, null, "note", 60, Map.of());
        lenient().when(financialHealthService.calculate(USER_ID)).thenReturn(lowSavingsHealth);
    }

    @Test
    void lowSavingsRateTriggersIncreaseSavingsRecommendation() {
        List<RecommendationService.Recommendation> recs = service.generateRecommendations(USER_ID);

        assertTrue(recs.stream().anyMatch(r -> r.title().equals("Increase your savings rate") && "HIGH".equals(r.priority())),
                "Expected a HIGH priority savings-rate recommendation, got: " + recs);
    }

    @Test
    void overBudgetCategoryTriggersReduceSpendingRecommendation() {
        List<RecommendationService.Recommendation> recs = service.generateRecommendations(USER_ID);

        assertTrue(recs.stream().anyMatch(r -> r.title().equals("Reduce Dining spending") && r.evidence().contains("110")),
                "Expected an evidence-cited Dining recommendation, got: " + recs);
    }

    @Test
    void budgetCoachAdviceComputesSafeDailySpendFromRemainingAndDaysLeft() {
        RecommendationService.BudgetCoachAdvice advice = service.getBudgetCoachAdvice(USER_ID);

        assertTrue(advice.hasBudget());
        assertEquals(1, advice.categories().size());
        RecommendationService.CategoryAdvice dining = advice.categories().get(0);
        assertEquals("OVER_BUDGET", dining.status());
        // remaining is negative (over budget) -> no safe daily spend suggested
        assertNull(dining.safeDailySpend());
    }

    @Test
    void savingsCoachAdviceComputesEmergencyFundFromTrailingExpenseAverage() {
        RecommendationService.SavingsCoachAdvice advice = service.getSavingsCoachAdvice(USER_ID);

        // target = 3 * avg monthly expense (600) = 1800; balance 500 -> ~27.8% progress
        assertEquals(1800.0, advice.emergencyFundTarget(), 0.001);
        assertEquals(500.0 / 1800.0 * 100, advice.emergencyFundProgressPercent(), 0.001);
    }
}
