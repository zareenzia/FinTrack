package org.example.finzin.service;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.BudgetTemplateCategoryEntity;
import org.example.finzin.entity.BudgetTemplateEntity;
import org.example.finzin.repository.BudgetTemplateCategoryRepository;
import org.example.finzin.repository.BudgetTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for BudgetTemplateService: ownership lookups, the save/delete lifecycle
 * of a template's category rows, and applyTemplate's seeding of a brand-new BudgetPlan from a
 * saved template (delegating the actual category/savings budget upserts to BudgetPlanService).
 */
@ExtendWith(MockitoExtension.class)
class BudgetTemplateServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private BudgetTemplateRepository budgetTemplateRepository;
    @Mock private BudgetTemplateCategoryRepository budgetTemplateCategoryRepository;
    @Mock private BudgetPlanService budgetPlanService;

    private BudgetTemplateService service;

    @BeforeEach
    void setUp() {
        service = new BudgetTemplateService(budgetTemplateRepository, budgetTemplateCategoryRepository, budgetPlanService);
    }

    private BudgetTemplateEntity template(Long id, Long userId, String name) {
        BudgetTemplateEntity t = new BudgetTemplateEntity();
        t.setId(id);
        t.setUserId(userId);
        t.setName(name);
        t.setPlannedIncome(1000.0);
        t.setPlannedSavings(200.0);
        t.setNotes("some notes");
        return t;
    }

    private BudgetTemplateCategoryEntity row(Long id, Long templateId, Long categoryId, double amount, boolean isSavings) {
        BudgetTemplateCategoryEntity r = new BudgetTemplateCategoryEntity();
        r.setId(id);
        r.setTemplateId(templateId);
        r.setCategoryId(categoryId);
        r.setPlannedAmount(amount);
        r.setIsSavings(isSavings);
        return r;
    }

    // ================================================================================
    // listForUser / findOwnedById / getRows
    // ================================================================================

    @Test
    void listForUserDelegatesToRepository() {
        List<BudgetTemplateEntity> list = List.of(template(1L, USER_ID, "Standard"));
        when(budgetTemplateRepository.findByUserId(USER_ID)).thenReturn(list);

        assertSame(list, service.listForUser(USER_ID));
    }

    @Test
    void findOwnedByIdReturnsNullWhenNotFound() {
        when(budgetTemplateRepository.findById(1L)).thenReturn(Optional.empty());

        assertNull(service.findOwnedById(1L, USER_ID));
    }

    @Test
    void findOwnedByIdReturnsNullWhenOwnedByAnotherUser() {
        BudgetTemplateEntity t = template(1L, 999L, "Standard");
        when(budgetTemplateRepository.findById(1L)).thenReturn(Optional.of(t));

        assertNull(service.findOwnedById(1L, USER_ID));
    }

    @Test
    void findOwnedByIdReturnsEntityWhenOwned() {
        BudgetTemplateEntity t = template(1L, USER_ID, "Standard");
        when(budgetTemplateRepository.findById(1L)).thenReturn(Optional.of(t));

        assertSame(t, service.findOwnedById(1L, USER_ID));
    }

    @Test
    void getRowsDelegatesToRepository() {
        List<BudgetTemplateCategoryEntity> rows = List.of(row(1L, 5L, 10L, 100.0, false));
        when(budgetTemplateCategoryRepository.findByTemplateId(5L)).thenReturn(rows);

        assertSame(rows, service.getRows(5L));
    }

    // ================================================================================
    // save
    // ================================================================================

    @Test
    void saveDeletesExistingRowsAndSavesEachNewRowWithNullIdAndTemplateId() {
        BudgetTemplateEntity t = template(null, USER_ID, "Standard");
        when(budgetTemplateRepository.save(t)).thenAnswer(inv -> {
            BudgetTemplateEntity saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        BudgetTemplateCategoryEntity r1 = row(123L, null, 10L, 100.0, false);
        BudgetTemplateCategoryEntity r2 = row(456L, null, 11L, 50.0, true);

        BudgetTemplateEntity result = service.save(t, new ArrayList<>(List.of(r1, r2)));

        assertEquals(9L, result.getId());
        verify(budgetTemplateCategoryRepository).deleteByTemplateId(9L);
        assertNull(r1.getId(), "row id must be cleared before insert so it's treated as new");
        assertEquals(9L, r1.getTemplateId());
        assertNull(r2.getId());
        assertEquals(9L, r2.getTemplateId());
        verify(budgetTemplateCategoryRepository).save(r1);
        verify(budgetTemplateCategoryRepository).save(r2);
    }

    @Test
    void saveHandlesNullRowsListWithoutThrowing() {
        BudgetTemplateEntity t = template(9L, USER_ID, "Standard");
        when(budgetTemplateRepository.save(t)).thenReturn(t);

        BudgetTemplateEntity result = service.save(t, null);

        assertSame(t, result);
        verify(budgetTemplateCategoryRepository).deleteByTemplateId(9L);
        verify(budgetTemplateCategoryRepository, never()).save(any());
    }

    // ================================================================================
    // delete
    // ================================================================================

    @Test
    void deleteRemovesRowsThenTheTemplateItself() {
        service.delete(9L);

        verify(budgetTemplateCategoryRepository).deleteByTemplateId(9L);
        verify(budgetTemplateRepository).deleteById(9L);
    }

    // ================================================================================
    // applyTemplate
    // ================================================================================

    @Test
    void applyTemplateUsesProvidedNameWhenNotBlank() {
        BudgetTemplateEntity t = template(1L, USER_ID, "Standard Template");
        when(budgetTemplateCategoryRepository.findByTemplateId(1L)).thenReturn(List.of());
        when(budgetPlanService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetPlanEntity result = service.applyTemplate(t, "August Plan", "MONTH", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals("August Plan", result.getName());
    }

    @Test
    void applyTemplateFallsBackToTemplateNameWhenNameBlank() {
        BudgetTemplateEntity t = template(1L, USER_ID, "Standard Template");
        when(budgetTemplateCategoryRepository.findByTemplateId(1L)).thenReturn(List.of());
        when(budgetPlanService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetPlanEntity result = service.applyTemplate(t, "  ", "MONTH", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals("Standard Template", result.getName());
    }

    @Test
    void applyTemplateCopiesUserIdIncomeSavingsNotesAndSetsStatusActive() {
        BudgetTemplateEntity t = template(1L, USER_ID, "Standard Template");
        when(budgetTemplateCategoryRepository.findByTemplateId(1L)).thenReturn(List.of());
        when(budgetPlanService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetPlanEntity result = service.applyTemplate(t, "August Plan", "MONTH", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(USER_ID, result.getUserId());
        assertEquals(1000.0, result.getPlannedIncome());
        assertEquals(200.0, result.getPlannedSavings());
        assertEquals("some notes", result.getNotes());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("MONTH", result.getPeriodType());
        assertEquals("2026-08", result.getPeriod());
        assertEquals(LocalDate.of(2026, 8, 1), result.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), result.getEndDate());
    }

    @Test
    void applyTemplateUpsertsSavingsBudgetForSavingsRowsAndCategoryBudgetForExpenseRows() {
        BudgetTemplateEntity t = template(1L, USER_ID, "Standard Template");
        BudgetTemplateCategoryEntity expenseRow = row(1L, 1L, 10L, 300.0, false);
        BudgetTemplateCategoryEntity savingsRow = row(2L, 1L, 20L, 150.0, true);
        when(budgetTemplateCategoryRepository.findByTemplateId(1L)).thenReturn(List.of(expenseRow, savingsRow));
        when(budgetPlanService.save(any())).thenAnswer(inv -> {
            BudgetPlanEntity plan = inv.getArgument(0);
            plan.setId(99L);
            return plan;
        });

        BudgetPlanEntity result = service.applyTemplate(t, "August Plan", "MONTH", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        verify(budgetPlanService).upsertCategoryBudget(result, 10L, 300.0);
        verify(budgetPlanService).upsertSavingsBudget(result, 20L, 150.0);
        verify(budgetPlanService, never()).upsertCategoryBudget(result, 20L, 150.0);
        verify(budgetPlanService, never()).upsertSavingsBudget(result, 10L, 300.0);
    }
}
