package org.example.finzin.family;

import org.example.finzin.entity.HouseholdBudgetEntity;
import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.family.dto.HouseholdBudgetRequest;
import org.example.finzin.family.dto.HouseholdBudgetResponse;
import org.example.finzin.repository.HouseholdBudgetRepository;
import org.example.finzin.repository.SharedTransactionRepository;
import org.example.finzin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/** A household-wide monthly spending cap on a category, measured live against the shared-expenses
 * ledger ({@link SharedExpenseService}) — never touches any member's personal budgets. */
@Service
public class HouseholdBudgetService {

    private final HouseholdBudgetRepository budgetRepository;
    private final SharedTransactionRepository sharedTransactionRepository;
    private final UserRepository userRepository;

    public HouseholdBudgetService(HouseholdBudgetRepository budgetRepository, SharedTransactionRepository sharedTransactionRepository,
                                   UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.sharedTransactionRepository = sharedTransactionRepository;
        this.userRepository = userRepository;
    }

    public HouseholdBudgetEntity requireBudget(Long id, Long householdId) {
        HouseholdBudgetEntity budget = budgetRepository.findById(id).orElseThrow(() -> FamilyException.notFound("Household budget"));
        if (!budget.getHouseholdId().equals(householdId)) throw FamilyException.notFound("Household budget");
        return budget;
    }

    public HouseholdBudgetEntity create(HouseholdEntity household, Long creatorUserId, HouseholdBudgetRequest request) {
        String categoryName = validateAndNormalize(request);
        boolean duplicate = budgetRepository.findByHouseholdIdOrderByCategoryNameAsc(household.getId()).stream()
                .anyMatch(b -> b.getCategoryName().trim().equalsIgnoreCase(categoryName));
        if (duplicate) throw FamilyException.conflict("A household budget for \"" + categoryName + "\" already exists.");

        HouseholdBudgetEntity budget = new HouseholdBudgetEntity();
        budget.setHouseholdId(household.getId());
        budget.setCategoryName(categoryName);
        budget.setMonthlyLimit(request.monthlyLimit());
        budget.setCreatedByUserId(creatorUserId);
        return budgetRepository.save(budget);
    }

    public HouseholdBudgetEntity update(HouseholdBudgetEntity budget, HouseholdBudgetRequest request) {
        if (request == null || request.monthlyLimit() == null || request.monthlyLimit() <= 0) {
            throw FamilyException.badRequest("monthlyLimit must be a positive number");
        }
        budget.setMonthlyLimit(request.monthlyLimit());
        return budgetRepository.save(budget);
    }

    public void delete(HouseholdBudgetEntity budget) {
        budgetRepository.delete(budget);
    }

    public List<HouseholdBudgetEntity> listForHousehold(Long householdId) {
        return budgetRepository.findByHouseholdIdOrderByCategoryNameAsc(householdId);
    }

    /** Distinct category names already seen in this household's shared expenses — used to
     * autocomplete the budget-creation form instead of leaving it pure freeform. */
    public List<String> categorySuggestions(Long householdId) {
        return sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(householdId).stream()
                .map(SharedTransactionEntity::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    public HouseholdBudgetResponse toResponse(HouseholdBudgetEntity budget) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        double spent = sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(budget.getHouseholdId(), start, end).stream()
                .filter(tx -> tx.getCategory() != null && tx.getCategory().trim().equalsIgnoreCase(budget.getCategoryName().trim()))
                .mapToDouble(SharedTransactionEntity::getTotalAmount)
                .sum();
        double percentUsed = budget.getMonthlyLimit() > 0 ? round2(spent / budget.getMonthlyLimit() * 100.0) : 0.0;
        String status = spent >= budget.getMonthlyLimit() ? "EXCEEDED" : (percentUsed >= 80 ? "NEAR_LIMIT" : "ON_TRACK");
        UserEntity creator = userRepository.findById(budget.getCreatedByUserId()).orElse(null);
        return new HouseholdBudgetResponse(budget.getId(), budget.getHouseholdId(), budget.getCategoryName(),
                budget.getMonthlyLimit(), round2(spent), percentUsed, status,
                budget.getCreatedByUserId(), creator != null ? creator.getFullName() : null);
    }

    private String validateAndNormalize(HouseholdBudgetRequest request) {
        if (request == null || request.categoryName() == null || request.categoryName().isBlank()) {
            throw FamilyException.badRequest("categoryName is required");
        }
        if (request.monthlyLimit() == null || request.monthlyLimit() <= 0) {
            throw FamilyException.badRequest("monthlyLimit must be a positive number");
        }
        return request.categoryName().trim();
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
