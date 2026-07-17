package org.example.finzin.service;

import org.example.finzin.entity.AccountEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long CARD_ID = 7L;

    @Mock private org.example.finzin.repository.AccountRepository accountRepository;
    @Mock private org.example.finzin.repository.TransactionRepository transactionRepository;

    private CreditCardService service;

    @BeforeEach
    void setUp() {
        service = new CreditCardService(accountRepository, transactionRepository);
    }

    private AccountEntity card(double outstanding, double limit, String behavior) {
        AccountEntity a = new AccountEntity();
        a.setId(CARD_ID);
        a.setUserId(USER_ID);
        a.setAccountType("CREDIT_CARD");
        a.setCurrentBalance(outstanding);
        a.setCreditLimit(limit);
        a.setCreditLimitBehavior(behavior);
        return a;
    }

    @Test
    void blockModeThrowsWhenPurchaseExceedsLimit() {
        when(accountRepository.findById(CARD_ID)).thenReturn(Optional.of(card(95000, 100000, "BLOCK")));

        assertThrows(CreditCardValidationException.class,
                () -> service.validate(USER_ID, CARD_ID, null, "expense", 10000));
    }

    @Test
    void warnModeReturnsWarningButDoesNotThrow() {
        when(accountRepository.findById(CARD_ID)).thenReturn(Optional.of(card(95000, 100000, "WARN")));

        String warning = service.validate(USER_ID, CARD_ID, null, "expense", 10000);

        assertEquals("This purchase exceeds your available credit limit.", warning);
    }

    @Test
    void ignoreModeAllowsSilently() {
        when(accountRepository.findById(CARD_ID)).thenReturn(Optional.of(card(95000, 100000, "IGNORE")));

        String warning = service.validate(USER_ID, CARD_ID, null, "expense", 10000);

        assertNull(warning);
    }

    @Test
    void withinLimitProducesNoWarningRegardlessOfMode() {
        when(accountRepository.findById(CARD_ID)).thenReturn(Optional.of(card(20000, 100000, "BLOCK")));

        String warning = service.validate(USER_ID, CARD_ID, null, "expense", 5000);

        assertNull(warning);
    }

    @Test
    void overpaymentIsAlwaysBlockedRegardlessOfBehaviorMode() {
        // destination is the credit card being paid off; behavior mode is irrelevant to overpayment
        when(accountRepository.findById(CARD_ID)).thenReturn(Optional.of(card(12000, 100000, "IGNORE")));

        assertThrows(CreditCardValidationException.class,
                () -> service.validate(USER_ID, null, CARD_ID, "transfer", 20000));
    }

    @Test
    void paymentWithinOutstandingIsAllowed() {
        when(accountRepository.findById(CARD_ID)).thenReturn(Optional.of(card(12000, 100000, "IGNORE")));

        String warning = service.validate(USER_ID, null, CARD_ID, "transfer", 12000);

        assertNull(warning);
    }

    @Test
    void statsComputeAvailableCreditAndUtilization() {
        AccountEntity account = card(38500, 100000, "WARN");

        CreditCardService.CreditCardStats stats = service.getStats(account);

        assertEquals(61500.0, stats.availableCredit(), 0.001);
        assertEquals(38.5, stats.utilizationPercent(), 0.001);
    }

    @Test
    void minimumPaymentEstimateNeverBelowFloor() {
        AccountEntity account = card(1000, 100000, "WARN");

        CreditCardService.CreditCardStats stats = service.getStats(account);

        assertEquals(500.0, stats.minimumPaymentEstimate(), 0.001, "5% of 1000 is 50, below the 500 floor");
    }
}
