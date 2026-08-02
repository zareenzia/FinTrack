package org.example.finzin.purchaseplanner;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.LoanEntity;
import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.entity.SubscriptionEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.LoanRepository;
import org.example.finzin.repository.RecurringTransactionRepository;
import org.example.finzin.repository.SubscriptionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching AccountBalanceServiceTest/AIServiceTest's convention) for the
 * pure-calculation affordability + decision-matrix engine. No Spring context; every collaborator is
 * mocked so the arithmetic in PurchaseAffordabilityService itself is what's under test.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseAffordabilityServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private AccountRepository accountRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private BudgetPlanService budgetPlanService;

    private PurchaseAffordabilityService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseAffordabilityService(accountRepository, loanRepository, subscriptionRepository,
                recurringTransactionRepository, budgetPlanService);
    }

    private AccountEntity account(String type, double balance) {
        AccountEntity a = new AccountEntity();
        a.setAccountType(type);
        a.setCurrentBalance(balance);
        return a;
    }

    private LoanEntity loan(String frequency, Double emi) {
        LoanEntity l = new LoanEntity();
        l.setPaymentFrequency(frequency);
        l.setEmiAmount(emi);
        return l;
    }

    private SubscriptionEntity subscription(String billingCycle, double cost) {
        SubscriptionEntity s = new SubscriptionEntity();
        s.setBillingCycle(billingCycle);
        s.setCost(cost);
        return s;
    }

    private RecurringTransactionEntity recurring(String type, String frequency, double amount, Integer interval) {
        RecurringTransactionEntity r = new RecurringTransactionEntity();
        r.setTransactionType(type);
        r.setFrequency(frequency);
        r.setAmount(amount);
        r.setIntervalValue(interval);
        return r;
    }

    private PurchaseItemEntity item(Long id, double price, String needLevel, String priority, String targetMonth, Long linkedGoalId) {
        PurchaseItemEntity i = new PurchaseItemEntity();
        i.setId(id);
        i.setUserId(USER_ID);
        i.setEstimatedPrice(price);
        i.setNeedLevel(needLevel);
        i.setPriority(priority);
        i.setTargetMonth(targetMonth);
        i.setLinkedSavingsGoalId(linkedGoalId);
        return i;
    }

    // ============== buildContext ==============

    @Test
    void buildContextSumsNonCreditCardActiveAccountBalancesOnly() {
        when(accountRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(
                account("BANK", 1000.0), account("CREDIT_CARD", 5000.0), account("CASH", 200.0)));
        when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(null);
        when(loanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(subscriptionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(recurringTransactionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

        PurchaseAffordabilityService.AffordabilityContext ctx = service.buildContext(USER_ID, List.of());

        assertEquals(1200.0, ctx.availableBalance(), 0.001, "CREDIT_CARD balance must be excluded from available cash");
    }

    @Test
    void buildContextZeroesBudgetAndEmergencyFieldsWhenNoCurrentPlan() {
        when(accountRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(null);
        when(loanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(subscriptionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(recurringTransactionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

        PurchaseAffordabilityService.AffordabilityContext ctx = service.buildContext(USER_ID, List.of());

        assertEquals(0.0, ctx.remainingMonthlyBudget());
        assertEquals(0.0, ctx.emergencyFundReserve());
    }

    @Test
    void buildContextComputesRemainingBudgetAndEmergencyReserveFromCurrentPlanCaseInsensitively() {
        BudgetPlanEntity plan = new BudgetPlanEntity();
        when(accountRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(plan);
        when(budgetPlanService.computeCategoryStatuses(plan)).thenReturn(List.of(
                Map.of("remainingAmount", 300.0), Map.of("remainingAmount", 200.0)));
        when(budgetPlanService.computeSavingsStatuses(plan)).thenReturn(List.of(
                Map.of("categoryName", "Emergency Fund", "currentAmount", 1500.0),
                Map.of("categoryName", "Vacation", "currentAmount", 800.0)));
        when(loanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(subscriptionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(recurringTransactionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

        PurchaseAffordabilityService.AffordabilityContext ctx = service.buildContext(USER_ID, List.of());

        assertEquals(500.0, ctx.remainingMonthlyBudget(), 0.001, "remaining budget sums every category's remainingAmount");
        assertEquals(1500.0, ctx.emergencyFundReserve(), 0.001, "only the savings goal whose name contains 'emergency' (case-insensitively) counts");
    }

    @Test
    void buildContextComputesMonthlyFixedCommitmentsAcrossLoansSubscriptionsAndSavingsRecurringTransactions() {
        // Loans: WEEKLY and YEARLY frequency conversions.
        when(loanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(
                loan("WEEKLY", 100.0),   // 100 * 52 / 12 = 433.333...
                loan("YEARLY", 1200.0))); // 1200 / 12 = 100.0

        // Subscriptions: YEARLY divided by 12, MONTHLY as-is.
        when(subscriptionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(
                subscription("YEARLY", 1200.0), // 100.0
                subscription("MONTHLY", 20.0))); // 20.0

        // Recurring transactions: only type "savings" (case-insensitive) count, every frequency
        // branch of monthlyEquivalent() plus the interval divisor and the null-interval default.
        when(recurringTransactionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(
                recurring("savings", "MONTHLY", 1000.0, 1),   // 1000.0
                recurring("savings", "WEEKLY", 100.0, 1),     // 433.0
                recurring("expense", "MONTHLY", 5000.0, 1),   // excluded: not "savings"
                recurring("SAVINGS", "QUARTERLY", 300.0, 1),  // 100.0, case-insensitive type match
                recurring("savings", "YEARLY", 1200.0, 1),    // 100.0
                recurring("savings", "DAILY", 60.0, 2),       // 60*30/2 = 900.0
                recurring("savings", "BOGUS", 500.0, 1),      // unknown frequency -> 0.0
                recurring("savings", "MONTHLY", 200.0, null))); // null interval defaults to 1 -> 200.0

        when(accountRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(null);

        PurchaseAffordabilityService.AffordabilityContext ctx = service.buildContext(USER_ID, List.of());

        double expectedLoans = 433.333333 + 100.0;
        double expectedSubs = 100.0 + 20.0;
        double expectedRecurring = 1000.0 + 433.0 + 100.0 + 100.0 + 900.0 + 0.0 + 200.0;
        assertEquals(expectedLoans + expectedSubs + expectedRecurring, ctx.monthlyFixedCommitments(), 0.05);
    }

    // ============== computeAffordability ==============

    @Test
    void computeAffordabilityCanBuyNowWhenNetAvailableCoversPrice() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(1000, 0, 500, 100, List.of());
        PurchaseItemEntity item = item(1L, 800.0, "MUST_HAVE", "HIGH", null, null);

        var result = service.computeAffordability(item, ctx);

        assertEquals("CAN_BUY_NOW", result.status());
        assertEquals("✅ Can Buy Now", result.statusLabel());
        assertEquals(0, result.monthsRemaining());
        assertEquals(1000.0, result.netAvailableNow(), 0.001);
        assertEquals(400.0, result.monthlySavingsCapacity(), 0.001);
    }

    @Test
    void computeAffordabilityNotAffordableWhenMonthlySavingsCapacityIsZero() {
        // remainingMonthlyBudget (100) < monthlyFixedCommitments (150) -> capacity clamps to 0.
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(100, 0, 100, 150, List.of());
        PurchaseItemEntity item = item(1L, 1000.0, "MUST_HAVE", "HIGH", null, null);

        var result = service.computeAffordability(item, ctx);

        assertEquals("NOT_AFFORDABLE", result.status());
        assertNull(result.monthsRemaining());
    }

    @Test
    void computeAffordabilityWaitReturnsCeilingMonthsWithPluralLabel() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 600, 100, List.of());
        PurchaseItemEntity item = item(1L, 1500.0, "MUST_HAVE", "HIGH", null, null);

        var result = service.computeAffordability(item, ctx);

        assertEquals("WAIT", result.status());
        assertEquals(3, result.monthsRemaining(), "ceil(1500/500) = 3");
        assertEquals("⚠ Wait 3 Months", result.statusLabel());
    }

    @Test
    void computeAffordabilityWaitUsesSingularMonthLabelWhenExactlyOneMonthRemains() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 600, 100, List.of());
        PurchaseItemEntity item = item(1L, 500.0, "MUST_HAVE", "HIGH", null, null);

        var result = service.computeAffordability(item, ctx);

        assertEquals(1, result.monthsRemaining());
        assertEquals("⚠ Wait 1 Month", result.statusLabel(), "singular 'Month', no trailing s");
    }

    @Test
    void computeAffordabilityNotAffordableWhenWaitWouldExceedMaxWaitMonths() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 600, 500, List.of());
        // capacity = 100/month; shortfall 2500 -> ceil = 25 months, over the 24-month cap.
        PurchaseItemEntity item = item(1L, 2500.0, "MUST_HAVE", "HIGH", null, null);

        var result = service.computeAffordability(item, ctx);

        assertEquals("NOT_AFFORDABLE", result.status());
        assertNull(result.monthsRemaining());
    }

    @Test
    void computeAffordabilityDeductsOtherActiveItemsReservedForTheSameTargetMonth() {
        PurchaseItemEntity item = item(1L, 500.0, "MUST_HAVE", "HIGH", "2026-08", null);
        PurchaseItemEntity sameMonthOther = item(2L, 300.0, "SHOULD_HAVE", "MEDIUM", "2026-08", null);
        PurchaseItemEntity differentMonthOther = item(3L, 1_000_000.0, "SHOULD_HAVE", "MEDIUM", "2026-09", null);
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(1000, 0, 500, 100,
                List.of(item, sameMonthOther, differentMonthOther));

        var result = service.computeAffordability(item, ctx);

        assertEquals(700.0, result.netAvailableNow(), 0.001, "1000 available minus the 300 reserved by the same-month item");
        assertEquals("CAN_BUY_NOW", result.status());
    }

    @Test
    void computeAffordabilityAddsLinkedSavingsGoalCurrentAmount() {
        PurchaseItemEntity item = item(1L, 500.0, "MUST_HAVE", "HIGH", null, 9L);
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());
        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 9L)).thenReturn(Optional.of(Map.of("currentAmount", 300.0)));

        var result = service.computeAffordability(item, ctx);

        assertEquals(300.0, result.netAvailableNow(), 0.001);
    }

    @Test
    void computeAffordabilityNetAvailableNeverGoesNegativeWhenEmergencyReserveExceedsBalance() {
        PurchaseItemEntity item = item(1L, 10.0, "MUST_HAVE", "HIGH", null, null);
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(100, 500, 0, 0, List.of());

        var result = service.computeAffordability(item, ctx);

        assertEquals(0.0, result.netAvailableNow(), 0.001);
    }

    @Test
    void computeAffordabilityZeroPriceIsAlwaysCanBuyNow() {
        PurchaseItemEntity item = item(1L, 0.0, "MUST_HAVE", "HIGH", null, null);
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());

        var result = service.computeAffordability(item, ctx);

        assertEquals("CAN_BUY_NOW", result.status(), "a zero-cost item can never have a positive shortfall");
    }

    @Test
    void computeAffordabilityNegativePriceIsAlwaysCanBuyNow() {
        // Edge case: validate() blocks negative estimatedPrice at creation time, but this pure
        // calculation method itself has no guard, so it must degrade gracefully rather than throw.
        PurchaseItemEntity item = item(1L, -50.0, "MUST_HAVE", "HIGH", null, null);
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());

        var result = service.computeAffordability(item, ctx);

        assertEquals("CAN_BUY_NOW", result.status());
    }

    // ============== computeDecisionMatrix ==============

    @Test
    void computeDecisionMatrixMapsNeedLevelToScore() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());

        assertEquals(5.0, service.computeDecisionMatrix(item(1L, 100.0, "MUST_HAVE", "LOW", null, null), ctx).needScore());
        assertEquals(3.0, service.computeDecisionMatrix(item(2L, 100.0, "SHOULD_HAVE", "LOW", null, null), ctx).needScore());
        assertEquals(1.0, service.computeDecisionMatrix(item(3L, 100.0, "NICE_TO_HAVE", "LOW", null, null), ctx).needScore());
        assertEquals(1.0, service.computeDecisionMatrix(item(4L, 100.0, null, "LOW", null, null), ctx).needScore(), "null needLevel falls back to the default (lowest) score");
    }

    @Test
    void computeDecisionMatrixUrgencyDecreasesWithMonthsUntilTargetAndClampsAtZero() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());

        PurchaseItemEntity dueNow = item(1L, 100.0, "MUST_HAVE", "CRITICAL", YearMonth.now().toString(), null);
        assertEquals(5.0, service.computeDecisionMatrix(dueNow, ctx).urgencyScore(), 0.001, "0 months out: no reduction from the CRITICAL base of 5");

        PurchaseItemEntity farOut = item(2L, 100.0, "MUST_HAVE", "CRITICAL", YearMonth.now().plusMonths(30).toString(), null);
        assertEquals(0.0, service.computeDecisionMatrix(farOut, ctx).urgencyScore(), 0.001, "5 - 30*0.25 is negative and must clamp to 0");
    }

    @Test
    void computeDecisionMatrixTreatsMalformedOrBlankTargetMonthAsNoTarget() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());

        PurchaseItemEntity malformed = item(1L, 100.0, "MUST_HAVE", "HIGH", "not-a-month", null);
        assertEquals(4.0, service.computeDecisionMatrix(malformed, ctx).urgencyScore(), 0.001, "malformed targetMonth is swallowed, urgency stays at the HIGH base of 4");

        PurchaseItemEntity blank = item(2L, 100.0, "MUST_HAVE", "HIGH", "", null);
        assertEquals(4.0, service.computeDecisionMatrix(blank, ctx).urgencyScore(), 0.001);
    }

    @Test
    void computeDecisionMatrixBudgetScoreFollowsRatioThresholdLadder() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 1000, 0, List.of());

        assertEquals(5.0, service.computeDecisionMatrix(item(1L, 200.0, null, null, null, null), ctx).budgetScore(), "ratio 0.2 <= 0.25");
        assertEquals(4.0, service.computeDecisionMatrix(item(2L, 400.0, null, null, null, null), ctx).budgetScore(), "ratio 0.4 <= 0.50");
        assertEquals(3.0, service.computeDecisionMatrix(item(3L, 600.0, null, null, null, null), ctx).budgetScore(), "ratio 0.6 <= 0.75");
        assertEquals(2.0, service.computeDecisionMatrix(item(4L, 900.0, null, null, null, null), ctx).budgetScore(), "ratio 0.9 <= 1.00");
        assertEquals(1.0, service.computeDecisionMatrix(item(5L, 1500.0, null, null, null, null), ctx).budgetScore(), "ratio 1.5 <= 2.00");
        assertEquals(0.0, service.computeDecisionMatrix(item(6L, 3000.0, null, null, null, null), ctx).budgetScore(), "ratio 3.0 > 2.00");
    }

    @Test
    void computeDecisionMatrixBudgetScoreIsZeroWhenNoRemainingBudget() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());
        PurchaseItemEntity item = item(1L, 1.0, null, null, null, null);

        assertEquals(0.0, service.computeDecisionMatrix(item, ctx).budgetScore(), "remainingMonthlyBudget <= 0 forces the ratio to MAX_VALUE");
    }

    @Test
    void computeDecisionMatrixSavingsScoreUsesLinkedGoalCoverageWhenGoalIsLinked() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of());
        PurchaseItemEntity item = item(1L, 1000.0, null, null, null, 7L);

        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 7L)).thenReturn(Optional.of(Map.of("currentAmount", 1000.0)));
        assertEquals(5.0, service.computeDecisionMatrix(item, ctx).savingsScore(), "100% coverage");

        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 7L)).thenReturn(Optional.of(Map.of("currentAmount", 750.0)));
        assertEquals(4.0, service.computeDecisionMatrix(item, ctx).savingsScore(), "75% coverage");

        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 7L)).thenReturn(Optional.of(Map.of("currentAmount", 500.0)));
        assertEquals(3.0, service.computeDecisionMatrix(item, ctx).savingsScore(), "50% coverage");

        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 7L)).thenReturn(Optional.of(Map.of("currentAmount", 250.0)));
        assertEquals(2.0, service.computeDecisionMatrix(item, ctx).savingsScore(), "25% coverage");

        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 7L)).thenReturn(Optional.of(Map.of("currentAmount", 100.0)));
        assertEquals(1.0, service.computeDecisionMatrix(item, ctx).savingsScore(), "10% coverage (>0)");

        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 7L)).thenReturn(Optional.empty());
        assertEquals(0.0, service.computeDecisionMatrix(item, ctx).savingsScore(), "no savings status found -> 0 coverage");
    }

    @Test
    void computeDecisionMatrixSavingsScoreUsesAvailableBalanceMinusEmergencyReserveWhenNoLinkedGoal() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(1000, 200, 0, 0, List.of());
        PurchaseItemEntity fullyCovered = item(1L, 800.0, null, null, null, null); // (1000-200)/800 = 100%
        PurchaseItemEntity halfCovered = item(2L, 1600.0, null, null, null, null); // 800/1600 = 50%

        assertEquals(5.0, service.computeDecisionMatrix(fullyCovered, ctx).savingsScore());
        assertEquals(3.0, service.computeDecisionMatrix(halfCovered, ctx).savingsScore());
    }

    @Test
    void computeDecisionMatrixSavingsScoreIsZeroWhenPriceIsZeroOrNegative() {
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(1000, 0, 0, 0, List.of());

        assertEquals(0.0, service.computeDecisionMatrix(item(1L, 0.0, null, null, null, null), ctx).savingsScore());
        assertEquals(0.0, service.computeDecisionMatrix(item(2L, -100.0, null, null, null, null), ctx).savingsScore());
    }

    @Test
    void computeDecisionMatrixOverallReadinessIsTheWeightedAverageOfAllFourScores() {
        var perfectCtx = new PurchaseAffordabilityService.AffordabilityContext(1000, 0, 1000, 0, List.of());
        // MUST_HAVE (need=5), due now at CRITICAL (urgency=5), price ratio 0.2 (budget=5), fully covered (savings=5).
        PurchaseItemEntity allFive = item(1L, 200.0, "MUST_HAVE", "CRITICAL", YearMonth.now().toString(), null);

        var result = service.computeDecisionMatrix(allFive, perfectCtx);

        assertEquals(100.0, result.overallReadinessPercent(), 0.01,
                "(5*.15 + 5*.15 + 5*.35 + 5*.35) / 5 * 100 must equal 100 when every sub-score is maxed");
    }

    @Test
    void computeDecisionMatrixOverallReadinessWithMixedScoresMatchesTheDocumentedWeights() {
        // needScore=1 (NICE_TO_HAVE), urgencyScore=1 (LOW priority, no target), budgetScore=5 (ratio 0.2),
        // savingsScore=0 (no balance, no linked goal).
        var ctx = new PurchaseAffordabilityService.AffordabilityContext(0, 0, 1000, 0, List.of());
        PurchaseItemEntity item = item(1L, 200.0, "NICE_TO_HAVE", "LOW", null, null);

        var result = service.computeDecisionMatrix(item, ctx);

        // (1*.15 + 1*.15 + 5*.35 + 0*.35) / 5 * 100 = 2.05 / 5 * 100 = 41.0
        assertEquals(41.0, result.overallReadinessPercent(), 0.01);
    }
}
