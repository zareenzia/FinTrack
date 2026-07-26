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
        when(accountRepository.findById(1L)).thenReturn(java.util.Optional.of(bank));

        service.applyBalanceChange(USER_ID, 1L, null, "expense", 100.0, false);

        assertEquals(900.0, bank.getCurrentBalance(), 0.001);
    }

    @Test
    void creditCardExpenseIncreasesOutstanding() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findById(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, 2L, null, "expense", 5000.0, false);

        assertEquals(25000.0, card.getCurrentBalance(), 0.001, "spending on a credit card must INCREASE outstanding, not decrease it");
    }

    @Test
    void creditCardIncomeDecreasesOutstandingLikeARefund() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findById(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, 2L, null, "income", 1000.0, false);

        assertEquals(19000.0, card.getCurrentBalance(), 0.001);
    }

    @Test
    void normalAccountAsTransferDestinationIncreasesBalance() {
        AccountEntity savings = account(3L, "BANK", 500.0);
        when(accountRepository.findById(3L)).thenReturn(java.util.Optional.of(savings));

        service.applyBalanceChange(USER_ID, null, 3L, "transfer", 200.0, false);

        assertEquals(700.0, savings.getCurrentBalance(), 0.001);
    }

    @Test
    void creditCardAsTransferDestinationDecreasesOutstandingAsAPayment() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findById(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, null, 2L, "transfer", 15000.0, false);

        assertEquals(5000.0, card.getCurrentBalance(), 0.001, "a payment (transfer-in) must DECREASE outstanding");
    }

    @Test
    void creditCardAsTransferSourceIncreasesOutstandingAsACashAdvance() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findById(2L)).thenReturn(java.util.Optional.of(card));

        service.applyBalanceChange(USER_ID, 2L, null, "transfer", 3000.0, false);

        assertEquals(23000.0, card.getCurrentBalance(), 0.001);
    }

    @Test
    void reverseThenReapplyNetsToTheAmountDifference() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findById(2L)).thenReturn(java.util.Optional.of(card));

        // Old: 5000 expense already applied (outstanding at 20000 already reflects it). Editing to 7000:
        service.applyBalanceChange(USER_ID, 2L, null, "expense", 5000.0, true);  // reverse old
        service.applyBalanceChange(USER_ID, 2L, null, "expense", 7000.0, false); // apply new

        assertEquals(22000.0, card.getCurrentBalance(), 0.001, "net effect of editing 5000 -> 7000 must be +2000");
    }

    @Test
    void unknownAccountIsSilentlyIgnored() {
        when(accountRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        // Should not throw.
        service.applyBalanceChange(USER_ID, 99L, null, "expense", 100.0, false);
    }

    @Test
    void createTransactionValidatesSavesAndAppliesBalance() {
        AccountEntity card = account(2L, "CREDIT_CARD", 20000.0);
        when(accountRepository.findById(2L)).thenReturn(java.util.Optional.of(card));
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
}
