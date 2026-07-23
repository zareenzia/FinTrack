package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.BudgetEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.SavingsBudgetEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.service.BudgetExportService;
import org.example.finzin.service.BudgetPlanService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/budget-plans")
public class BudgetPlanApiController {

    private final BudgetPlanService budgetPlanService;
    private final BudgetExportService budgetExportService;
    private final CategoryRepository categoryRepository;

    public BudgetPlanApiController(BudgetPlanService budgetPlanService, BudgetExportService budgetExportService,
                                    CategoryRepository categoryRepository) {
        this.budgetPlanService = budgetPlanService;
        this.budgetExportService = budgetExportService;
        this.categoryRepository = categoryRepository;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request,
                                           @RequestParam(required = false) String period,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(required = false) String sort) {
        Long userId = getUserId(request);
        return budgetPlanService.listForUser(userId, period, status, search, sort).stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/current")
    public ResponseEntity<?> current(HttpServletRequest request) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.getCurrentPlan(userId);
        if (plan == null) {
            return ResponseEntity.ok(Map.of("hasCurrent", false));
        }
        Map<String, Object> response = toSummaryResponse(plan);
        response.put("hasCurrent", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/full")
    public ResponseEntity<?> full(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        }
        List<Map<String, Object>> categories = budgetPlanService.computeCategoryStatuses(plan);
        List<Map<String, Object>> savings = budgetPlanService.computeSavingsStatuses(plan);
        Map<String, Object> summary = budgetPlanService.computeSummary(plan, categories);
        int score = budgetPlanService.computeBudgetScore(plan, categories, savings, summary);

        Map<String, Object> response = new LinkedHashMap<>(toPlanResponse(plan));
        response.put("summary", summary);
        response.put("categories", categories);
        response.put("savings", savings);
        response.put("score", score);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody PlanRequest body) {
        Long userId = getUserId(request);
        if (body == null) return ResponseEntity.badRequest().body(Map.of("error", "Missing request body"));
        LocalDate startDate = parseDate(body.startDate());
        LocalDate endDate = parseDate(body.endDate());
        String error = budgetPlanService.validate(body.name(), body.periodType(), body.period(), startDate, endDate);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));

