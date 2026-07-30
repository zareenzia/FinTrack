package org.example.finzin.ai;

import org.example.finzin.repository.NetWorthSnapshotRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.FinancialSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test mirroring {@code AIServiceTest}'s convention — verifies the health
 * scoring formula against fixed mocked inputs rather than a live DB, so the weighted breakdown
 * (savings/budget/stability/cash-reserve/net-worth-trend) never silently drifts.
 */
@ExtendWith(MockitoExtension.class)
class FinancialHealthServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private FinancialSummaryService financialSummaryService;
    @Mock private BudgetPlanService budgetPlanService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private NetWorthSnapshotRepository netWorthSnapshotRepository;

    private FinancialHealthService service;

    @BeforeEach
    void setUp() {
        service = new FinancialHealthService(financialSummaryService, budgetPlanService, transactionRepository, netWorthSnapshotRepository);

        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("income"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1000.0);
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("expense"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(600.0);
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("savings"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(200.0);

        when(netWorthSnapshotRepository.findByUserIdAndSnapshotMonth(eq(USER_ID), anyString())).thenReturn(Optional.empty());
        when(netWorthSnapshotRepository.findTopByUserIdAndSnapshotMonthLessThanOrderBySnapshotMonthDesc(eq(USER_ID), anyString()))
                .thenReturn(Optional.empty());

        when(financialSummaryService.getNetWorth(USER_ID)).thenReturn(5000.0);
        when(financialSummaryService.getTotalAssets(USER_ID)).thenReturn(2000.0);
        when(financialSummaryService.getBalance(USER_ID)).thenReturn(3000.0);
        when(financialSummaryService.getTotalSavings(USER_ID)).thenReturn(1000.0);
    }

    @Test
    void computesMonthlyRatesFromCurrentMonthSums() {
        FinancialHealthService.FinancialHealth health = service.calculate(USER_ID);

        assertEquals(20.0, health.monthlySavingsRatePercent(), 0.001);
        assertEquals(60.0, health.expenseRatioPercent(), 0.001);
    }

    @Test
    void constantTrailingMonthsProduceMaximumStabilityScore() {
        FinancialHealthService.FinancialHealth health = service.calculate(USER_ID);

        assertEquals(100.0, health.incomeStabilityScore(), 0.001);
        assertEquals(100.0, health.cashFlowStabilityScore(), 0.001);
    }

    @Test
    void noBudgetPlanLeavesUtilizationNullAndUsesNeutralBaselineInScore() {
        FinancialHealthService.FinancialHealth health = service.calculate(USER_ID);

        assertNull(health.budgetUtilizationPercent());
        assertEquals(17.5, health.scoreBreakdown().get("budgetAdherence"), 0.001);
    }

    @Test
    void noPriorSnapshotLeavesGrowthNullWithExplanatoryNote() {
        FinancialHealthService.FinancialHealth health = service.calculate(USER_ID);

        assertNull(health.assetGrowthPercent());
        assertNull(health.netWorthGrowthPercent());
        assertNotNull(health.growthNote());
        verify(netWorthSnapshotRepository).save(any());
    }

    @Test
    void overallHealthScoreMatchesWeightedFormula() {
        FinancialHealthService.FinancialHealth health = service.calculate(USER_ID);

        // savings 20%*0.25=5 + budget 70*0.25=17.5 + stability 100*0.20=20 + cashReserve(5mo runway, capped)*0.20=20 + netWorthTrend 50*0.10=5 = 67.5 -> 68
        assertEquals(68, health.overallHealthScore());
    }
}
