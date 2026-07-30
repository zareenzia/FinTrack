package org.example.finzin.service;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.gamification.GamificationEventType;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Owns every balance-affecting side effect of a transaction create/update/delete, for every
 * account type. Moved out of {@code FinanceApiController} so it can be wrapped in
 * {@code @Transactional} (Spring's proxy only applies to calls into an injected bean, never to a
 * private method called from within the same class) and so credit-card validation/sign-inversion
 * lives in one centralized place instead of being duplicated at each call site.
 */
@Service
public class AccountBalanceService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CreditCardService creditCardService;
    private final ApplicationEventPublisher eventPublisher;

    public AccountBalanceService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                                  CreditCardService creditCardService, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.creditCardService = creditCardService;
        this.eventPublisher = eventPublisher;
    }

    /** {@code warning} is non-null only when a credit card purchase exceeded its limit under WARN mode. */
    public record TransactionSaveResult(TransactionEntity transaction, String warning) {}

    @Transactional
    public TransactionSaveResult createTransaction(Long userId, TransactionEntity entity) {
        String warning = creditCardService.validate(userId, entity.getSourceAccountId(), entity.getDestinationAccountId(),
                entity.getTransactionType(), entity.getAmount());
        TransactionEntity saved = transactionRepository.save(entity);
        applyBalanceChange(userId, saved.getSourceAccountId(), saved.getDestinationAccountId(), saved.getTransactionType(), saved.getAmount(), false);
        // Gamification reacts to creation only (not edits/deletes — see GamificationEventListener's
        // class doc for why); AFTER_COMMIT-listened, so a bug here can never roll back this transaction.
        eventPublisher.publishEvent(new GamificationEvent(userId, GamificationEventType.TRANSACTION_LOGGED, Map.of(
                "transactionType", saved.getTransactionType(),
                "transactionId", saved.getId(),
                "amount", saved.getAmount()
        )));
        return new TransactionSaveResult(saved, warning);
    }

    /**
     * {@code entity} must already have its new field values set by the caller (description/category/
     * date/type/amount/accounts) before calling this — only the old values are needed separately,
     * to reverse their effect first. Reversing never needs validation (it only reduces obligations);
     * throwing from {@link CreditCardService#validate} after the reversal is safe specifically
     * because this whole method is one {@code @Transactional} unit — the reversal rolls back too.
     */
    @Transactional
    public TransactionSaveResult updateTransaction(Long userId, TransactionEntity entity,
                                                    Long oldSourceAccountId, Long oldDestinationAccountId,
                                                    String oldType, double oldAmount) {
        applyBalanceChange(userId, oldSourceAccountId, oldDestinationAccountId, oldType, oldAmount, true);
        String warning = creditCardService.validate(userId, entity.getSourceAccountId(), entity.getDestinationAccountId(),
                entity.getTransactionType(), entity.getAmount());
        TransactionEntity saved = transactionRepository.save(entity);
        applyBalanceChange(userId, saved.getSourceAccountId(), saved.getDestinationAccountId(), saved.getTransactionType(), saved.getAmount(), false);
        return new TransactionSaveResult(saved, warning);
    }

    @Transactional
    public void deleteTransaction(Long userId, TransactionEntity entity) {
        applyBalanceChange(userId, entity.getSourceAccountId(), entity.getDestinationAccountId(),
                entity.getTransactionType(), entity.getAmount(), true);
        transactionRepository.deleteById(entity.getId());
    }

    /**
     * Adjusts account balances for a transaction. For a normal account this is the original
     * arithmetic (income credits the source; expense/savings/transfer debit it; transfer credits
     * the destination). For a CREDIT_CARD account in either role, the sign is inverted — spending
     * increases what's owed, a payment (transfer-in) decreases it — because {@code currentBalance}
     * on a credit card means outstanding debt, not available funds.
     */
    @Transactional
    public void applyBalanceChange(Long userId, Long sourceAccountId, Long destinationAccountId, String type, double amount, boolean reverse) {
        double multiplier = reverse ? -1 : 1;
        if (sourceAccountId != null) {
            AccountEntity account = accountRepository.findById(sourceAccountId).orElse(null);
            if (account != null && account.getUserId().equals(userId)) {
                account.setCurrentBalance(account.getCurrentBalance() + multiplier * signedDelta(account, type, amount, true));
                accountRepository.save(account);
            }
        }
        if ("transfer".equals(type) && destinationAccountId != null) {
            AccountEntity destination = accountRepository.findById(destinationAccountId).orElse(null);
            if (destination != null && destination.getUserId().equals(userId)) {
                destination.setCurrentBalance(destination.getCurrentBalance() + multiplier * signedDelta(destination, type, amount, false));
                accountRepository.save(destination);
            }
        }
    }

    /**
     * The delta a transaction applies to {@code account}'s balance occupying the given role
     * (before any reverse-multiplier). Shared with {@link CreditCardService#getLedger} so the
     * live-update path and the ledger replay can never drift apart.
     */
    public static double signedDelta(AccountEntity account, String type, double amount, boolean isSourceRole) {
        double raw = rawDelta(type, amount, isSourceRole);
        return CreditCardService.isCreditCard(account) ? -raw : raw;
    }

    private static double rawDelta(String type, double amount, boolean isSourceRole) {
        if (isSourceRole) {
            return switch (type) {
                case "income" -> amount;
                case "expense", "savings", "transfer" -> -amount;
                default -> 0;
            };
        }
        return "transfer".equals(type) ? amount : 0;
    }
}
