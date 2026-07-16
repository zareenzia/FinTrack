package org.example.finzin.service;

import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.RecurringTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class RecurringTransactionService {

    private static final List<String> VALID_TYPES = List.of("income", "expense", "savings", "transfer");
    private static final List<String> VALID_FREQUENCIES = List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");
    private static final List<String> VALID_STATUSES = List.of("ACTIVE", "PAUSED", "COMPLETED");

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final CategoryRepository categoryRepository;

    public RecurringTransactionService(RecurringTransactionRepository recurringTransactionRepository,
                                        CategoryRepository categoryRepository) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<RecurringTransactionEntity> getForUser(Long userId) {
        return recurringTransactionRepository.findByUserId(userId);
    }

    public List<RecurringTransactionEntity> getUpcoming(Long userId, int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return recurringTransactionRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(r -> !r.getNextExecutionDate().isAfter(cutoff))
                .sorted((a, b) -> a.getNextExecutionDate().compareTo(b.getNextExecutionDate()))
                .toList();
    }

    /** Validates a create/update request; returns an error message, or null if valid. */
    public String validate(Long userId, String transactionName, String transactionType, Double amount,
                            Long categoryId, String frequency, Integer intervalValue, LocalDate startDate) {
        if (transactionName == null || transactionName.isBlank()) {
            return "Transaction name is required";
        }
        if (transactionType == null || !VALID_TYPES.contains(transactionType.toLowerCase(Locale.ROOT))) {
            return "transactionType must be income, expense, savings, or transfer";
        }
        if (amount == null || amount <= 0) {
            return "A positive amount is required";
        }
        if (frequency == null || !VALID_FREQUENCIES.contains(frequency.toUpperCase(Locale.ROOT))) {
            return "frequency must be DAILY, WEEKLY, MONTHLY, QUARTERLY, or YEARLY";
        }
        if (intervalValue == null || intervalValue < 1) {
            return "intervalValue must be at least 1";
        }
        if (startDate == null) {
            return "startDate is required";
        }
        if (!"transfer".equalsIgnoreCase(transactionType)) {
            if (categoryId == null) {
                return "category is required";
            }
            CategoryEntity category = categoryRepository.findById(categoryId).orElse(null);
            if (category == null || category.getUserId() == null || !category.getUserId().equals(userId)) {
                return "Invalid category";
            }
        }
        return null;
    }

    public boolean isValidStatus(String status) {
        return status != null && VALID_STATUSES.contains(status.toUpperCase(Locale.ROOT));
    }

    public RecurringTransactionEntity save(RecurringTransactionEntity entity) {
        return recurringTransactionRepository.save(entity);
    }

    public RecurringTransactionEntity findOwnedById(Long id, Long userId) {
        RecurringTransactionEntity entity = recurringTransactionRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return null;
        }
        return entity;
    }

    public void delete(Long id) {
        recurringTransactionRepository.deleteById(id);
    }

    /** Resuming a paused schedule skips the paused window rather than backfilling it. */
    public LocalDate nextOccurrenceOnOrAfterToday(RecurringTransactionEntity entity) {
        LocalDate date = entity.getNextExecutionDate();
        LocalDate today = LocalDate.now();
        while (date.isBefore(today)) {
            date = RecurringTransactionExecutionService.computeNextDate(date, entity.getFrequency(), entity.getIntervalValue());
        }
        return date;
    }
}
