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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdBudgetServiceTest {

    private static final Long HOUSEHOLD_ID = 1L;

    @Mock private HouseholdBudgetRepository budgetRepository;
    @Mock private SharedTransactionRepository sharedTransactionRepository;
    @Mock private UserRepository userRepository;

    private HouseholdBudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new HouseholdBudgetService(budgetRepository, sharedTransactionRepository, userRepository);
    }

    private HouseholdEntity household() {
        HouseholdEntity h = new HouseholdEntity();
        h.setId(HOUSEHOLD_ID);
        return h;
    }

    private HouseholdBudgetEntity budget(Long id, Long householdId, String category, double limit) {
        HouseholdBudgetEntity b = new HouseholdBudgetEntity();
        b.setId(id);
        b.setHouseholdId(householdId);
        b.setCategoryName(category);
        b.setMonthlyLimit(limit);
        b.setCreatedByUserId(1L);
        return b;
    }

    private SharedTransactionEntity expense(String category, double amount, LocalDate date) {
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setCategory(category);
        tx.setTotalAmount(amount);
        tx.setExpenseDate(date);
        return tx;
    }

    // ===================== requireBudget =====================

    @Test
    void requireBudgetThrowsNotFoundWhenMissing() {
        when(budgetRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(FamilyException.class, () -> budgetService.requireBudget(9L, HOUSEHOLD_ID));
    }

    @Test
    void requireBudgetThrowsNotFoundWhenItBelongsToADifferentHousehold() {
        HouseholdBudgetEntity other = budget(9L, 999L, "Groceries", 5000.0);
        when(budgetRepository.findById(9L)).thenReturn(Optional.of(other));

        assertThrows(FamilyException.class, () -> budgetService.requireBudget(9L, HOUSEHOLD_ID));
    }

    // ===================== create =====================

    @Test
    void createRejectsABlankCategoryName() {
        HouseholdBudgetRequest req = new HouseholdBudgetRequest("  ", 100.0);
        assertThrows(FamilyException.class, () -> budgetService.create(household(), 1L, req));
    }

    @Test
    void createRejectsANonPositiveMonthlyLimit() {
        HouseholdBudgetRequest req = new HouseholdBudgetRequest("Groceries", 0.0);
        assertThrows(FamilyException.class, () -> budgetService.create(household(), 1L, req));
    }

    @Test
    void createRejectsADuplicateCategoryCaseInsensitively() {
        when(budgetRepository.findByHouseholdIdOrderByCategoryNameAsc(HOUSEHOLD_ID))
                .thenReturn(List.of(budget(1L, HOUSEHOLD_ID, "groceries", 5000.0)));
        HouseholdBudgetRequest req = new HouseholdBudgetRequest("GROCERIES", 6000.0);

        FamilyException ex = assertThrows(FamilyException.class, () -> budgetService.create(household(), 1L, req));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void createSavesANewBudgetForANonDuplicateCategory() {
        when(budgetRepository.findByHouseholdIdOrderByCategoryNameAsc(HOUSEHOLD_ID)).thenReturn(List.of());
        when(budgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HouseholdBudgetEntity saved = budgetService.create(household(), 7L, new HouseholdBudgetRequest("  Utilities  ", 2000.0));

        assertEquals("Utilities", saved.getCategoryName());
        assertEquals(2000.0, saved.getMonthlyLimit());
        assertEquals(7L, saved.getCreatedByUserId());
        assertEquals(HOUSEHOLD_ID, saved.getHouseholdId());
    }

    // ===================== update =====================

    @Test
    void updateRejectsANonPositiveLimit() {
        HouseholdBudgetEntity existing = budget(1L, HOUSEHOLD_ID, "Groceries", 5000.0);
        assertThrows(FamilyException.class, () -> budgetService.update(existing, new HouseholdBudgetRequest("Groceries", -1.0)));
    }

    @Test
    void updateSavesTheNewMonthlyLimit() {
        HouseholdBudgetEntity existing = budget(1L, HOUSEHOLD_ID, "Groceries", 5000.0);
        when(budgetRepository.save(existing)).thenReturn(existing);

        HouseholdBudgetEntity result = budgetService.update(existing, new HouseholdBudgetRequest("Groceries", 8000.0));

        assertEquals(8000.0, result.getMonthlyLimit());
    }

    // ===================== delete / list =====================

    @Test
    void deleteDelegatesToTheRepository() {
        HouseholdBudgetEntity existing = budget(1L, HOUSEHOLD_ID, "Groceries", 5000.0);
        budgetService.delete(existing);
        verify(budgetRepository).delete(existing);
    }

    // ===================== categorySuggestions =====================

    @Test
    void categorySuggestionsReturnsDistinctTrimmedNonBlankCategoriesOnly() {
        when(sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(HOUSEHOLD_ID)).thenReturn(List.of(
                expense("Groceries", 100.0, LocalDate.now()),
                expense("  Groceries  ", 50.0, LocalDate.now()),
                expense("Utilities", 30.0, LocalDate.now()),
                expense("", 10.0, LocalDate.now()),
                expense(null, 10.0, LocalDate.now())
        ));

        List<String> suggestions = budgetService.categorySuggestions(HOUSEHOLD_ID);

        // Distinct-after-trim categories in the fixture above: "Groceries" and "Utilities" (the
        // "  Groceries  " entry collapses into the same "Groceries" as the first) — "" and null are
        // excluded entirely, so 2, not 3.
        assertEquals(2, suggestions.size(), "duplicate (after trim) and blank/null categories must be excluded");
        assertTrue(suggestions.contains("Groceries"));
        assertTrue(suggestions.contains("Utilities"));
    }

    // ===================== toResponse =====================

    @Test
    void toResponseComputesOnTrackStatusWhenSpendingIsWellBelowTheLimit() {
        HouseholdBudgetEntity b = budget(1L, HOUSEHOLD_ID, "Groceries", 1000.0);
        YearMonth month = YearMonth.now();
        when(sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(HOUSEHOLD_ID, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(List.of(expense("Groceries", 300.0, LocalDate.now())));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        HouseholdBudgetResponse response = budgetService.toResponse(b);

        assertEquals(300.0, response.spentThisMonth());
        assertEquals(30.0, response.percentUsed());
        assertEquals("ON_TRACK", response.status());
    }

    @Test
    void toResponseFlagsNearLimitAtEightyPercentUsage() {
        HouseholdBudgetEntity b = budget(1L, HOUSEHOLD_ID, "Groceries", 1000.0);
        YearMonth month = YearMonth.now();
        when(sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(HOUSEHOLD_ID, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(List.of(expense("Groceries", 800.0, LocalDate.now())));

        HouseholdBudgetResponse response = budgetService.toResponse(b);

        assertEquals("NEAR_LIMIT", response.status());
    }

    @Test
    void toResponseFlagsExceededOnceSpendingReachesTheLimit() {
        HouseholdBudgetEntity b = budget(1L, HOUSEHOLD_ID, "Groceries", 1000.0);
        YearMonth month = YearMonth.now();
        when(sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(HOUSEHOLD_ID, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(List.of(expense("Groceries", 1000.0, LocalDate.now())));

        HouseholdBudgetResponse response = budgetService.toResponse(b);

        assertEquals("EXCEEDED", response.status());
    }

    @Test
    void toResponseOnlySumsExpensesMatchingTheBudgetsCategoryCaseInsensitively() {
        HouseholdBudgetEntity b = budget(1L, HOUSEHOLD_ID, "Groceries", 1000.0);
        YearMonth month = YearMonth.now();
        when(sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(HOUSEHOLD_ID, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(List.of(
                        expense("GROCERIES", 200.0, LocalDate.now()),
                        expense("Utilities", 500.0, LocalDate.now())
                ));

        HouseholdBudgetResponse response = budgetService.toResponse(b);

        assertEquals(200.0, response.spentThisMonth(), "expenses in an unrelated category must not count toward this budget");
    }

    @Test
    void toResponseIncludesTheCreatorsFullNameWhenResolvable() {
        HouseholdBudgetEntity b = budget(1L, HOUSEHOLD_ID, "Groceries", 1000.0);
        UserEntity creator = new UserEntity();
        creator.setId(1L);
        creator.setFullName("Bob");
        when(sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(any(), any(), any())).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));

        HouseholdBudgetResponse response = budgetService.toResponse(b);

        assertEquals("Bob", response.createdByName());
    }
}
