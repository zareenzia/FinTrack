package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.service.AccountBalanceService;
import org.example.finzin.service.CreditCardValidationException;
import org.example.finzin.service.RecurringTransactionExecutionService;
import org.example.finzin.service.RecurringTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recurring-transactions")
public class RecurringTransactionApiController {

    private final RecurringTransactionService recurringTransactionService;
    private final CategoryRepository categoryRepository;
    private final AccountBalanceService accountBalanceService;
    private final DocumentIndexer documentIndexer;

    public RecurringTransactionApiController(RecurringTransactionService recurringTransactionService,
                                              CategoryRepository categoryRepository,
                                              AccountBalanceService accountBalanceService,
                                              DocumentIndexer documentIndexer) {
        this.recurringTransactionService = recurringTransactionService;
        this.categoryRepository = categoryRepository;
        this.accountBalanceService = accountBalanceService;
        this.documentIndexer = documentIndexer;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public List<Map<String, Object>> getRecurringTransactions(HttpServletRequest request) {
        Long userId = getUserId(request);
        return recurringTransactionService.getForUser(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/upcoming")
    public List<Map<String, Object>> getUpcoming(HttpServletRequest request,
                                                  @RequestParam(required = false, defaultValue = "14") Integer days) {
        Long userId = getUserId(request);
        return recurringTransactionService.getUpcoming(userId, days).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Occurrences that are due (or overdue) and awaiting the user's confirm/skip decision. */
    @GetMapping("/pending")
    public List<Map<String, Object>> getPending(HttpServletRequest request) {
        Long userId = getUserId(request);
        LocalDate today = LocalDate.now();
        return recurringTransactionService.getForUser(userId).stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()) && !r.getNextExecutionDate().isAfter(today))
                .sorted((a, b) -> a.getNextExecutionDate().compareTo(b.getNextExecutionDate()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> createRecurringTransaction(HttpServletRequest request, @RequestBody RecurringTransactionRequest body) {
        Long userId = getUserId(request);
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing request body"));
        }

        LocalDate startDate = parseDate(body.startDate());
        String error = recurringTransactionService.validate(userId, body.transactionName(), body.transactionType(),
                body.amount(), body.categoryId(), body.frequency(), body.intervalValue(), startDate);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }

        RecurringTransactionEntity entity = new RecurringTransactionEntity();
        entity.setUserId(userId);
        applyRequestToEntity(body, entity, startDate);
        entity.setNextExecutionDate(startDate);
        entity.setStatus("ACTIVE");

        RecurringTransactionEntity saved = recurringTransactionService.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRecurringTransaction(HttpServletRequest request, @PathVariable Long id,
                                                         @RequestBody RecurringTransactionRequest body) {
        Long userId = getUserId(request);
        RecurringTransactionEntity entity = recurringTransactionService.findOwnedById(id, userId);
        if (entity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Recurring transaction not found"));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing request body"));
        }

        LocalDate startDate = parseDate(body.startDate());
        String error = recurringTransactionService.validate(userId, body.transactionName(), body.transactionType(),
                body.amount(), body.categoryId(), body.frequency(), body.intervalValue(), startDate);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }

        // An explicit nextExecutionDate from the client (the user directly editing the "Next Run"
        // field) always wins outright — but the scheduler backfills in a loop whenever
        // nextExecutionDate <= today (see RecurringTransactionExecutionService.processOne), so an
        // unvalidated past date here would silently generate one real transaction per missed period.
        // Reject anything before today, and anything that wouldn't be strictly after the last real
        // execution (which would re-trigger/duplicate a period that already ran).
        LocalDate explicitNextExecutionDate = parseDate(body.nextExecutionDate());
        if (explicitNextExecutionDate != null) {
            if (explicitNextExecutionDate.isBefore(LocalDate.now())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Next Run cannot be set to a date in the past."));
            }
            if (entity.getLastExecutionDate() != null && !explicitNextExecutionDate.isAfter(entity.getLastExecutionDate())) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Next Run must be after the last executed date (" + entity.getLastExecutionDate() + ")."));
            }
        }

        LocalDate oldStartDate = entity.getStartDate();
        String oldFrequency = entity.getFrequency();
        Integer oldIntervalValue = entity.getIntervalValue();

        applyRequestToEntity(body, entity, startDate);

        if (explicitNextExecutionDate != null) {
            entity.setNextExecutionDate(explicitNextExecutionDate);
        } else {
            // Re-anchor nextExecutionDate to the new schedule ONLY when startDate/frequency/interval
            // actually changed. A cosmetic edit (amount, description, category, account) must never
            // move an already-established next-run date — including for a never-executed schedule:
            // recomputing unconditionally there would silently fast-forward past a backdated
            // startDate the scheduler simply hasn't caught up on yet, skipping its normal catch-up
            // execution instead of just correctly re-anchoring an actually-changed schedule.
            boolean scheduleChanged = !entity.getStartDate().equals(oldStartDate)
                    || !entity.getFrequency().equals(oldFrequency)
                    || !entity.getIntervalValue().equals(oldIntervalValue);
            if (scheduleChanged) {
                entity.setNextExecutionDate(recurringTransactionService.recalculateNextExecutionDateForEdit(
                        entity.getStartDate(), entity.getFrequency(), entity.getIntervalValue(), entity.getLastExecutionDate()));
            }
        }

        RecurringTransactionEntity saved = recurringTransactionService.save(entity);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody StatusRequest body) {
        Long userId = getUserId(request);
        RecurringTransactionEntity entity = recurringTransactionService.findOwnedById(id, userId);
        if (entity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Recurring transaction not found"));
        }
        if (body == null || !recurringTransactionService.isValidStatus(body.status())) {
            return ResponseEntity.badRequest().body(Map.of("error", "status must be ACTIVE, PAUSED, or COMPLETED"));
        }

        String newStatus = body.status().toUpperCase(Locale.ROOT);
        if (newStatus.equals("ACTIVE") && entity.getStatus().equals("PAUSED")) {
            // Resuming skips the paused window rather than backfilling it.
            entity.setNextExecutionDate(recurringTransactionService.nextOccurrenceOnOrAfterToday(entity));
        }
        entity.setStatus(newStatus);
        RecurringTransactionEntity saved = recurringTransactionService.save(entity);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * Confirms that a due occurrence actually happened — creates a real transaction (through
     * {@link AccountBalanceService}, so credit-card sign-inversion/limit validation applies exactly
     * like a manual entry) and advances the schedule by one cadence step from its own prior date.
     * All body fields are optional overrides of what actually happened (e.g. paid on a different day,
     * for a different amount) — anything omitted falls back to the recurring transaction's template.
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirmOccurrence(HttpServletRequest request, @PathVariable Long id,
                                                @RequestBody(required = false) ConfirmRequest body) {
        Long userId = getUserId(request);
        RecurringTransactionEntity recurring = recurringTransactionService.findOwnedById(id, userId);
        if (recurring == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Recurring transaction not found"));
        }
        if ("COMPLETED".equals(recurring.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "This recurring transaction has already completed."));
        }

        LocalDate date = recurring.getNextExecutionDate();
        if (body != null && body.date() != null && !body.date().isBlank()) {
            date = parseDate(body.date());
        }
        double amount = (body != null && body.amount() != null) ? body.amount() : recurring.getAmount();
        if (amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid amount."));
        }
        Long categoryId = (body != null && body.categoryId() != null) ? body.categoryId() : recurring.getCategoryId();
        Long sourceAccountId = (body != null && body.sourceAccountId() != null) ? body.sourceAccountId() : recurring.getSourceAccountId();
        Long destinationAccountId = (body != null && body.destinationAccountId() != null) ? body.destinationAccountId() : recurring.getDestinationAccountId();
        String description = (body != null && body.description() != null && !body.description().isBlank())
                ? body.description()
                : (recurring.getDescription() != null && !recurring.getDescription().isBlank() ? recurring.getDescription() : recurring.getTransactionName());

        CategoryEntity category = ("transfer".equals(recurring.getTransactionType()) || categoryId == null)
                ? null
                : categoryRepository.findById(categoryId).orElse(null);

        TransactionEntity entity = new TransactionEntity(
                userId,
                amount,
                description,
                category,
                recurring.getTransactionType(),
                date.atStartOfDay(),
                LocalDateTime.now()
        );
        entity.setSourceAccountId(sourceAccountId);
        entity.setDestinationAccountId(destinationAccountId);
        entity.setIsAutoGenerated(true);
        entity.setRecurringTransactionId(recurring.getId());

        AccountBalanceService.TransactionSaveResult result;
        try {
            result = accountBalanceService.createTransaction(userId, entity);
        } catch (CreditCardValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        documentIndexer.indexTransaction(result.transaction());

        recurring.setLastExecutionDate(date);
        LocalDate next = RecurringTransactionExecutionService.computeNextDate(
                recurring.getNextExecutionDate(), recurring.getFrequency(), recurring.getIntervalValue());
        recurring.setNextExecutionDate(next);
        if (recurring.getEndDate() != null && next.isAfter(recurring.getEndDate())) {
            recurring.setStatus("COMPLETED");
        }
        RecurringTransactionEntity savedRecurring = recurringTransactionService.save(recurring);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transactionId", result.transaction().getId());
        response.put("recurringTransaction", toResponse(savedRecurring));
        if (result.warning() != null) {
            response.put("warning", result.warning());
        }
        return ResponseEntity.ok(response);
    }

    /** Skips a due occurrence — advances the schedule without recording any transaction. */
    @PostMapping("/{id}/skip")
    public ResponseEntity<?> skipOccurrence(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        RecurringTransactionEntity recurring = recurringTransactionService.findOwnedById(id, userId);
        if (recurring == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Recurring transaction not found"));
        }
        if ("COMPLETED".equals(recurring.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "This recurring transaction has already completed."));
        }

        LocalDate next = RecurringTransactionExecutionService.computeNextDate(
                recurring.getNextExecutionDate(), recurring.getFrequency(), recurring.getIntervalValue());
        recurring.setNextExecutionDate(next);
        if (recurring.getEndDate() != null && next.isAfter(recurring.getEndDate())) {
            recurring.setStatus("COMPLETED");
        }
        RecurringTransactionEntity saved = recurringTransactionService.save(recurring);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRecurringTransaction(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        RecurringTransactionEntity entity = recurringTransactionService.findOwnedById(id, userId);
        if (entity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Recurring transaction not found"));
        }
        // Deleting the schedule never touches previously generated transactions (no cascade).
        recurringTransactionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void applyRequestToEntity(RecurringTransactionRequest body, RecurringTransactionEntity entity, LocalDate startDate) {
        entity.setTransactionName(body.transactionName().trim());
        entity.setDescription(body.description());
        entity.setTransactionType(body.transactionType().toLowerCase(Locale.ROOT));
        entity.setCategoryId("transfer".equalsIgnoreCase(body.transactionType()) ? null : body.categoryId());
        entity.setAmount(body.amount());
        entity.setSourceAccountId(body.sourceAccountId());
        entity.setDestinationAccountId(body.destinationAccountId());
        entity.setFrequency(body.frequency().toUpperCase(Locale.ROOT));
        entity.setIntervalValue(body.intervalValue());
        entity.setStartDate(startDate);
        entity.setEndDate(parseDate(body.endDate()));
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        return LocalDate.parse(dateString);
    }

    private Map<String, Object> toResponse(RecurringTransactionEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("transactionName", entity.getTransactionName());
        map.put("description", entity.getDescription());
        map.put("transactionType", entity.getTransactionType());
        map.put("categoryId", entity.getCategoryId());
        if (entity.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findById(entity.getCategoryId()).orElse(null);
            map.put("categoryName", category != null ? category.getName() : null);
            map.put("categoryColor", category != null ? category.getColor() : null);
        } else {
            map.put("categoryName", null);
            map.put("categoryColor", null);
        }
        map.put("amount", entity.getAmount());
        map.put("sourceAccountId", entity.getSourceAccountId());
        map.put("destinationAccountId", entity.getDestinationAccountId());
        map.put("frequency", entity.getFrequency());
        map.put("intervalValue", entity.getIntervalValue());
        map.put("startDate", entity.getStartDate().toString());
        map.put("endDate", entity.getEndDate() != null ? entity.getEndDate().toString() : null);
        map.put("nextExecutionDate", entity.getNextExecutionDate().toString());
        map.put("lastExecutionDate", entity.getLastExecutionDate() != null ? entity.getLastExecutionDate().toString() : null);
        map.put("status", entity.getStatus());
        return map;
    }

    private record RecurringTransactionRequest(
            String transactionName,
            String description,
            String transactionType,
            Long categoryId,
            Double amount,
            Long sourceAccountId,
            Long destinationAccountId,
            String frequency,
            Integer intervalValue,
            String startDate,
            String endDate,
            String nextExecutionDate
    ) {}

    private record StatusRequest(String status) {}

    private record ConfirmRequest(
            String date,
            Double amount,
            Long categoryId,
            Long sourceAccountId,
            Long destinationAccountId,
            String description
    ) {}
}
