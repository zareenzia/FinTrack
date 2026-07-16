package org.example.finzin.ai;

import org.example.finzin.entity.AiSettingsEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private AiSettingsService aiSettingsService;
    @Mock private InsightService insightService;
    @Mock private RecommendationService recommendationService;
    @Mock private FinancialHealthService financialHealthService;
    @Mock private TransactionRepository transactionRepository;

    private DashboardSummaryService service;
    private AiSettingsEntity settings;

    @BeforeEach
    void setUp() {
        service = new DashboardSummaryService(aiSettingsService, insightService, recommendationService, financialHealthService, transactionRepository);

        settings = new AiSettingsEntity();
        settings.setEnableDashboardSummary(true);
        settings.setEnableProactiveInsights(true);
        settings.setEnableBudgetCoaching(false);
        settings.setEnableSavingsCoaching(false);
        when(aiSettingsService.getOrDefault(USER_ID)).thenReturn(settings);
    }

    @Test
    void disabledDashboardSummarySkipsAllComputation() {
        settings.setEnableDashboardSummary(false);

        DashboardSummaryService.DashboardSummary summary = service.summarize(USER_ID);

        assertFalse(summary.enabled());
        assertNull(summary.todaysInsight());
        assertEquals(0, summary.healthScore());
    }

    @Test
    void disabledCoachingTogglesOmitTheirSections() {
        stubHealthAndInsightsAndNoTransactions();

        DashboardSummaryService.DashboardSummary summary = service.summarize(USER_ID);

        assertTrue(summary.enabled());
        assertNull(summary.budgetStatus(), "Budget coaching disabled -> section should be null");
        assertNull(summary.savingsProgress(), "Savings coaching disabled -> section should be null");
        assertNotNull(summary.todaysInsight(), "Proactive insights enabled -> should be populated");
    }

    @Test
    void biggestExpenseIsHighestAmountExpenseThisMonth() {
        stubHealthAndInsightsAndNoTransactions();

        YearMonth month = YearMonth.now();
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        CategoryEntity dining = new CategoryEntity();
        dining.setName("Dining");

        TransactionEntity small = new TransactionEntity();
        small.setTransactionType("expense");
        small.setAmount(50.0);
        small.setDescription("Snack");
        small.setDate(start.plusDays(1));

        TransactionEntity big = new TransactionEntity();
        big.setTransactionType("expense");
        big.setAmount(500.0);
        big.setDescription("Rent");
        big.setDate(start.plusDays(2));
        big.setCategory(dining);

        when(transactionRepository.findByUserIdAndDateRange(USER_ID, start, end)).thenReturn(List.of(small, big));

        DashboardSummaryService.DashboardSummary summary = service.summarize(USER_ID);

        assertNotNull(summary.biggestExpense());
        assertEquals(500.0, summary.biggestExpense().get("amount"));
        assertEquals("Rent", summary.biggestExpense().get("description"));
    }

    private void stubHealthAndInsightsAndNoTransactions() {
        FinancialHealthService.FinancialHealth health = new FinancialHealthService.FinancialHealth(
                20.0, 60.0, 90.0, 90.0, null, null, null, "note", 75, Map.of());
        when(financialHealthService.calculate(USER_ID)).thenReturn(health);

        InsightService.Insight insight = new InsightService.Insight("Dining spending increased 18% this month",
                "desc", "HIGH", "Dining", null, "TRANSACTIONS_MOM", 0.85);
        when(insightService.generateInsights(USER_ID)).thenReturn(List.of(insight));

        RecommendationService.Recommendation rec = new RecommendationService.Recommendation(
                "Reduce Dining spending", "desc", "HIGH", "Dining", "evidence");
        when(recommendationService.generateRecommendations(USER_ID)).thenReturn(List.of(rec));

        YearMonth month = YearMonth.now();
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        when(transactionRepository.findByUserIdAndDateRange(USER_ID, start, end)).thenReturn(List.of());
    }
}
