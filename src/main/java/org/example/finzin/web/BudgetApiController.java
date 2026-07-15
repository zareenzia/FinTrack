package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.BudgetEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.service.BudgetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/budgets")
public class BudgetApiController {

    private final BudgetService budgetService;
    private final CategoryRepository categoryRepository;

    public BudgetApiController(BudgetService budgetService, CategoryRepository categoryRepository) {
        this.budgetService = budgetService;
        this.categoryRepository = categoryRepository;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public List<Map<String, Object>> getBudgets(HttpServletRequest request,
                                                 @RequestParam(required = false) String period) {
        Long userId = getUserId(request);
        String effectivePeriod = (period != null && !period.isBlank()) ? period : YearMonth.now().toString();
        return budgetService.getForUserAndPeriod(userId, effectivePeriod).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/status")
    public List<Map<String, Object>> getStatus(HttpServletRequest request,
                                                @RequestParam(required = false) String period) {
        Long userId = getUserId(request);
        String effectivePeriod = (period != null && !period.isBlank()) ? period : YearMonth.now().toString();
        return budgetService.getStatus(userId, effectivePeriod);
    }

    @PostMapping
    public ResponseEntity<?> upsertBudget(HttpServletRequest request, @RequestBody BudgetRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.categoryId() == null || body.budgetAmount() == null || body.budgetAmount() <= 0
                || body.period() == null || body.period().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "categoryId, period, and a positive budgetAmount are required"));
        }
        CategoryEntity category = categoryRepository.findById(body.categoryId()).orElse(null);
        if (category == null || category.getUserId() == null || !category.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category"));
        }
        try {
            YearMonth.parse(body.period());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "period must be formatted yyyy-MM"));
        }

        BudgetEntity saved = budgetService.upsert(userId, body.categoryId(), body.period(), body.budgetAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBudget(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        BudgetEntity entity = budgetService.findOwnedById(id, userId);
        if (entity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget not found"));
        }
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toResponse(BudgetEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("categoryId", entity.getCategoryId());
        map.put("period", entity.getPeriod());
        map.put("budgetAmount", entity.getBudgetAmount());
        return map;
    }

    private record BudgetRequest(Long categoryId, String period, Double budgetAmount) {}
}
