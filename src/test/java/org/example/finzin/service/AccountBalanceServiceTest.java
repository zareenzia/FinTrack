package org.example.finzin.service;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching AIServiceTest's convention) guarding the sign-inversion fix:
 * a CREDIT_CARD account's currentBalance means outstanding debt, so every scenario must move in
 * the OPPOSITE direction from a normal account for the same transaction.
 */
@ExtendWith(MockitoExtension.class)
class AccountBalanceServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CreditCardService creditCardService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AccountBalanceService service;

    @BeforeEach
    void setUp() {
        service = new AccountBalanceService(accountRepository, transactionRepository, creditCardService, eventPublisher);
    }

    private AccountEntity account(Long id, String type, double balance) {
        AccountEntity a = new AccountEntity();
        a.setId(id);
        a.setUserId(USER_ID);
        a.setAccountType(type);
        a.setCurrentBalance(balance);
        return a;
    }

    @Test
    void normalAccountExpenseDecreasesBalance() {
        AccountEntity bank = account(1L, "BANK", 1000.0);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(bank));

        service.applyBalanceChange(USER_ID, 1L, null, "expense", 100.0, false);

        assertEquals(900.0, bank.getCurrentBalance(), 0.001);
    }

    @Test
    void creditCardExpenseIncreasesOutstanding() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, 2L, null, "expense", 5000.0, false);

        assertEquals(25000.0, card.getCurrentBalance(), 0.001, "spending on a credit card must INCREASE outstanding, not decrease it");
    }

    @Test
    void creditCardIncomeDecreasesOutstandingLikeARefund() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, 2L, null, "income", 1000.0, false);

        assertEquals(19000.0, card.getCurrentBalance(), 0.001);
    }

    @Test
    void normalAccountAsTransferDestinationIncreasesBalance() {
        AccountEntity savings = account(3L, "BANK", 500.0);
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(java.util.Optional.of(savings));

        service.applyBalanceChange(USER_ID, null, 3L, "transfer", 200.0, false);

        assertEquals(700.0, savings.getCurrentBalance(), 0.001);
    }

    @Test
    void creditCardAsTransferDestinationDecreasesOutstandingAsAPayment() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, null, 2L, "transfer", 15000.0, false);

        assertEquals(5000.0, card.getCurrentBalance(), 0.001, "a payment (transfer-in) must DECREASE outstanding");
    }

    @Test
    void creditCardAsTransferSourceIncreasesOutstandingAsACashAdvance() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, 2L, null, "transfer", 3000.0, false);

        assertEquals(23000.0, card.getCurrentBalance(), 0.001);
    }

    @Test
    void reverseThenReapplyNetsToTheAmountDifference() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(card));

        // Old: 5000 expense already applied (outstanding at 20000 already reflects it). Editing to 7000:
        service.applyBalanceChange(USER_ID, 2L, null, "expense", 5000.0, true);  // reverse old
        service.applyBalanceChange(USER_ID, 2L, null, "expense", 7000.0, false); // apply new

        assertEquals(22000.0, card.getCurrentBalance(), 0.001, "net effect of editing 5000 -> 7000 must be +2000");
    }

    @Test
    void unknownAccountIsSilentlyIgnored() {
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(java.util.Optional.empty());
        // Should not throw.
        service.applyBalanceChange(USER_ID, 99L, null, "expense", 100.0, false);
    }

    @Test
    void createTransactionValidatesSavesAndAppliesBalance() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(card));
        TransactionEntity entity = new TransactionEntity(USER_ID, 5000.0, "Shopping", null, "expense",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        entity.setSourceAccountId(2L);
        entity.setId(99L); // a real save() always assigns the generated id; needed since the gamification event reads saved.getId()
        when(transactionRepository.save(entity)).thenReturn(entity);
        lenient().when(creditCardService.validate(USER_ID, 2L, null, "expense", 5000.0)).thenReturn(null);

        AccountBalanceService.TransactionSaveResult result = service.createTransaction(USER_ID, entity);

        assertNull(result.warning());
        assertEquals(25000.0, card.getCurrentBalance(), 0.001);
    }

    @Test
    void createTransactionPropagatesBlockException() {
        TransactionEntity entity = new TransactionEntity(USER_ID, 5000.0, "Shopping", null, "expense",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        entity.setSourceAccountId(2L);
        when(creditCardService.validate(USER_ID, 2L, null, "expense", 5000.0))
                .thenThrow(new CreditCardValidationException("This purchase exceeds your available credit limit."));

        org.junit.jupiter.api.Assertions.assertThrows(CreditCardValidationException.class,
                () -> service.createTransaction(USER_ID, entity));
    }

    // ============================================================================================
    // Transfer zero-sum regression suite.
    //
    // Every test above this point exercises "transfer" with exactly one of sourceAccountId/
    // destinationAccountId non-null (an external endpoint) or with a CREDIT_CARD on one side.
    // None of them cover the case reported as buggy: a transfer between two ordinary, already-
    // tracked accounts (e.g. BANK -> BANK) where BOTH ids are non-null in the same call. These
    // tests close that gap and pin the zero-sum invariant: for any transfer, the source's loss
    // must exactly equal the destination's gain, so the combined total across the two accounts
    // never changes.
    // ============================================================================================

    @Test
    void applyBalanceChangeFetchesBothAccountsWithPessimisticLockNotPlainFindById() {
        // Regression guard for the concurrent-transfer money-duplication bug: a plain findById
        // here (or in CreditCardService.validate, see CreditCardServiceTest's equivalent guards)
        // lets two concurrent requests touching the same account both read the same stale balance
        // and one silently clobber the other's write when both eventually save().
        AccountEntity scb = account(10L, "BANK", 70000.0);
        AccountEntity cityBank = account(11L, "BANK", 0.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));

        service.applyBalanceChange(USER_ID, 10L, 11L, "transfer", 20000.0, false);

        verify(accountRepository).findByIdForUpdate(10L);
        verify(accountRepository).findByIdForUpdate(11L);
        verify(accountRepository, never()).findById(10L);
        verify(accountRepository, never()).findById(11L);
    }

    @Test
    void incomeCreditsTheReceivingAccountOnly() {
        // "External -> SCB" salary: income has no destinationAccountId, only sourceAccountId
        // (the account being credited).
        AccountEntity scb = account(10L, "BANK", 0.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));

        service.applyBalanceChange(USER_ID, 10L, null, "income", 75000.0, false);

        assertEquals(75000.0, scb.getCurrentBalance(), 0.001);
    }

    @Test
    void transferBetweenTwoNormalBankAccountsDebitsSourceAndCreditsDestination() {
        // The exact scenario from the bug report: SCB -> City Bank, both real tracked BANK
        // accounts, sourceAccountId AND destinationAccountId both non-null in one call.
        AccountEntity scb = account(10L, "BANK", 70000.0);
        AccountEntity cityBank = account(11L, "BANK", 0.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));

        service.applyBalanceChange(USER_ID, 10L, 11L, "transfer", 20000.0, false);

        assertEquals(50000.0, scb.getCurrentBalance(), 0.001, "source account must be debited by the transferred amount");
        assertEquals(20000.0, cityBank.getCurrentBalance(), 0.001, "destination account must be credited by the transferred amount");
        assertEquals(70000.0, scb.getCurrentBalance() + cityBank.getCurrentBalance(), 0.001,
                "a transfer must be zero-sum across the two accounts");
    }

    @Test
    void creditCardBillPaymentDebitsThePayingBankAccount() {
        // "SCB -> Credit Card" bill payment: source is a normal account (must be debited), the
        // destination is a CREDIT_CARD (outstanding balance drops as a payment, see the
        // creditCardAsTransferDestinationDecreasesOutstandingAsAPayment test above for that half).
        AccountEntity scb = account(10L, "BANK", 70000.0);
        AccountEntity card = account(2L, "CREDIT_CARD", 5000.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, 10L, 2L, "transfer", 5000.0, false);

        assertEquals(65000.0, scb.getCurrentBalance(), 0.001, "paying a credit card bill must debit the paying bank account");
        assertEquals(0.0, card.getCurrentBalance(), 0.001, "the card's outstanding balance must drop by the payment amount");
    }

    @Test
    void transferBetweenDifferentAccountTypesPreservesTotal() {
        // "City Bank -> bKash": BANK -> MFS.
        AccountEntity cityBank = account(11L, "BANK", 7000.0);
        AccountEntity bkash = account(12L, "MFS", 0.0);
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));
        when(accountRepository.findByIdForUpdate(12L)).thenReturn(java.util.Optional.of(bkash));

        service.applyBalanceChange(USER_ID, 11L, 12L, "transfer", 3000.0, false);

        assertEquals(4000.0, cityBank.getCurrentBalance(), 0.001);
        assertEquals(3000.0, bkash.getCurrentBalance(), 0.001);
    }

    @Test
    void cashToBankTransferDebitsCashAndCreditsBank() {
        // "Cash -> Bank": CASH -> BANK.
        AccountEntity cash = account(13L, "CASH", 5000.0);
        AccountEntity scb = account(10L, "BANK", 62000.0);
        when(accountRepository.findByIdForUpdate(13L)).thenReturn(java.util.Optional.of(cash));
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));

        service.applyBalanceChange(USER_ID, 13L, 10L, "transfer", 2000.0, false);

        assertEquals(3000.0, cash.getCurrentBalance(), 0.001);
        assertEquals(64000.0, scb.getCurrentBalance(), 0.001);
    }

    @Test
    void updatingTransferAmountReversesOldThenAppliesNewOnBothAccounts() {
        // Old transfer (SCB -> City Bank, 20000) is already reflected in the starting balances
        // below. Editing the amount to 30000 must move an EXTRA 10000, not double-apply anything.
        AccountEntity scb = account(10L, "BANK", 50000.0);
        AccountEntity cityBank = account(11L, "BANK", 20000.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));

        service.applyBalanceChange(USER_ID, 10L, 11L, "transfer", 20000.0, true);  // reverse old
        service.applyBalanceChange(USER_ID, 10L, 11L, "transfer", 30000.0, false); // apply new

        assertEquals(40000.0, scb.getCurrentBalance(), 0.001, "net effect of editing 20000 -> 30000 must be an extra -10000 on the source");
        assertEquals(30000.0, cityBank.getCurrentBalance(), 0.001, "net effect must be an extra +10000 on the destination");
        assertEquals(70000.0, scb.getCurrentBalance() + cityBank.getCurrentBalance(), 0.001);
    }

    @Test
    void updatingTransferToADifferentDestinationMovesTheEffectCorrectly() {
        // Old: SCB -> City Bank 10000 (already applied). Edit: same source/amount, but the
        // destination changes to bKash instead of City Bank.
        AccountEntity scb = account(10L, "BANK", 60000.0);
        AccountEntity cityBank = account(11L, "BANK", 10000.0);
        AccountEntity bkash = account(12L, "MFS", 0.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));
        when(accountRepository.findByIdForUpdate(12L)).thenReturn(java.util.Optional.of(bkash));

        service.applyBalanceChange(USER_ID, 10L, 11L, "transfer", 10000.0, true);  // reverse old (SCB -> City)
        service.applyBalanceChange(USER_ID, 10L, 12L, "transfer", 10000.0, false); // apply new (SCB -> bKash)

        assertEquals(60000.0, scb.getCurrentBalance(), 0.001, "source unaffected since the amount didn't change, only the destination did");
        assertEquals(0.0, cityBank.getCurrentBalance(), 0.001, "old destination must lose the reversed amount");
        assertEquals(10000.0, bkash.getCurrentBalance(), 0.001, "new destination must gain the amount");
    }

    @Test
    void deletingTransferReversesBothAccountsBackToOriginal() {
        AccountEntity scb = account(10L, "BANK", 50000.0);
        AccountEntity cityBank = account(11L, "BANK", 20000.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));

        TransactionEntity entity = new TransactionEntity(USER_ID, 20000.0, "Transfer", null, "transfer",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        entity.setId(55L);
        entity.setSourceAccountId(10L);
        entity.setDestinationAccountId(11L);

        service.deleteTransaction(USER_ID, entity);

        assertEquals(70000.0, scb.getCurrentBalance(), 0.001);
        assertEquals(0.0, cityBank.getCurrentBalance(), 0.001);
        verify(transactionRepository).deleteById(55L);
    }

    @Test
    void createTransactionAppliesBankToBankTransferToBothAccounts() {
        // Same as transferBetweenTwoNormalBankAccountsDebitsSourceAndCreditsDestination but
        // driven through the public createTransaction() entry point the controller actually calls,
        // not applyBalanceChange() directly — closes the gap at the layer the bug was reported at.
        AccountEntity scb = account(10L, "BANK", 70000.0);
        AccountEntity cityBank = account(11L, "BANK", 0.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));
        TransactionEntity entity = new TransactionEntity(USER_ID, 20000.0, "Transfer to City Bank", null, "transfer",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        entity.setSourceAccountId(10L);
        entity.setDestinationAccountId(11L);
        entity.setId(100L);
        when(transactionRepository.save(entity)).thenReturn(entity);
        lenient().when(creditCardService.validate(USER_ID, 10L, 11L, "transfer", 20000.0)).thenReturn(null);

        service.createTransaction(USER_ID, entity);

        assertEquals(50000.0, scb.getCurrentBalance(), 0.001, "REGRESSION: source account must be debited on transfer creation");
        assertEquals(20000.0, cityBank.getCurrentBalance(), 0.001);
    }

    @Test
    void updateTransactionOnBankToBankTransferReversesOldAndAppliesNewCorrectly() {
        AccountEntity scb = account(10L, "BANK", 50000.0);
        AccountEntity cityBank = account(11L, "BANK", 20000.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));
        TransactionEntity entity = new TransactionEntity(USER_ID, 30000.0, "Transfer to City Bank", null, "transfer",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        entity.setId(100L);
        entity.setSourceAccountId(10L);
        entity.setDestinationAccountId(11L);
        when(transactionRepository.save(entity)).thenReturn(entity);
        lenient().when(creditCardService.validate(USER_ID, 10L, 11L, "transfer", 30000.0)).thenReturn(null);

        service.updateTransaction(USER_ID, entity, 10L, 11L, "transfer", 20000.0);

        assertEquals(40000.0, scb.getCurrentBalance(), 0.001);
        assertEquals(30000.0, cityBank.getCurrentBalance(), 0.001);
    }

    @Test
    void multipleChainedTransfersAlwaysPreserveTotalAcrossAllAccounts() {
        // SCB -> City Bank -> bKash, plus Cash -> SCB, chained. After EVERY step, the sum across
        // all four accounts must be unchanged, since transfers can only move money, never create
        // or destroy it.
        AccountEntity scb = account(10L, "BANK", 70000.0);
        AccountEntity cityBank = account(11L, "BANK", 0.0);
        AccountEntity bkash = account(12L, "MFS", 0.0);
        AccountEntity cash = account(13L, "CASH", 5000.0);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(scb));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(cityBank));
        when(accountRepository.findByIdForUpdate(12L)).thenReturn(java.util.Optional.of(bkash));
        when(accountRepository.findByIdForUpdate(13L)).thenReturn(java.util.Optional.of(cash));

        double totalBefore = sumBalances(scb, cityBank, bkash, cash);

        service.applyBalanceChange(USER_ID, 10L, 11L, "transfer", 10000.0, false); // SCB -> City Bank
        assertEquals(totalBefore, sumBalances(scb, cityBank, bkash, cash), 0.001);

        service.applyBalanceChange(USER_ID, 11L, 12L, "transfer", 3000.0, false); // City Bank -> bKash
        assertEquals(totalBefore, sumBalances(scb, cityBank, bkash, cash), 0.001);

        service.applyBalanceChange(USER_ID, 13L, 10L, "transfer", 2000.0, false); // Cash -> SCB
        assertEquals(totalBefore, sumBalances(scb, cityBank, bkash, cash), 0.001, "no money may ever be duplicated across a chain of transfers");

        assertEquals(62000.0, scb.getCurrentBalance(), 0.001);
        assertEquals(7000.0, cityBank.getCurrentBalance(), 0.001);
        assertEquals(3000.0, bkash.getCurrentBalance(), 0.001);
        assertEquals(3000.0, cash.getCurrentBalance(), 0.001);
    }

    private static double sumBalances(AccountEntity... accounts) {
        double total = 0;
        for (AccountEntity a : accounts) total += a.getCurrentBalance();
        return total;
    }
}