        BudgetPlanEntity plan = new BudgetPlanEntity();
        plan.setUserId(userId);
        applyRequestToEntity(body, plan, startDate, endDate);
        plan.setStatus("ACTIVE");
        BudgetPlanEntity saved = budgetPlanService.save(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlanResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @RequestBody PlanRequest body) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        if (body == null) return ResponseEntity.badRequest().body(Map.of("error", "Missing request body"));
        LocalDate startDate = parseDate(body.startDate());
        LocalDate endDate = parseDate(body.endDate());
        String error = budgetPlanService.validate(body.name(), body.periodType(), body.period(), startDate, endDate);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));

        applyRequestToEntity(body, plan, startDate, endDate);
        return ResponseEntity.ok(toPlanResponse(budgetPlanService.save(plan)));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<?> archive(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        budgetPlanService.archive(plan);
        return ResponseEntity.ok(toPlanResponse(plan));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        budgetPlanService.delete(plan);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<?> duplicate(HttpServletRequest request, @PathVariable Long id, @RequestBody PlanRequest body) {
        Long userId = getUserId(request);
        BudgetPlanEntity source = budgetPlanService.findOwnedById(id, userId);
        if (source == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        if (body == null) return ResponseEntity.badRequest().body(Map.of("error", "Missing request body"));
        LocalDate startDate = parseDate(body.startDate());
        LocalDate endDate = parseDate(body.endDate());
        String error = budgetPlanService.validate(
                body.name() != null && !body.name().isBlank() ? body.name() : source.getName(),
                body.periodType(), body.period(), startDate, endDate);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));

        BudgetPlanEntity copy = budgetPlanService.duplicate(source, body.name(), body.periodType(), body.period(), startDate, endDate);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlanResponse(copy));
    }

    @PostMapping("/copy-previous")
    public ResponseEntity<?> copyPrevious(HttpServletRequest request, @RequestBody PlanRequest body) {
        Long userId = getUserId(request);
        if (body == null) return ResponseEntity.badRequest().body(Map.of("error", "Missing request body"));
        LocalDate startDate = parseDate(body.startDate());
        LocalDate endDate = parseDate(body.endDate());
        String error = budgetPlanService.validate(body.name(), body.periodType(), body.period(), startDate, endDate);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));

        BudgetPlanEntity plan = new BudgetPlanEntity();
        plan.setUserId(userId);
        applyRequestToEntity(body, plan, startDate, endDate);
        plan.setStatus("ACTIVE");
        BudgetPlanEntity saved = budgetPlanService.save(plan);
        BudgetPlanEntity result = budgetPlanService.copyFromPreviousPlan(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlanResponse(result));
    }

    @PostMapping("/{id}/categories")
    public ResponseEntity<?> upsertCategory(HttpServletRequest request, @PathVariable Long id, @RequestBody CategoryBudgetRequest body) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        if (body == null || body.categoryId() == null || body.amount() == null || body.amount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "categoryId and a positive amount are required"));
        }
        CategoryEntity category = categoryRepository.findById(body.categoryId()).orElse(null);
        if (category == null || category.getUserId() == null || !category.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category"));
        }
        BudgetEntity saved = budgetPlanService.upsertCategoryBudget(plan, body.categoryId(), body.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "categoryId", saved.getCategoryId(), "budgetAmount", saved.getBudgetAmount()));
    }

    @DeleteMapping("/{id}/categories/{budgetId}")
    public ResponseEntity<?> deleteCategory(HttpServletRequest request, @PathVariable Long id, @PathVariable Long budgetId) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        boolean owns = budgetPlanService.computeCategoryStatuses(plan).stream().anyMatch(c -> budgetId.equals(c.get("id")));
        if (!owns) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category budget not found"));
        budgetPlanService.deleteCategoryBudgetById(budgetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/savings")
    public ResponseEntity<?> upsertSavings(HttpServletRequest request, @PathVariable Long id, @RequestBody SavingsBudgetRequest body) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        if (body == null || body.categoryId() == null || body.amount() == null || body.amount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "categoryId and a positive target amount are required"));
        }
        CategoryEntity category = categoryRepository.findById(body.categoryId()).orElse(null);
        if (category == null || category.getUserId() == null || !category.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category"));
        }
        if (body.initialAmount() != null && body.initialAmount() < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "initialAmount cannot be negative"));
        }
        SavingsBudgetEntity saved = budgetPlanService.upsertSavingsBudget(plan, body.categoryId(), body.amount(),
                body.initialAmount(), body.storageAccountId(), body.sourceAccountId(), body.sourceDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(), "categoryId", saved.getCategoryId(), "targetAmount", saved.getTargetAmount(),
                "initialAmount", saved.getInitialAmount()));
    }

    @DeleteMapping("/{id}/savings/{savingsId}")
    public ResponseEntity<?> deleteSavings(HttpServletRequest request, @PathVariable Long id, @PathVariable Long savingsId) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));
        boolean owns = budgetPlanService.computeSavingsStatuses(plan).stream().anyMatch(s -> savingsId.equals(s.get("id")));
        if (!owns) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Savings goal not found"));
        budgetPlanService.deleteSavingsBudgetById(savingsId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export/{format}")
    public ResponseEntity<?> export(HttpServletRequest request, @PathVariable Long id, @PathVariable String format) {
        Long userId = getUserId(request);
        BudgetPlanEntity plan = budgetPlanService.findOwnedById(id, userId);
        if (plan == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Budget plan not found"));

        List<Map<String, Object>> categories = budgetPlanService.computeCategoryStatuses(plan);
        List<Map<String, Object>> savings = budgetPlanService.computeSavingsStatuses(plan);
        Map<String, Object> summary = budgetPlanService.computeSummary(plan, categories);
        int score = budgetPlanService.computeBudgetScore(plan, categories, savings, summary);
        String filenameBase = "budget-" + plan.getPeriod();

        try {
            return switch (format.toLowerCase(Locale.ROOT)) {
                case "csv" -> {
                    String csv = budgetExportService.generateCsv(plan, summary, categories, savings, score);
                    yield ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("text/csv"))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filenameBase + ".csv\"")
                            .body(csv.getBytes(StandardCharsets.UTF_8));
                }
                case "excel", "xlsx" -> {
                    byte[] bytes = budgetExportService.generateExcel(plan, summary, categories, savings, score);
                    yield ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filenameBase + ".xlsx\"")
                            .body(bytes);
                }
                case "pdf" -> {
                    byte[] bytes = budgetExportService.generatePdf(plan, summary, categories, savings, score);
                    yield ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filenameBase + ".pdf\"")
                            .body(bytes);
                }
                default -> ResponseEntity.badRequest().body(Map.of("error", "format must be csv, excel, or pdf"));
            };
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to generate export"));
        }
    }

    private void applyRequestToEntity(PlanRequest body, BudgetPlanEntity entity, LocalDate startDate, LocalDate endDate) {
        entity.setName(body.name().trim());
        entity.setPeriodType(body.periodType().toUpperCase(Locale.ROOT));
        entity.setPeriod(body.period());
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setPlannedIncome(body.plannedIncome() != null ? body.plannedIncome() : 0.0);
        entity.setPlannedSavings(body.plannedSavings() != null ? body.plannedSavings() : 0.0);
        entity.setNotes(body.notes());
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s);
    }

    private Map<String, Object> toPlanResponse(BudgetPlanEntity plan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", plan.getId());
        map.put("name", plan.getName());
        map.put("periodType", plan.getPeriodType());
        map.put("period", plan.getPeriod());
        map.put("startDate", plan.getStartDate().toString());
        map.put("endDate", plan.getEndDate().toString());
        map.put("plannedIncome", plan.getPlannedIncome());
        map.put("plannedSavings", plan.getPlannedSavings());
        map.put("notes", plan.getNotes());
        map.put("status", plan.getStatus());
        return map;
    }

    /** Plan fields + summary + score, used for list/current views where callers want more than the bare plan. */
    private Map<String, Object> toSummaryResponse(BudgetPlanEntity plan) {
        List<Map<String, Object>> categories = budgetPlanService.computeCategoryStatuses(plan);
        List<Map<String, Object>> savings = budgetPlanService.computeSavingsStatuses(plan);
        Map<String, Object> summary = budgetPlanService.computeSummary(plan, categories);
        int score = budgetPlanService.computeBudgetScore(plan, categories, savings, summary);

        Map<String, Object> map = new LinkedHashMap<>(toPlanResponse(plan));
        map.put("summary", summary);
        map.put("score", score);
        map.put("categoryCount", categories.size());
        map.put("categories", categories);
        return map;
    }

    private record PlanRequest(
            String name, String periodType, String period, String startDate, String endDate,
            Double plannedIncome, Double plannedSavings, String notes
    ) {}

    private record CategoryBudgetRequest(Long categoryId, Double amount) {}

    private record SavingsBudgetRequest(Long categoryId, Double amount, Double initialAmount,
                                         Long storageAccountId, Long sourceAccountId, String sourceDescription) {}
}
