package org.example.finzin.service;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.AssetRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.gold.GoldAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialSummaryServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private TransactionRepository transactionRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private GoldAssetService goldAssetService;
    @Mock private AccountRepository accountRepository;

    private FinancialSummaryService service;

    @BeforeEach
    void setUp() {
        service = new FinancialSummaryService(transactionRepository, assetRepository, goldAssetService, accountRepository);
        // Defaults for tests that don't care about assets/credit-card debt (getTotalSavings/getBalance).
        lenient().when(accountRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
        lenient().when(assetRepository.sumValuesByUserId(USER_ID)).thenReturn(0.0);
        lenient().when(goldAssetService.getTotalGoldValueForUser(USER_ID)).thenReturn(0.0);
    }

    private AccountEntity creditCard(double outstandingBalance) {
        AccountEntity a = new AccountEntity();
        a.setId(1L);
        a.setUserId(USER_ID);
        a.setAccountType("CREDIT_CARD");
        a.setCurrentBalance(outstandingBalance);
        return a;
    }

    @Test
    void getTotalSavingsReturnsGrossDepositSumWhenNoFromSavingsWithdrawals() {
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "savings")).thenReturn(2000.0);
        when(transactionRepository.sumByUserIdAndTransactionTypeAndFromSavingsTrue(USER_ID, "expense")).thenReturn(null);

        assertEquals(2000.0, service.getTotalSavings(USER_ID), 0.001);
    }

    @Test
    void getTotalSavingsNetsOutFromSavingsWithdrawals() {
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "savings")).thenReturn(5000.0);
        when(transactionRepository.sumByUserIdAndTransactionTypeAndFromSavingsTrue(USER_ID, "expense")).thenReturn(1200.0);

        assertEquals(3800.0, service.getTotalSavings(USER_ID), 0.001);
    }

    @Test
    void getBalanceIsIdenticalWhetherOrNotAFromSavingsExpenseExists() {
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "income")).thenReturn(10000.0);

        // Scenario 1: no from-savings expense. totalExpense=3000 (gross), savings deposits=2000, withdrawals=0
        // -> net savings = 2000.
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "expense")).thenReturn(3000.0);
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "savings")).thenReturn(2000.0);
        when(transactionRepository.sumByUserIdAndTransactionTypeAndFromSavingsTrue(USER_ID, "expense")).thenReturn(0.0);

        double balanceWithoutFromSavingsExpense = service.getBalance(USER_ID);

        // Scenario 2: a $500 expense funded from savings. totalExpense=3500 (gross, includes the $500),
        // savings deposits=2000 (unchanged), withdrawals=500 -> net savings = 1500.
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "expense")).thenReturn(3500.0);
        when(transactionRepository.sumByUserIdAndTransactionTypeAndFromSavingsTrue(USER_ID, "expense")).thenReturn(500.0);

        double balanceWithFromSavingsExpense = service.getBalance(USER_ID);

        assertEquals(5000.0, balanceWithoutFromSavingsExpense, 0.001);
        assertEquals(balanceWithoutFromSavingsExpense, balanceWithFromSavingsExpense, 0.001,
                "Spending from savings must not change liquid balance");
    }

    @Test
    void getNetWorthDropsByExactlyTheSpentAmountWhenAnExpenseIsFundedFromSavings() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(creditCard(1000.0)));
        when(assetRepository.sumValuesByUserId(USER_ID)).thenReturn(2000.0);
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "income")).thenReturn(10000.0);

        // Before: no from-savings expense. totalExpense=3000 (gross), savings deposits=2000, withdrawals=0.
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "expense")).thenReturn(3000.0);
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "savings")).thenReturn(2000.0);
        when(transactionRepository.sumByUserIdAndTransactionTypeAndFromSavingsTrue(USER_ID, "expense")).thenReturn(0.0);

        double netWorthBefore = service.getNetWorth(USER_ID);

        // After: a $500 expense funded from savings is added. Gross expense goes up by $500 and that same
        // $500 also shows up as a fromSavings withdrawal; deposits are unchanged.
        when(transactionRepository.sumByUserIdAndTransactionType(USER_ID, "expense")).thenReturn(3500.0);
        when(transactionRepository.sumByUserIdAndTransactionTypeAndFromSavingsTrue(USER_ID, "expense")).thenReturn(500.0);

        double netWorthAfter = service.getNetWorth(USER_ID);

        assertEquals(8000.0, netWorthBefore, 0.001);
        assertEquals(500.0, netWorthBefore - netWorthAfter, 0.001,
                "Net worth should drop by exactly the amount spent from savings");
    }
}
