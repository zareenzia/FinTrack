package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.service.RecurringTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    public RecurringTransactionApiController(RecurringTransactionService recurringTransactionService,
                                              CategoryRepository categoryRepository) {
        this.recurringTransactionService = recurringTransactionService;
        this.categoryRepository = categoryRepository;
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

        // Editing only affects future occurrences — nextExecutionDate/lastExecutionDate are left untouched here.
        applyRequestToEntity(body, entity, startDate);
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
            String endDate
    ) {}

    private record StatusRequest(String status) {}
}
