package org.example.finzin.gamification;

import org.example.finzin.ai.FinancialHealthService;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.InvestmentEntity;
import org.example.finzin.entity.LoanEntity;
import org.example.finzin.entity.StreakEntity;
import org.example.finzin.entity.UserStatCounterEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.BudgetPlanRepository;
import org.example.finzin.repository.InvestmentRepository;
import org.example.finzin.repository.LoanRepository;
import org.example.finzin.repository.UserStatCounterRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.CreditCardService;
import org.example.finzin.service.FinancialSummaryService;
import org.example.finzin.service.gold.GoldAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the metric-resolution switch — the part of the gamification engine most likely to silently
 * drift from the achievements that depend on it, since every achievement threshold is only as
 * correct as the metric value backing it.
 */
@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private UserStatCounterRepository counterRepository;
    @Mock private FinancialSummaryService financialSummaryService;
    @Mock private AccountRepository accountRepository;
    @Mock private InvestmentRepository investmentRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private GoldAssetService goldAssetService;
    @Mock private CreditCardService creditCardService;
    @Mock private StreakService streakService;
    @Mock private ChallengeService challengeService;
    @Mock private FinancialHealthService financialHealthService;
    @Mock private BudgetPlanRepository budgetPlanRepository;
    @Mock private BudgetPlanService budgetPlanService;

    private ProgressService progressService;

    @BeforeEach
    void setUp() {
        progressService = new ProgressService(counterRepository, financialSummaryService, accountRepository,
                investmentRepository, loanRepository, goldAssetService, creditCardService, streakService,
                challengeService, financialHealthService, budgetPlanRepository, budgetPlanService);
    }

    private BudgetPlanEntity monthlyPlan(Long id, LocalDate endDate) {
        BudgetPlanEntity p = new BudgetPlanEntity();
        p.setId(id);
        p.setPeriodType("MONTH");
        p.setEndDate(endDate);
        return p;
    }

    private Map<String, Object> categoryStatus(double percentUsed) {
        return Map.of("percentUsed", percentUsed);
    }

    private InvestmentEntity investment(String type, double quantity, double price) {
        InvestmentEntity i = new InvestmentEntity();
        i.setInvestmentType(type);
        i.setQuantity(quantity);
        i.setCurrentPrice(price);
        return i;
    }

    private LoanEntity loan(double principal, double remaining) {
        LoanEntity l = new LoanEntity();
        l.setPrincipalAmount(principal);
        l.setRemainingBalance(remaining);
        return l;
    }

    private AccountEntity creditCard(double balance, double limit) {
        AccountEntity a = new AccountEntity();
        a.setAccountType("CREDIT_CARD");
        a.setCurrentBalance(balance);
        a.setCreditLimit(limit);
        return a;
    }

    @Test
    void counterBackedMetricReadsThroughToTheCounterRepository() {
        UserStatCounterEntity counter = new UserStatCounterEntity();
        counter.setCounterValue(37.0);
        when(counterRepository.findByUserIdAndCounterKey(USER_ID, "transactions.count")).thenReturn(Optional.of(counter));

        assertEquals(37.0, progressService.resolveMetric(USER_ID, "transactions.count"));
    }

    @Test
    void counterBackedMetricDefaultsToZeroWhenNeverIncremented() {
        when(counterRepository.findByUserIdAndCounterKey(USER_ID, "notes.count")).thenReturn(Optional.empty());

        assertEquals(0.0, progressService.resolveMetric(USER_ID, "notes.count"));
    }

    @Test
    void incrementCounterAccumulatesOnTopOfTheExistingValue() {
        UserStatCounterEntity counter = new UserStatCounterEntity();
        counter.setCounterValue(4.0);
        when(counterRepository.findByUserIdAndCounterKey(USER_ID, "notes.count")).thenReturn(Optional.of(counter));
        when(counterRepository.save(counter)).thenReturn(counter);

        double result = progressService.incrementCounter(USER_ID, "notes.count", 1.0);

        assertEquals(5.0, result);
    }

    @Test
    void distinctInvestmentTypesCountsUniqueNonBlankTypesOnly() {
        when(investmentRepository.findByUserId(USER_ID)).thenReturn(List.of(
                investment("STOCK", 1, 1), investment("STOCK", 2, 1), investment("BOND", 1, 1), investment("", 1, 1)
        ));

        assertEquals(2.0, progressService.resolveMetric(USER_ID, "investments.distinct_types"));
    }

    @Test
    void portfolioValueSumsQuantityTimesCurrentPriceAcrossHoldings() {
        when(investmentRepository.findByUserId(USER_ID)).thenReturn(List.of(
                investment("STOCK", 10, 100), investment("BOND", 5, 40)
        ));

        assertEquals(1200.0, progressService.resolveMetric(USER_ID, "investments.portfolio_value"));
    }

    @Test
    void netWorthValueDelegatesToFinancialSummaryService() {
        when(financialSummaryService.getNetWorth(USER_ID)).thenReturn(50_000.0);

        assertEquals(50_000.0, progressService.resolveMetric(USER_ID, "networth.value"));
    }

    @Test
    void debtReducedPercentComputesAcrossAllLoansCombined() {
        when(loanRepository.findByUserId(USER_ID)).thenReturn(List.of(loan(100_000, 25_000)));

        assertEquals(75.0, progressService.resolveMetric(USER_ID, "loans.debt_reduced_percent"), 0.001);
    }

    @Test
    void debtFreeIsOneOnlyWhenEveryLoanIsFullyPaidOff() {
        when(loanRepository.findByUserId(USER_ID)).thenReturn(List.of(loan(50_000, 0.0)));
        assertEquals(1.0, progressService.resolveMetric(USER_ID, "loans.debt_free"));
    }

    @Test
    void debtFreeIsZeroWhenAnyLoanStillHasABalance() {
        when(loanRepository.findByUserId(USER_ID)).thenReturn(List.of(loan(50_000, 0.0), loan(20_000, 500.0)));
        assertEquals(0.0, progressService.resolveMetric(USER_ID, "loans.debt_free"));
    }

    @Test
    void debtFreeIsZeroWhenTheUserHasNeverHadALoan() {
        when(loanRepository.findByUserId(USER_ID)).thenReturn(List.of());
        assertEquals(0.0, progressService.resolveMetric(USER_ID, "loans.debt_free"));
    }

    @Test
    void creditCardHeadroomIsHundredMinusTheWorstUtilizationAmongCards() {
        AccountEntity card = creditCard(3000, 10000);
        when(accountRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(card));
        when(creditCardService.getStats(card)).thenReturn(new CreditCardService.CreditCardStats(7000, 30.0, 500, 10));

        assertEquals(70.0, progressService.resolveMetric(USER_ID, "creditcard.headroom_percent"), 0.001);
    }

    @Test
    void assetsTotalValueDelegatesToGoldAssetService() {
        when(goldAssetService.getTotalGoldValueForUser(USER_ID)).thenReturn(15_000.0);

        assertEquals(15_000.0, progressService.resolveMetric(USER_ID, "assets.total_value"));
    }

    @Test
    void streakDailyActiveReadsCurrentStreakFromStreakService() {
        StreakEntity streak = new StreakEntity();
        streak.setCurrentStreak(9);
        when(streakService.get(USER_ID, "DAILY_ACTIVE")).thenReturn(streak);

        assertEquals(9.0, progressService.resolveMetric(USER_ID, "streak.daily_active"));
    }

    @Test
    void streakDailyActiveIsZeroWhenNoStreakRecordedYet() {
        when(streakService.get(USER_ID, "DAILY_ACTIVE")).thenReturn(null);

        assertEquals(0.0, progressService.resolveMetric(USER_ID, "streak.daily_active"));
    }

    @Test
    void unrecognizedMetricKeyResolvesToZeroRatherThanThrowing() {
        assertEquals(0.0, progressService.resolveMetric(USER_ID, "not.a.real.metric"));
    }

    @Test
    void netWorthGrowthPercentReusesFinancialHealthServicesOwnCalculation() {
        FinancialHealthService.FinancialHealth health = new FinancialHealthService.FinancialHealth(
                0, 0, 0, 0, null, null, 18.5, null, 80, Map.of());
        when(financialHealthService.calculate(USER_ID)).thenReturn(health);

        assertEquals(18.5, progressService.resolveMetric(USER_ID, "networth.growth_percent"));
    }

    @Test
    void netWorthGrowthPercentIsZeroWhenNoPriorSnapshotExistsYet() {
        FinancialHealthService.FinancialHealth health = new FinancialHealthService.FinancialHealth(
                0, 0, 0, 0, null, null, null, null, 80, Map.of());
        when(financialHealthService.calculate(USER_ID)).thenReturn(health);

        assertEquals(0.0, progressService.resolveMetric(USER_ID, "networth.growth_percent"));
    }

    @Test
    void budgetConsecutiveMonthsCountsBackFromMostRecentClosedPlanUntilFirstViolation() {
        BudgetPlanEntity latest = monthlyPlan(3L, LocalDate.now().minusDays(1));
        BudgetPlanEntity middle = monthlyPlan(2L, LocalDate.now().minusMonths(1).minusDays(1));
        BudgetPlanEntity oldest = monthlyPlan(1L, LocalDate.now().minusMonths(2).minusDays(1));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of(oldest, middle, latest));
        lenient().when(budgetPlanService.computeCategoryStatuses(latest)).thenReturn(List.of(categoryStatus(80.0)));
        lenient().when(budgetPlanService.computeCategoryStatuses(middle)).thenReturn(List.of(categoryStatus(95.0)));
        lenient().when(budgetPlanService.computeCategoryStatuses(oldest)).thenReturn(List.of(categoryStatus(110.0)));

        assertEquals(2.0, progressService.resolveMetric(USER_ID, "budget.consecutive_months_within"));
    }

    @Test
    void budgetConsecutiveMonthsStopsAtTheFirstOverBudgetPlanGoingBackward() {
        BudgetPlanEntity latest = monthlyPlan(2L, LocalDate.now().minusDays(1));
        BudgetPlanEntity older = monthlyPlan(1L, LocalDate.now().minusMonths(1).minusDays(1));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of(older, latest));
        when(budgetPlanService.computeCategoryStatuses(latest)).thenReturn(List.of(categoryStatus(101.0)));

        assertEquals(0.0, progressService.resolveMetric(USER_ID, "budget.consecutive_months_within"));
        verify(budgetPlanService, org.mockito.Mockito.never()).computeCategoryStatuses(older);
    }

    @Test
    void budgetConsecutiveMonthsIgnoresTheStillOpenCurrentPlan() {
        BudgetPlanEntity openPlan = monthlyPlan(1L, LocalDate.now().plusDays(5));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of(openPlan));

        assertEquals(0.0, progressService.resolveMetric(USER_ID, "budget.consecutive_months_within"));
    }

    @Test
    void incrementCounterAlsoFeedsTheSameDeltaIntoChallengeProgress() {
        UserStatCounterEntity counter = new UserStatCounterEntity();
        counter.setCounterValue(0.0);
        when(counterRepository.findByUserIdAndCounterKey(USER_ID, "notes.count")).thenReturn(Optional.of(counter));
        when(counterRepository.save(any())).thenReturn(counter);

        progressService.incrementCounter(USER_ID, "notes.count", 1.0);

        verify(challengeService).recordProgress(USER_ID, "notes.count", 1.0);
    }
}
