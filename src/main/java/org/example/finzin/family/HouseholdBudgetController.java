package org.example.finzin.family;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.HouseholdBudgetEntity;
import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.family.dto.HouseholdBudgetRequest;
import org.example.finzin.family.dto.HouseholdBudgetResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/households/{id}/budgets")
public class HouseholdBudgetController {

    private final HouseholdBudgetService budgetService;
    private final HouseholdService householdService;
    private final FamilyPermissionService permissionService;

    public HouseholdBudgetController(HouseholdBudgetService budgetService, HouseholdService householdService,
                                      FamilyPermissionService permissionService) {
        this.budgetService = budgetService;
        this.householdService = householdService;
        this.permissionService = permissionService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return (Long) userId;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            List<HouseholdBudgetResponse> result = budgetService.listForHousehold(id).stream()
                    .map(budgetService::toResponse).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @GetMapping("/category-suggestions")
    public ResponseEntity<?> categorySuggestions(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            return ResponseEntity.ok(budgetService.categorySuggestions(id));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @PathVariable Long id, @RequestBody HouseholdBudgetRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            HouseholdBudgetEntity saved = budgetService.create(household, userId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PutMapping("/{budgetId}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @PathVariable Long budgetId,
                                     @RequestBody HouseholdBudgetRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdBudgetEntity budget = budgetService.requireBudget(budgetId, id);
            HouseholdBudgetEntity saved = budgetService.update(budget, body);
            return ResponseEntity.ok(budgetService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @DeleteMapping("/{budgetId}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id, @PathVariable Long budgetId) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdBudgetEntity budget = budgetService.requireBudget(budgetId, id);
            budgetService.delete(budget);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    private ResponseEntity<?> mapFamilyException(FamilyException e) {
        HttpStatus status = switch (e.getErrorTag()) {
            case "NOT_MEMBER", "NOT_ADMIN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", e.getUserMessage()));
    }
}
