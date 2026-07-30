package org.example.finzin.service;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Owns everything specific to treating a CREDIT_CARD account as a liability: purchase/payment
 * validation, the derived (never stored) statement stats, and the per-card transaction ledger.
 * Depends only on repositories — never on {@link AccountBalanceService} — so the two stay a
 * one-directional dependency graph (AccountBalanceService calls into this one, not the reverse).
 */
@Service
public class CreditCardService {

    private static final double MIN_PAYMENT_FLOOR = 500.0;
    private static final double MIN_PAYMENT_RATE = 0.05;
    private static final List<String> DEBITING_SOURCE_TYPES = List.of("expense", "savings", "transfer");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public CreditCardService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public record CreditCardStats(double availableCredit, double utilizationPercent,
                                   double minimumPaymentEstimate, Integer daysUntilDue) {}

    public record LedgerEntry(String date, String description, String category, String transactionType,
                              double amount, double runningBalance) {}

    public static boolean isCreditCard(AccountEntity account) {
        return account != null && "CREDIT_CARD".equals(account.getAccountType());
    }

    /**
     * Validates a would-be transaction against credit-card constraints BEFORE it's persisted.
     * Returns a non-null warning message when the account's mode is WARN and the limit would be
     * exceeded; returns null when there's nothing to flag. Throws for BLOCK-mode limit violations
     * or any overpayment (overpayment is always enforced — not configurable, per spec).
     */
    public String validate(Long userId, Long sourceAccountId, Long destinationAccountId, String type, double amount) {
        if (sourceAccountId != null) {
            AccountEntity source = findOwned(sourceAccountId, userId);
            if (isCreditCard(source) && DEBITING_SOURCE_TYPES.contains(type)) {
                Double limit = source.getCreditLimit();
                double projectedOutstanding = source.getCurrentBalance() + amount;
                if (limit != null && projectedOutstanding > limit) {
                    String mode = source.getCreditLimitBehavior() != null ? source.getCreditLimitBehavior() : "WARN";
                    if ("BLOCK".equals(mode)) {
                        throw new CreditCardValidationException("This purchase exceeds your available credit limit.");
                    } else if ("WARN".equals(mode)) {
                        return "This purchase exceeds your available credit limit.";
                    }
                    // IGNORE: fall through, no warning
                }
            }
        }
        if ("transfer".equals(type) && destinationAccountId != null) {
            AccountEntity destination = findOwned(destinationAccountId, userId);
            if (isCreditCard(destination) && amount > destination.getCurrentBalance()) {
                throw new CreditCardValidationException("Payment exceeds current outstanding balance.");
            }
        }
        return null;
    }

    public CreditCardStats getStats(AccountEntity account) {
        double outstanding = account.getCurrentBalance();
        Double limit = account.getCreditLimit();
        double availableCredit = (limit != null) ? Math.max(0, limit - outstanding) : 0;
        double utilizationPercent = (limit != null && limit > 0) ? (outstanding / limit) * 100 : 0;
        double minimumPaymentEstimate = Math.max(MIN_PAYMENT_FLOOR, outstanding * MIN_PAYMENT_RATE);
        Integer daysUntilDue = computeDaysUntilDue(account.getDueDay());
        return new CreditCardStats(availableCredit, utilizationPercent, minimumPaymentEstimate, daysUntilDue);
    }

    /** Chronologically replays every transaction touching this account to build a running-balance ledger, newest first. */
    public List<LedgerEntry> getLedger(Long userId, Long accountId, LocalDate startDate, LocalDate endDate,
                                        String category, String type, String merchant) {
        AccountEntity account = findOwned(accountId, userId);
        if (account == null) return List.of();

        List<TransactionEntity> transactions = transactionRepository
                .findBySourceAccountIdOrDestinationAccountIdOrderByDateAsc(accountId, accountId);

        double running = account.getOpeningBalance();
        List<LedgerEntry> entries = new ArrayList<>();
        for (TransactionEntity t : transactions) {
            boolean isSourceRole = accountId.equals(t.getSourceAccountId());
            double delta = AccountBalanceService.signedDelta(account, t.getTransactionType(), t.getAmount(), isSourceRole);
            running += delta;

            LocalDate txnDate = t.getDate().toLocalDate();
            if (startDate != null && txnDate.isBefore(startDate)) continue;
            if (endDate != null && txnDate.isAfter(endDate)) continue;
            if (category != null && !category.isBlank()
                    && (t.getCategory() == null || !t.getCategory().getName().equalsIgnoreCase(category))) continue;
            if (type != null && !type.isBlank() && !type.equalsIgnoreCase(t.getTransactionType())) continue;
            if (merchant != null && !merchant.isBlank()
                    && (t.getDescription() == null || !t.getDescription().toLowerCase(Locale.ROOT).contains(merchant.toLowerCase(Locale.ROOT)))) continue;

            entries.add(new LedgerEntry(txnDate.toString(), t.getDescription(),
                    t.getCategory() != null ? t.getCategory().getName() : null,
                    t.getTransactionType(), delta, running));
        }
        Collections.reverse(entries);
        return entries;
    }

    private Integer computeDaysUntilDue(Integer dueDay) {
        if (dueDay == null) return null;
        LocalDate today = LocalDate.now();
        LocalDate dueThisMonth = today.withDayOfMonth(Math.min(dueDay, today.lengthOfMonth()));
        LocalDate due = dueThisMonth.isBefore(today) ? dueThisMonth.plusMonths(1) : dueThisMonth;
        due = due.withDayOfMonth(Math.min(dueDay, due.lengthOfMonth()));
        return (int) ChronoUnit.DAYS.between(today, due);
    }

    private AccountEntity findOwned(Long accountId, Long userId) {
        AccountEntity account = accountRepository.findById(accountId).orElse(null);
        return (account != null && account.getUserId().equals(userId)) ? account : null;
    }
}
