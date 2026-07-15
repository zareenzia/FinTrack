package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.BudgetTemplateCategoryEntity;
import org.example.finzin.entity.BudgetTemplateEntity;
import org.example.finzin.service.BudgetTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/budget-templates")
public class BudgetTemplateApiController {

    private final BudgetTemplateService budgetTemplateService;

    public BudgetTemplateApiController(BudgetTemplateService budgetTemplateService) {
        this.budgetTemplateService = budgetTemplateService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        return budgetTemplateService.listForUser(userId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody TemplateRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Template name is required"));
        }
        BudgetTemplateEntity entity = new BudgetTemplateEntity();
        entity.setUserId(userId);
        applyRequest(body, entity);
        BudgetTemplateEntity saved = budgetTemplateService.save(entity, toRows(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @RequestBody TemplateRequest body) {
        Long userId = getUserId(request);
        BudgetTemplateEntity entity = budgetTemplateService.findOwnedById(id, userId);
        if (entity == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Template not found"));
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Template name is required"));
        }
        applyRequest(body, entity);
        BudgetTemplateEntity saved = budgetTemplateService.save(entity, toRows(body));
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        BudgetTemplateEntity entity = budgetTemplateService.findOwnedById(id, userId);
        if (entity == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Template not found"));
        budgetTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<?> apply(HttpServletRequest request, @PathVariable Long id, @RequestBody ApplyRequest body) {
        Long userId = getUserId(request);
        BudgetTemplateEntity template = budgetTemplateService.findOwnedById(id, userId);
        if (template == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Template not found"));
        if (body == null || body.periodType() == null || body.period() == null || body.startDate() == null || body.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "periodType, period, startDate, and endDate are required"));
        }
        BudgetPlanEntity plan = budgetTemplateService.applyTemplate(template, body.name(), body.periodType(), body.period(),
                LocalDate.parse(body.startDate()), LocalDate.parse(body.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", plan.getId(), "name", plan.getName(), "period", plan.getPeriod()));
    }

    private void applyRequest(TemplateRequest body, BudgetTemplateEntity entity) {
        entity.setName(body.name().trim());
        entity.setPlannedIncome(body.plannedIncome() != null ? body.plannedIncome() : 0.0);
        entity.setPlannedSavings(body.plannedSavings() != null ? body.plannedSavings() : 0.0);
        entity.setNotes(body.notes());
    }

    private List<BudgetTemplateCategoryEntity> toRows(TemplateRequest body) {
        if (body.categories() == null) return List.of();
        return body.categories().stream().map(r -> {
            BudgetTemplateCategoryEntity row = new BudgetTemplateCategoryEntity();
            row.setCategoryId(r.categoryId());
            row.setPlannedAmount(r.plannedAmount());
            row.setIsSavings(Boolean.TRUE.equals(r.isSavings()));
            return row;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> toResponse(BudgetTemplateEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("plannedIncome", entity.getPlannedIncome());
        map.put("plannedSavings", entity.getPlannedSavings());
        map.put("notes", entity.getNotes());
        map.put("categories", budgetTemplateService.getRows(entity.getId()).stream().map(row -> {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("categoryId", row.getCategoryId());
            rowMap.put("plannedAmount", row.getPlannedAmount());
            rowMap.put("isSavings", row.getIsSavings());
            return rowMap;
        }).collect(Collectors.toList()));
        return map;
    }

    private record TemplateRequest(String name, Double plannedIncome, Double plannedSavings, String notes,
                                    List<TemplateCategoryRequest> categories) {}

    private record TemplateCategoryRequest(Long categoryId, Double plannedAmount, Boolean isSavings) {}

    private record ApplyRequest(String name, String periodType, String period, String startDate, String endDate) {}
}
