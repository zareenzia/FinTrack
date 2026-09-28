package org.example.finzin.ai;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.FinancialSummaryService;
import org.example.finzin.service.gold.GoldAssetService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (no Spring context), matching {@code AIServiceTest}'s convention.
 * Covers the LLM-shaped mapping/aggregation logic {@link FinancialContextService} adds on top of
 * existing repositories/services: month parsing/defaulting, fuzzy category matching (never an
 * exception on a typo, always the closest/shortest match), transaction limit clamping, and the
 * account/gold-asset field mapping used verbatim in tool results shown to the model.
 */
@ExtendWith(MockitoExtension.class)
class FinancialContextServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BudgetPlanService budgetPlanService;
    @Mock private FinancialSummaryService financialSummaryService;
    @Mock private GoldAssetService goldAssetService;

    private FinancialContextService service;

    @BeforeEach
    void setUp() {
        service = new FinancialContextService(transactionRepository, accountRepository, categoryRepository,
                budgetPlanService, financialSummaryService, goldAssetService);
    }

    private CategoryEntity category(Long id, String name) {
        CategoryEntity c = new CategoryEntity();
        c.setId(id);
        c.setName(name);
        return c;
    }

    @Test
    void getAccountBalancesMapsProviderAsFallbackWhenBankNameIsAbsent() {
        AccountEntity mfs = new AccountEntity();
        mfs.setAccountNickname("bKash Wallet");
        mfs.setAccountType("MFS");
        mfs.setBankName(null);
        mfs.setProvider("bKash");
        mfs.setCurrentBalance(1500.0);
        mfs.setStatus("ACTIVE");
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(mfs));

        List<Map<String, Object>> result = service.getAccountBalances(USER_ID);

        assertEquals(1, result.size());
        assertEquals("bKash", result.get(0).get("bankOrProvider"));
        assertEquals(1500.0, result.get(0).get("currentBalance"));
    }

    @Test
    void getAccountBalancesPrefersBankNameOverProviderWhenBothArePresent() {
        AccountEntity bank = new AccountEntity();
        bank.setAccountNickname("Main Account");
        bank.setBankName("City Bank");
        bank.setProvider("irrelevant");
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(bank));

        List<Map<String, Object>> result = service.getAccountBalances(USER_ID);

        assertEquals("City Bank", result.get(0).get("bankOrProvider"));
    }

    @Test
    void getMonthlyExpensesDefaultsToTheCurrentMonthWhenBlank() {
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("income"), any(), any())).thenReturn(1000.0);
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("expense"), any(), any())).thenReturn(400.0);
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("savings"), any(), any())).thenReturn(100.0);

        Map<String, Object> result = service.getMonthlyExpenses(USER_ID, null);

        assertEquals(YearMonth.now().toString(), result.get("month"));
        assertEquals(1000.0, result.get("income"));
        assertEquals(400.0, result.get("expense"));
        assertEquals(100.0, result.get("savings"));
        assertEquals(500.0, result.get("net")); // income - expense - savings
    }

    @Test
    void getMonthlyExpensesDefaultsToTheCurrentMonthWhenUnparseable() {
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), any(), any(), any())).thenReturn(null);

        Map<String, Object> result = service.getMonthlyExpenses(USER_ID, "not-a-month");

        assertEquals(YearMonth.now().toString(), result.get("month"));
        assertEquals(0.0, result.get("income"), "null sums must be treated as zero, never NPE");
    }

    @Test
    void getMonthlyExpensesParsesAnExplicitMonth() {
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), any(), any(), any())).thenReturn(0.0);

        Map<String, Object> result = service.getMonthlyExpenses(USER_ID, "2026-03");

        assertEquals("2026-03", result.get("month"));
    }

    @Test
    void getExpenseByCategoryReturnsAnErrorMapWhenNoCategoryMatches() {
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(category(1L, "Dining")));

        Map<String, Object> result = service.getExpenseByCategory(USER_ID, "Spelunking", null);

        assertTrue(result.get("error").toString().contains("Spelunking"));
        assertFalse(result.containsKey("totalSpent"));
    }

    @Test
    void getExpenseByCategoryPrefersTheShortestFuzzyMatch() {
        // "fun" is a substring of both — the shortest/closest match must win rather than an arbitrary one.
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(category(1L, "Fun Money And More"), category(2L, "Fun")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(2L), any(), any()))
                .thenReturn(75.0);

        Map<String, Object> result = service.getExpenseByCategory(USER_ID, "fun", null);

        assertEquals("Fun", result.get("category"));
        assertEquals(75.0, result.get("totalSpent"));
    }

    @Test
    void getMonthComparisonDelegatesBothMonthsToGetMonthlyExpenses() {
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), any(), any(), any())).thenReturn(0.0);

        Map<String, Object> result = service.getMonthComparison(USER_ID, "2026-01", "2026-02");

        @SuppressWarnings("unchecked")
        Map<String, Object> monthA = (Map<String, Object>) result.get("monthA");
        @SuppressWarnings("unchecked")
        Map<String, Object> monthB = (Map<String, Object>) result.get("monthB");
        assertEquals("2026-01", monthA.get("month"));
        assertEquals("2026-02", monthB.get("month"));
    }

    @Test
    void getRecentTransactionsClampsAnOverlyLargeLimitToFifty() {
        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID)).thenReturn(manyTransactions(60));

        List<Map<String, Object>> result = service.getRecentTransactions(USER_ID, 1000);

        assertEquals(50, result.size());
    }

    @Test
    void getRecentTransactionsClampsAZeroOrNegativeLimitToOne() {
        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID)).thenReturn(manyTransactions(5));

        List<Map<String, Object>> result = service.getRecentTransactions(USER_ID, -3);

        assertEquals(1, result.size());
    }

    @Test
    void getRecentTransactionsMapsAMissingCategoryAsNullRatherThanThrowing() {
        TransactionEntity t = new TransactionEntity();
        t.setDate(LocalDateTime.now());
        t.setDescription("Cash withdrawal");
        t.setAmount(200.0);
        t.setTransactionType("expense");
        t.setCategory(null);
        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID)).thenReturn(List.of(t));

        List<Map<String, Object>> result = service.getRecentTransactions(USER_ID, 10);

        assertNull(result.get(0).get("category"));
    }

    @Test
    void getBudgetStatusReturnsHasBudgetFalseWhenNoActivePlanExists() {
        when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(null);

        Map<String, Object> result = service.getBudgetStatus(USER_ID);

        assertEquals(false, result.get("hasBudget"));
        assertFalse(result.containsKey("budgetScore"));
    }

    @Test
    void getBudgetStatusAssemblesEveryComponentWhenAPlanExists() {
        BudgetPlanEntity plan = new BudgetPlanEntity();
        plan.setName("July Plan");
        plan.setPeriod("2026-07");
        when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(plan);
        when(budgetPlanService.computeCategoryStatuses(plan)).thenReturn(List.of(Map.of("categoryName", "Food")));
        when(budgetPlanService.computeSavingsStatuses(plan)).thenReturn(List.of(Map.of("categoryName", "Emergency")));
        when(budgetPlanService.computeSummary(eq(plan), any())).thenReturn(Map.of("totalPlanned", 5000.0));
        when(budgetPlanService.computeBudgetScore(eq(plan), any(), any(), any())).thenReturn(88);

        Map<String, Object> result = service.getBudgetStatus(USER_ID);

        assertEquals(true, result.get("hasBudget"));
        assertEquals("July Plan", result.get("planName"));
        assertEquals(88, result.get("budgetScore"));
    }

    @Test
    void getNetWorthDelegatesEveryFieldToFinancialSummaryService() {
        when(financialSummaryService.getNetWorth(USER_ID)).thenReturn(50000.0);
        when(financialSummaryService.getBalance(USER_ID)).thenReturn(20000.0);
        when(financialSummaryService.getTotalSavings(USER_ID)).thenReturn(15000.0);
        when(financialSummaryService.getTotalAssets(USER_ID)).thenReturn(20000.0);
        when(financialSummaryService.getTotalCreditCardDebt(USER_ID)).thenReturn(5000.0);

        Map<String, Object> result = service.getNetWorth(USER_ID);

        assertEquals(50000.0, result.get("netWorth"));
        assertEquals(5000.0, result.get("creditCardDebt"));
    }

    @Test
    void getSavingsReturnsEmptyGoalsListWhenThereIsNoActivePlan() {
        when(financialSummaryService.getTotalSavings(USER_ID)).thenReturn(1000.0);
        when(budgetPlanService.getCurrentPlan(USER_ID)).thenReturn(null);

        Map<String, Object> result = service.getSavings(USER_ID);

        assertEquals(List.of(), result.get("activeSavingsGoals"));
    }

    @Test
    void getAssetsMapsGoldAssetFieldsAndTotals() {
        GoldAssetEntity gold = new GoldAssetEntity();
        gold.setAssetName("Wedding ring");
        gold.setGoldType("22K");
        gold.setPurity("91.6%");
        gold.setWeight(10.0);
        gold.setWeightUnit("gram");
        gold.setCurrentValue(85000.0);
        when(goldAssetService.getAssetsForUser(USER_ID)).thenReturn(List.of(gold));
        when(goldAssetService.getTotalGoldValueForUser(USER_ID)).thenReturn(85000.0);
        when(goldAssetService.getTotalGoldWeightInGrams(USER_ID)).thenReturn(10.0);

        Map<String, Object> result = service.getAssets(USER_ID);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goldAssets = (List<Map<String, Object>>) result.get("goldAssets");
        assertEquals("Wedding ring", goldAssets.get(0).get("name"));
        assertEquals(85000.0, result.get("totalGoldValue"));
    }

    private List<TransactionEntity> manyTransactions(int count) {
        List<TransactionEntity> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            TransactionEntity t = new TransactionEntity();
            t.setDate(LocalDateTime.now().minusDays(i));
            t.setDescription("txn-" + i);
            t.setAmount(10.0);
            t.setTransactionType("expense");
            list.add(t);
        }
        return list;
    }
}
