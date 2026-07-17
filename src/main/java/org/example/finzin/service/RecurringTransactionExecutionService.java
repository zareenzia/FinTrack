package org.example.finzin.service;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.RecurringTransactionRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns due RecurringTransaction definitions into real Transaction rows.
 * Kept isolated from FinanceApiController so existing manual transaction CRUD is never touched.
 */
@Service
public class RecurringTransactionExecutionService {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;
    private final DocumentIndexer documentIndexer;

    public RecurringTransactionExecutionService(RecurringTransactionRepository recurringTransactionRepository,
                                                 TransactionRepository transactionRepository,
                                                 AccountRepository accountRepository,
                                                 CategoryRepository categoryRepository,
                                                 NotificationService notificationService,
                                                 DocumentIndexer documentIndexer) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.notificationService = notificationService;
        this.documentIndexer = documentIndexer;
    }

    public void processAllDue() {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> due = recurringTransactionRepository
                .findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", today);
        due.forEach(this::processOne);
    }

    public void processDueForUser(Long userId) {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> due = recurringTransactionRepository
                .findByUserIdAndStatusAndNextExecutionDateLessThanEqual(userId, "ACTIVE", today);
        due.forEach(this::processOne);
    }

    private void processOne(RecurringTransactionEntity recurring) {
        LocalDate today = LocalDate.now();

        while (!recurring.getStatus().equals("COMPLETED") && !recurring.getNextExecutionDate().isAfter(today)) {
            LocalDate occurrenceDate = recurring.getNextExecutionDate();

            boolean generated = tryGenerateTransaction(recurring, occurrenceDate);
            if (generated) {
                recurring.setLastExecutionDate(occurrenceDate);
            }

            LocalDate next = computeNextDate(occurrenceDate, recurring.getFrequency(), recurring.getIntervalValue());
            recurring.setNextExecutionDate(next);

            if (recurring.getEndDate() != null && next.isAfter(recurring.getEndDate())) {
                recurring.setStatus("COMPLETED");
            }
        }

        recurringTransactionRepository.save(recurring);
    }

    private boolean tryGenerateTransaction(RecurringTransactionEntity recurring, LocalDate occurrenceDate) {
        Long userId = recurring.getUserId();
        String type = recurring.getTransactionType();
        double amount = recurring.getAmount();
        Long sourceAccountId = recurring.getSourceAccountId();

        boolean debitsSource = type.equals("expense") || type.equals("savings") || type.equals("transfer");
        if (debitsSource && sourceAccountId != null) {
            AccountEntity source = accountRepository.findById(sourceAccountId).orElse(null);
            if (source != null && source.getUserId().equals(userId) && source.getCurrentBalance() < amount) {
                notificationService.create(
                        userId,
                        "RECURRING_INSUFFICIENT_BALANCE",
                        recurring.getTransactionName() + " could not be completed",
                        "Insufficient balance in " + source.getAccountNickname() + " to process \"" + recurring.getTransactionName() + "\" (" + amount + ") on " + occurrenceDate + ".",
                        "RECURRING_TRANSACTION",
                        recurring.getId()
                );
                return false;
            }
        }

        TransactionEntity entity = new TransactionEntity(
                userId,
                amount,
                recurring.getDescription() != null && !recurring.getDescription().isBlank() ? recurring.getDescription() : recurring.getTransactionName(),
                recurring.getCategoryId() != null ? categoryRepository.findById(recurring.getCategoryId()).orElse(null) : null,
                type,
                occurrenceDate.atStartOfDay(),
                LocalDateTime.now()
        );
        entity.setSourceAccountId(sourceAccountId);
        entity.setDestinationAccountId(recurring.getDestinationAccountId());
        entity.setIsAutoGenerated(true);
        entity.setRecurringTransactionId(recurring.getId());
        TransactionEntity saved = transactionRepository.save(entity);
        documentIndexer.indexTransaction(saved);

        applyBalanceChange(userId, sourceAccountId, recurring.getDestinationAccountId(), type, amount);

        notificationService.create(
                userId,
                "RECURRING_GENERATED",
                recurring.getTransactionName() + " added",
                recurring.getTransactionName() + " (" + amount + ") was automatically posted on " + occurrenceDate + ".",
                "RECURRING_TRANSACTION",
                recurring.getId()
        );
        return true;
    }

    /** Mirrors FinanceApiController#applyBalanceChange (kept separate on purpose, see class Javadoc). */
    private void applyBalanceChange(Long userId, Long sourceAccountId, Long destinationAccountId, String type, double amount) {
        if (sourceAccountId != null) {
            AccountEntity account = accountRepository.findById(sourceAccountId).orElse(null);
            if (account != null && account.getUserId().equals(userId)) {
                switch (type) {
                    case "income" -> account.setCurrentBalance(account.getCurrentBalance() + amount);
                    case "expense", "savings" -> account.setCurrentBalance(account.getCurrentBalance() - amount);
                    case "transfer" -> account.setCurrentBalance(account.getCurrentBalance() - amount);
                }
                accountRepository.save(account);
            }
        }
        if ("transfer".equals(type) && destinationAccountId != null) {
            AccountEntity dest = accountRepository.findById(destinationAccountId).orElse(null);
            if (dest != null && dest.getUserId().equals(userId)) {
                dest.setCurrentBalance(dest.getCurrentBalance() + amount);
                accountRepository.save(dest);
            }
        }
    }

    static LocalDate computeNextDate(LocalDate from, String frequency, int intervalValue) {
        return switch (frequency) {
            case "DAILY" -> from.plusDays(intervalValue);
            case "WEEKLY" -> from.plusWeeks(intervalValue);
            case "MONTHLY" -> from.plusMonths(intervalValue);
            case "QUARTERLY" -> from.plusMonths(3L * intervalValue);
            case "YEARLY" -> from.plusYears(intervalValue);
            default -> from.plusMonths(intervalValue);
        };
    }

    /** Generates "due soon" reminder notifications (today / tomorrow / in 2 days) — avoids duplicate reminders per day. */
    public void sendUpcomingReminders() {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> upcoming = recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", today.plusDays(2));
        for (RecurringTransactionEntity recurring : upcoming) {
            long daysUntil = today.until(recurring.getNextExecutionDate()).getDays();
            String when = daysUntil == 0 ? "today" : daysUntil == 1 ? "tomorrow" : "in " + daysUntil + " days";
            notificationService.create(
                    recurring.getUserId(),
                    "RECURRING_UPCOMING",
                    recurring.getTransactionName() + " is due " + when,
                    recurring.getTransactionName() + " (" + recurring.getAmount() + ") is scheduled for " + when + ".",
                    "RECURRING_TRANSACTION",
                    recurring.getId()
            );
        }
    }
}
