package org.example.finzin.ai;

import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private FinancialContextService financialContextService;
    @Mock private FinancialHealthService financialHealthService;
    @Mock private RecommendationService recommendationService;
    @Mock private BudgetPlanService budgetPlanService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;

    private MonthlyReportService service;
    private LocalDateTime monthStart;
    private LocalDateTime monthEnd;

    @BeforeEach
    void setUp() {
        service = new MonthlyReportService(financialContextService, financialHealthService, recommendationService,
                budgetPlanService, transactionRepository, categoryRepository);

        YearMonth current = YearMonth.now();
        monthStart = current.atDay(1).atStartOfDay();
        monthEnd = current.plusMonths(1).atDay(1).atStartOfDay();

        when(financialContextService.getMonthlyExpenses(eq(USER_ID), anyString()))
                .thenReturn(Map.of("month", current.toString(), "income", 2000.0, "expense", 1200.0, "savings", 400.0, "net", 400.0));

        FinancialHealthService.FinancialHealth health = new FinancialHealthService.FinancialHealth(
                33.0, 60.0, 80.0, 80.0, null, 5.0, 5.0, null, 70, Map.of());
        when(financialHealthService.calculate(USER_ID)).thenReturn(health);

        RecommendationService.Recommendation topRec = new RecommendationService.Recommendation(
                "Reduce Dining spending", "desc", "HIGH", "Dining", "evidence");
        when(recommendationService.generateRecommendations(USER_ID)).thenReturn(List.of(topRec));

        when(budgetPlanService.listForUser(eq(USER_ID), anyString(), eq("ACTIVE"), isNull(), isNull())).thenReturn(List.of());

        CategoryEntity dining = new CategoryEntity();
        dining.setId(1L);
        dining.setName("Dining");
        CategoryEntity transport = new CategoryEntity();
        transport.setId(2L);
        transport.setName("Transport");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(dining, transport));

        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(1L), eq(monthStart), eq(monthEnd)))
                .thenReturn(200.0);
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(2L), eq(monthStart), eq(monthEnd)))
                .thenReturn(50.0);

        TransactionEntity bigPurchase = new TransactionEntity();
        bigPurchase.setTransactionType("expense");
        bigPurchase.setAmount(150.0);
        bigPurchase.setDescription("Dinner out");
        bigPurchase.setDate(monthStart.plusDays(2));
        bigPurchase.setCategory(dining);

        TransactionEntity biggerPurchase = new TransactionEntity();
        biggerPurchase.setTransactionType("expense");
        biggerPurchase.setAmount(300.0);
        biggerPurchase.setDescription("Cab rides");
        biggerPurchase.setDate(monthStart.plusDays(5));
        biggerPurchase.setCategory(transport);

        TransactionEntity incomeTx = new TransactionEntity();
        incomeTx.setTransactionType("income");
        incomeTx.setAmount(2000.0);
        incomeTx.setDescription("Salary");
        incomeTx.setDate(monthStart.plusDays(1));

        when(transactionRepository.findByUserIdAndDateRange(USER_ID, monthStart, monthEnd))
                .thenReturn(List.of(bigPurchase, biggerPurchase, incomeTx));
    }

    @Test
    void categoryAnalysisIsSortedDescendingByAmount() {
        MonthlyReportService.MonthlyReport report = service.generate(USER_ID, null);

        assertEquals(2, report.categoryAnalysis().size());
        assertEquals("Dining", report.categoryAnalysis().get(0).categoryName());
        assertEquals(200.0, report.categoryAnalysis().get(0).amount(), 0.001);
        assertEquals("Transport", report.categoryAnalysis().get(1).categoryName());
    }

    @Test
    void topPurchasesExcludeIncomeAndAreSortedByAmountDescending() {
        MonthlyReportService.MonthlyReport report = service.generate(USER_ID, null);

        assertEquals(2, report.topPurchases().size());
        assertEquals("Cab rides", report.topPurchases().get(0).description());
        assertEquals(300.0, report.topPurchases().get(0).amount(), 0.001);
        assertEquals("Dinner out", report.topPurchases().get(1).description());
    }

    @Test
    void noBudgetPlanFallsBackToTopRecommendationsForNextMonthGoals() {
        MonthlyReportService.MonthlyReport report = service.generate(USER_ID, null);

        assertEquals(Boolean.FALSE, report.budgetPerformance().get("hasBudget"));
        assertTrue(report.goalsForNextMonth().stream().anyMatch(g -> g.contains("Reduce Dining spending")));
    }
}
