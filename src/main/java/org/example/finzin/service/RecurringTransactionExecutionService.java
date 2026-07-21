package org.example.finzin.service;

import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.repository.RecurringTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Reminds users about due RecurringTransaction occurrences — it deliberately does NOT create
 * Transaction rows or touch account balances on its own. Auto-posting on a schedule was misleading
 * (the app would silently record money as spent/received on a date the user might not actually
 * pay/receive it), so every occurrence now waits for an explicit confirm or skip via
 * {@code RecurringTransactionApiController} (which posts through {@code AccountBalanceService}
 * just like a manual transaction). This class only surfaces "this is due" notifications; it never
 * advances {@code nextExecutionDate} itself — that only moves once the user confirms or skips.
 */
@Service
public class RecurringTransactionExecutionService {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final NotificationService notificationService;

    public RecurringTransactionExecutionService(RecurringTransactionRepository recurringTransactionRepository,
                                                 NotificationService notificationService) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.notificationService = notificationService;
    }

    public void processAllDue() {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> due = recurringTransactionRepository
                .findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", today);
        due.forEach(this::remindOne);
    }

    public void processDueForUser(Long userId) {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> due = recurringTransactionRepository
                .findByUserIdAndStatusAndNextExecutionDateLessThanEqual(userId, "ACTIVE", today);
        due.forEach(this::remindOne);
    }

    private void remindOne(RecurringTransactionEntity recurring) {
        notificationService.createIfNotRecent(
                recurring.getUserId(),
                "RECURRING_PENDING",
                recurring.getTransactionName() + " needs confirmation",
                "\"" + recurring.getTransactionName() + "\" (" + recurring.getAmount() + ") was due on "
                        + recurring.getNextExecutionDate() + ". Confirm it if it happened, or skip it, in Recurring Transactions.",
                "RECURRING_TRANSACTION",
                recurring.getId()
        );
    }

    public static LocalDate computeNextDate(LocalDate from, String frequency, int intervalValue) {
        return switch (frequency) {
            case "DAILY" -> from.plusDays(intervalValue);
            case "WEEKLY" -> from.plusWeeks(intervalValue);
            case "MONTHLY" -> from.plusMonths(intervalValue);
            case "QUARTERLY" -> from.plusMonths(3L * intervalValue);
            case "YEARLY" -> from.plusYears(intervalValue);
            default -> from.plusMonths(intervalValue);
        };
    }

    /** Generates "due soon" reminder notifications (tomorrow / in 2 days) — avoids duplicate reminders per day. */
    public void sendUpcomingReminders() {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> upcoming = recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", today.plusDays(2));
        for (RecurringTransactionEntity recurring : upcoming) {
            long daysUntil = today.until(recurring.getNextExecutionDate()).getDays();
            if (daysUntil <= 0) continue; // already due/overdue — the "needs confirmation" reminder covers this instead
            String when = daysUntil == 1 ? "tomorrow" : "in " + daysUntil + " days";
            notificationService.createIfNotRecent(
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
