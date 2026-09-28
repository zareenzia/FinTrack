package org.example.finzin.service;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.BudgetEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.SavingsBudgetEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.BudgetPlanRepository;
import org.example.finzin.repository.BudgetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.SavingsBudgetRepository;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching AccountBalanceServiceTest/AIServiceTest's convention) for
 * BudgetPlanService, the most business-logic-dense class in the Budgets & Financial Planner slice:
 * category/savings status computation, budget scoring, duplicate/copy-previous-period, and
 * threshold notification triggering.
 */
@ExtendWith(MockitoExtension.class)
class BudgetPlanServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private BudgetPlanRepository budgetPlanRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private SavingsBudgetRepository savingsBudgetRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private NotificationService notificationService;
    @Mock private DocumentIndexer documentIndexer;

    private BudgetPlanService service;

    @BeforeEach
    void setUp() {
        service = new BudgetPlanService(budgetPlanRepository, budgetRepository, savingsBudgetRepository,
                categoryRepository, transactionRepository, accountRepository, notificationService, documentIndexer);
    }

    // ================================================================================
    // Helpers
    // ================================================================================

    /**
     * periodType is set to "MONTH" but period is deliberately an unparsable string, so
     * BudgetPlanService's private periodStart/periodEnd helpers fall into their catch-block
     * fallback and use the raw startDate/endDate below directly. That gives each test full,
     * deterministic control over the plan's effective date range without needing a Clock seam
     * on the service (there isn't one) to control "today".
     */
    private BudgetPlanEntity planWithRange(Long id, LocalDate start, LocalDate end) {
        BudgetPlanEntity p = new BudgetPlanEntity();
        p.setId(id);
        p.setUserId(USER_ID);
        p.setName("Plan " + id);
        p.setPeriodType("MONTH");
        p.setPeriod("not-a-year-month");
        p.setStartDate(start);
        p.setEndDate(end);
        p.setPlannedIncome(0.0);
        p.setPlannedSavings(0.0);
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    /** First/last day of the current month — for status-computation tests that need a plan period
     *  that's never in the past (which would make computeCategoryStatuses report COMPLETED instead
     *  of ON_TRACK/NEAR_LIMIT/OVER_BUDGET) regardless of what date this suite actually runs on. */
    private LocalDate currentMonthStart() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate currentMonthEnd() {
        LocalDate now = LocalDate.now();
        return now.withDayOfMonth(now.lengthOfMonth());
    }

    /** A plan whose period genuinely parses (real MONTH/QUARTER/YEAR logic) and covers "today", for getPlanForDate tests. */
    private BudgetPlanEntity planForOverlapping(Long id, String periodType, LocalDate today) {
        BudgetPlanEntity p = new BudgetPlanEntity();
        p.setId(id);
        p.setUserId(USER_ID);
        p.setName("Plan " + id);
        p.setPeriodType(periodType);
        switch (periodType) {
            case "MONTH" -> p.setPeriod(java.time.YearMonth.from(today).toString());
            case "QUARTER" -> {
                int q = (today.getMonthValue() - 1) / 3 + 1;
                p.setPeriod(today.getYear() + "-Q" + q);
            }
            case "YEAR" -> p.setPeriod(String.valueOf(today.getYear()));
            default -> p.setPeriod("2026-01");
        }
        p.setStartDate(today.minusDays(1));
        p.setEndDate(today.plusDays(1));
        p.setPlannedIncome(0.0);
        p.setPlannedSavings(0.0);
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    private BudgetEntity budget(Long id, Long planId, Long categoryId, double amount) {
        BudgetEntity b = new BudgetEntity();
        b.setId(id);
        b.setUserId(USER_ID);
        b.setBudgetPlanId(planId);
        b.setCategoryId(categoryId);
        b.setPeriod("2026-07");
        b.setBudgetAmount(amount);
        return b;
    }

    private SavingsBudgetEntity savings(Long id, Long planId, Long categoryId, double target, double initial,
                                         Long storageAccountId, Long sourceAccountId, String sourceDescription) {
        SavingsBudgetEntity s = new SavingsBudgetEntity();
        s.setId(id);
        s.setBudgetPlanId(planId);
        s.setCategoryId(categoryId);
        s.setTargetAmount(target);
        s.setInitialAmount(initial);
        s.setStorageAccountId(storageAccountId);
        s.setSourceAccountId(sourceAccountId);
        s.setSourceDescription(sourceDescription);
        return s;
    }

    private CategoryEntity category(Long id, String name) {
        CategoryEntity c = new CategoryEntity();
        c.setId(id);
        c.setUserId(USER_ID);
        c.setName(name);
        c.setColor("#ff0000");
        c.setIcon("tag");
        return c;
    }

    private Map<String, Object> categoryStatus(double budgetAmount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetAmount", budgetAmount);
        return m;
    }

    private Map<String, Object> summaryMap(double actualIncome, double plannedIncome) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plannedIncome", plannedIncome);
        m.put("actualIncome", actualIncome);
        return m;
    }

    private Map<String, Object> categoryPercent(double percentUsed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("percentUsed", percentUsed);
        return m;
    }

    private Map<String, Object> savingsStatusMap(double targetAmount, double currentAmount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetAmount", targetAmount);
        m.put("currentAmount", currentAmount);
        return m;
    }

    // ================================================================================
    // listForUser
    // ================================================================================

    @Test
    void listForUserFiltersByExactPeriodMatch() {
        BudgetPlanEntity a = planWithRange(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        a.setPeriod("2026-01");
        BudgetPlanEntity b = planWithRange(2L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        b.setPeriod("2026-02");
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>(List.of(a, b)));

        List<BudgetPlanEntity> result = service.listForUser(USER_ID, "2026-02", null, null, null);

        assertEquals(List.of(b), result);
    }

    @Test
    void listForUserFiltersByStatusCaseInsensitively() {
        BudgetPlanEntity a = planWithRange(1L, LocalDate.now(), LocalDate.now());
        a.setStatus("ACTIVE");
        BudgetPlanEntity b = planWithRange(2L, LocalDate.now(), LocalDate.now());
        b.setStatus("ARCHIVED");
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>(List.of(a, b)));

        List<BudgetPlanEntity> result = service.listForUser(USER_ID, null, "archived", null, null);

        assertEquals(List.of(b), result);
    }

    @Test
    void listForUserFiltersBySearchTermCaseInsensitiveSubstringOfName() {
        BudgetPlanEntity a = planWithRange(1L, LocalDate.now(), LocalDate.now());
        a.setName("Vacation Fund");
        BudgetPlanEntity b = planWithRange(2L, LocalDate.now(), LocalDate.now());
        b.setName("Groceries");
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>(List.of(a, b)));

        List<BudgetPlanEntity> result = service.listForUser(USER_ID, null, null, "vacation", null);

        assertEquals(List.of(a), result);
    }

    @Test
    void listForUserDefaultSortIsStartDateDescending() {
        BudgetPlanEntity a = planWithRange(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        BudgetPlanEntity b = planWithRange(2L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>(List.of(a, b)));

        List<BudgetPlanEntity> result = service.listForUser(USER_ID, null, null, null, null);

        assertEquals(List.of(b, a), result, "default sort must be startDate descending (most recent first)");
    }

    @Test
    void listForUserSortsByNameCaseInsensitiveWhenRequested() {
        BudgetPlanEntity a = planWithRange(1L, LocalDate.now(), LocalDate.now());
        a.setName("zeta");
        BudgetPlanEntity b = planWithRange(2L, LocalDate.now(), LocalDate.now());
        b.setName("Alpha");
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>(List.of(a, b)));

        List<BudgetPlanEntity> result = service.listForUser(USER_ID, null, null, null, "name");

        assertEquals(List.of(b, a), result);
    }

    @Test
    void listForUserSortsByStartDateAscendingWhenRequested() {
        BudgetPlanEntity a = planWithRange(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        BudgetPlanEntity b = planWithRange(2L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>(List.of(a, b)));

        List<BudgetPlanEntity> result = service.listForUser(USER_ID, null, null, null, "startDate");

        assertEquals(List.of(b, a), result);
    }

    // ================================================================================
    // findOwnedById
    // ================================================================================

    @Test
    void findOwnedByIdReturnsNullWhenNotFound() {
        when(budgetPlanRepository.findById(1L)).thenReturn(Optional.empty());
        assertNull(service.findOwnedById(1L, USER_ID));
    }

    @Test
    void findOwnedByIdReturnsNullWhenOwnedByAnotherUser() {
        BudgetPlanEntity p = planWithRange(1L, LocalDate.now(), LocalDate.now());
        p.setUserId(999L);
        when(budgetPlanRepository.findById(1L)).thenReturn(Optional.of(p));
        assertNull(service.findOwnedById(1L, USER_ID));
    }

    @Test
    void findOwnedByIdReturnsEntityWhenOwned() {
        BudgetPlanEntity p = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(budgetPlanRepository.findById(1L)).thenReturn(Optional.of(p));
        assertSame(p, service.findOwnedById(1L, USER_ID));
    }

    // ================================================================================
    // getPlanForDate / getCurrentPlan
    // ================================================================================

    @Test
    void getPlanForDatePrefersMonthOverQuarterAndYearWhenAllOverlap() {
        LocalDate today = LocalDate.now();
        BudgetPlanEntity monthPlan = planForOverlapping(1L, "MONTH", today);
        BudgetPlanEntity quarterPlan = planForOverlapping(2L, "QUARTER", today);
        BudgetPlanEntity yearPlan = planForOverlapping(3L, "YEAR", today);
        when(budgetPlanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE"))
                .thenReturn(List.of(quarterPlan, yearPlan, monthPlan));

        BudgetPlanEntity result = service.getPlanForDate(USER_ID, today);

        assertEquals(1L, result.getId(), "MONTH must win over QUARTER/YEAR when all three overlap the date");
    }

    @Test
    void getPlanForDateReturnsNullWhenNoActivePlanCoversTheDate() {
        when(budgetPlanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());
        assertNull(service.getPlanForDate(USER_ID, LocalDate.now()));
    }

    @Test
    void getPlanForDatePicksMostRecentlyCreatedWhenSameSpecificityOverlaps() {
        LocalDate today = LocalDate.now();
        BudgetPlanEntity older = planForOverlapping(1L, "MONTH", today);
        older.setCreatedAt(LocalDateTime.now().minusDays(5));
        BudgetPlanEntity newer = planForOverlapping(2L, "MONTH", today);
        newer.setCreatedAt(LocalDateTime.now());
        when(budgetPlanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(older, newer));

        BudgetPlanEntity result = service.getPlanForDate(USER_ID, today);

        assertEquals(2L, result.getId());
    }

    @Test
    void getCurrentPlanDelegatesToGetPlanForDateWithToday() {
        LocalDate today = LocalDate.now();
        BudgetPlanEntity p = planForOverlapping(1L, "MONTH", today);
        when(budgetPlanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(p));

        assertEquals(1L, service.getCurrentPlan(USER_ID).getId());
    }

    // ================================================================================
    // validate
    // ================================================================================

    @Test
    void validateRejectsBlankOrNullName() {
        assertEquals("Budget name is required", service.validate("", "MONTH", "2026-07", LocalDate.now(), LocalDate.now()));
        assertEquals("Budget name is required", service.validate(null, "MONTH", "2026-07", LocalDate.now(), LocalDate.now()));
    }

    @Test
    void validateRejectsInvalidOrNullPeriodType() {
        assertEquals("periodType must be MONTH, QUARTER, or YEAR",
                service.validate("Name", "WEEK", "2026-07", LocalDate.now(), LocalDate.now()));
        assertEquals("periodType must be MONTH, QUARTER, or YEAR",
                service.validate("Name", null, "2026-07", LocalDate.now(), LocalDate.now()));
    }

    @Test
    void validateRejectsBlankPeriod() {
        assertEquals("period is required", service.validate("Name", "MONTH", "", LocalDate.now(), LocalDate.now()));
    }

    @Test
    void validateRejectsMissingDates() {
        assertEquals("startDate and endDate are required", service.validate("Name", "MONTH", "2026-07", null, LocalDate.now()));
        assertEquals("startDate and endDate are required", service.validate("Name", "MONTH", "2026-07", LocalDate.now(), null));
    }

    @Test
    void validateRejectsEndDateBeforeStartDate() {
        assertEquals("endDate must be on or after startDate",
                service.validate("Name", "MONTH", "2026-07", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1)));
    }

    @Test
    void validateAcceptsValidInputCaseInsensitively() {
        assertNull(service.validate("Name", "month", "2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
    }

    // ================================================================================
    // save / archive / delete
    // ================================================================================

    @Test
    void saveSavesAndIndexes() {
        BudgetPlanEntity p = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(budgetPlanRepository.save(p)).thenReturn(p);

        BudgetPlanEntity result = service.save(p);

        assertSame(p, result);
        verify(documentIndexer).indexBudgetPlan(p);
    }

    @Test
    void archiveSetsStatusAndSavesAndIndexes() {
        BudgetPlanEntity p = planWithRange(1L, LocalDate.now(), LocalDate.now());
        p.setStatus("ACTIVE");

        service.archive(p);

        assertEquals("ARCHIVED", p.getStatus());
        verify(budgetPlanRepository).save(p);
        verify(documentIndexer).indexBudgetPlan(p);
    }

    @Test
    void deleteRemovesCategoryAndSavingsBudgetsThenThePlanAndDeindexes() {
        BudgetPlanEntity p = planWithRange(5L, LocalDate.now(), LocalDate.now());
        BudgetEntity b1 = budget(10L, 5L, 1L, 100);
        BudgetEntity b2 = budget(11L, 5L, 2L, 200);
        SavingsBudgetEntity s1 = savings(20L, 5L, 3L, 500, 0, null, null, null);
        when(budgetRepository.findByBudgetPlanId(5L)).thenReturn(List.of(b1, b2));
        when(savingsBudgetRepository.findByBudgetPlanId(5L)).thenReturn(List.of(s1));

        service.delete(p);

        verify(budgetRepository).deleteById(10L);
        verify(budgetRepository).deleteById(11L);
        verify(savingsBudgetRepository).deleteById(20L);
        verify(budgetPlanRepository).deleteById(5L);
        verify(documentIndexer).deleteBudgetPlan(USER_ID, 5L);
    }

    // ================================================================================
    // duplicate
    // ================================================================================

    @Test
    void duplicateCopiesFieldsAndCategoriesAndSavingsWithNewName() {
        BudgetPlanEntity source = planWithRange(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        source.setName("January");
        source.setPlannedIncome(1000.0);
        source.setPlannedSavings(200.0);
        source.setNotes("notes");
        BudgetEntity cb = budget(10L, 1L, 3L, 300);
        SavingsBudgetEntity sb = savings(20L, 1L, 4L, 500, 50, 7L, null, "Bonus");
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(cb));
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(sb));
        when(budgetPlanRepository.save(any())).thenAnswer(inv -> {
            BudgetPlanEntity e = inv.getArgument(0);
            e.setId(2L);
            return e;
        });

        BudgetPlanEntity copy = service.duplicate(source, "February Copy", "MONTH", "2026-02",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertEquals("February Copy", copy.getName());
        assertEquals("ACTIVE", copy.getStatus());
        assertEquals(1000.0, copy.getPlannedIncome());
        assertEquals(200.0, copy.getPlannedSavings());
        assertEquals(USER_ID, copy.getUserId());

        ArgumentCaptor<BudgetEntity> budgetCaptor = ArgumentCaptor.forClass(BudgetEntity.class);
        verify(budgetRepository).save(budgetCaptor.capture());
        assertEquals(2L, budgetCaptor.getValue().getBudgetPlanId());
        assertEquals(3L, budgetCaptor.getValue().getCategoryId());
        assertEquals(300.0, budgetCaptor.getValue().getBudgetAmount());

        ArgumentCaptor<SavingsBudgetEntity> savingsCaptor = ArgumentCaptor.forClass(SavingsBudgetEntity.class);
        verify(savingsBudgetRepository).save(savingsCaptor.capture());
        assertEquals(2L, savingsCaptor.getValue().getBudgetPlanId());
        assertEquals(7L, savingsCaptor.getValue().getStorageAccountId());

        verify(documentIndexer).indexBudgetPlan(copy);
    }

    @Test
    void duplicateFallsBackToSourceNamePlusCopySuffixWhenNewNameBlank() {
        BudgetPlanEntity source = planWithRange(1L, LocalDate.now(), LocalDate.now());
        source.setName("January");
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of());
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of());
        when(budgetPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetPlanEntity copy = service.duplicate(source, "  ", "MONTH", "2026-02", LocalDate.now(), LocalDate.now());

        assertEquals("January (Copy)", copy.getName());
    }

    // ================================================================================
    // copyFromPreviousPlan
    // ================================================================================

    @Test
    void copyFromPreviousPlanReturnsUnchangedWhenNoPriorPlanExists() {
        BudgetPlanEntity newPlan = planWithRange(2L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of());

        BudgetPlanEntity result = service.copyFromPreviousPlan(newPlan);

        assertSame(newPlan, result);
        verify(budgetPlanRepository, never()).save(any());
    }

    @Test
    void copyFromPreviousPlanCopiesIncomeSavingsAndAllocationsFromMostRecentPriorPlan() {
        BudgetPlanEntity newPlan = planWithRange(3L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        BudgetPlanEntity olderPrior = planWithRange(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        olderPrior.setPlannedIncome(500.0);
        BudgetPlanEntity mostRecentPrior = planWithRange(2L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        mostRecentPrior.setPlannedIncome(1000.0);
        mostRecentPrior.setPlannedSavings(300.0);
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of(olderPrior, mostRecentPrior, newPlan));
        when(budgetRepository.findByBudgetPlanId(2L)).thenReturn(List.of());
        when(savingsBudgetRepository.findByBudgetPlanId(2L)).thenReturn(List.of());

        BudgetPlanEntity result = service.copyFromPreviousPlan(newPlan);

        assertEquals(1000.0, result.getPlannedIncome(), "must copy from the most recent prior plan (Feb), not an older one (Jan)");
        assertEquals(300.0, result.getPlannedSavings());
        verify(budgetPlanRepository).save(newPlan);
        verify(documentIndexer).indexBudgetPlan(newPlan);
    }

    @Test
    void copyFromPreviousPlanExcludesPlansThatDoNotEndBeforeTheNewPlanStarts() {
        BudgetPlanEntity newPlan = planWithRange(2L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        BudgetPlanEntity overlapping = planWithRange(1L, LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of(overlapping, newPlan));

        BudgetPlanEntity result = service.copyFromPreviousPlan(newPlan);

        assertSame(newPlan, result);
        verify(budgetPlanRepository, never()).save(any());
    }

    // ================================================================================
    // upsertCategoryBudget / upsertSavingsBudget
    // ================================================================================

    @Test
    void upsertCategoryBudgetCreatesNewWhenNoneExists() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        plan.setPeriod("2026-07");
        when(budgetRepository.findByBudgetPlanIdAndCategoryId(1L, 5L)).thenReturn(Optional.empty());
        when(budgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetEntity result = service.upsertCategoryBudget(plan, 5L, 300.0);

        assertEquals(USER_ID, result.getUserId());
        assertEquals(1L, result.getBudgetPlanId());
        assertEquals(5L, result.getCategoryId());
        assertEquals("2026-07", result.getPeriod());
        assertEquals(300.0, result.getBudgetAmount());
        verify(documentIndexer).indexBudgetPlan(plan);
    }

    @Test
    void upsertCategoryBudgetUpdatesExistingWhenAlreadyPresent() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        BudgetEntity existing = budget(9L, 1L, 5L, 100.0);
        when(budgetRepository.findByBudgetPlanIdAndCategoryId(1L, 5L)).thenReturn(Optional.of(existing));
        when(budgetRepository.save(existing)).thenReturn(existing);

        BudgetEntity result = service.upsertCategoryBudget(plan, 5L, 999.0);

        assertEquals(9L, result.getId());
        assertEquals(999.0, result.getBudgetAmount());
    }

    @Test
    void upsertSavingsBudgetTwoArgOverloadDefaultsInitialAmountToZeroAndNoAccounts() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(savingsBudgetRepository.findByBudgetPlanIdAndCategoryId(1L, 5L)).thenReturn(Optional.empty());
        when(savingsBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsBudgetEntity result = service.upsertSavingsBudget(plan, 5L, 1000.0);

        assertEquals(0.0, result.getInitialAmount());
        assertNull(result.getStorageAccountId());
        assertNull(result.getSourceAccountId());
        assertNull(result.getSourceDescription());
    }

    @Test
    void upsertSavingsBudgetSourceAccountIdWinsOverSourceDescriptionWhenBothProvided() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(savingsBudgetRepository.findByBudgetPlanIdAndCategoryId(1L, 5L)).thenReturn(Optional.empty());
        when(savingsBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsBudgetEntity result = service.upsertSavingsBudget(plan, 5L, 1000.0, 100.0, 20L, 30L, "Gift from parents");

        assertEquals(30L, result.getSourceAccountId());
        assertNull(result.getSourceDescription(), "a real linked source account must win over a stale free-text description");
        assertEquals(20L, result.getStorageAccountId());
    }

    @Test
    void upsertSavingsBudgetKeepsSourceDescriptionWhenNoSourceAccountGiven() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(savingsBudgetRepository.findByBudgetPlanIdAndCategoryId(1L, 5L)).thenReturn(Optional.empty());
        when(savingsBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsBudgetEntity result = service.upsertSavingsBudget(plan, 5L, 1000.0, 100.0, null, null, "Bonus");

        assertEquals("Bonus", result.getSourceDescription());
        assertNull(result.getSourceAccountId());
    }

    // ================================================================================
    // deleteCategoryBudgetById / deleteSavingsBudgetById
    // ================================================================================

    @Test
    void deleteCategoryBudgetByIdDeletesAndIndexesWhenFound() {
        BudgetEntity b = budget(9L, 1L, 5L, 100);
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(budgetRepository.findById(9L)).thenReturn(Optional.of(b));
        when(budgetPlanRepository.findById(1L)).thenReturn(Optional.of(plan));

        service.deleteCategoryBudgetById(9L);

        verify(budgetRepository).deleteById(9L);
        verify(documentIndexer).indexBudgetPlan(plan);
    }

    @Test
    void deleteCategoryBudgetByIdDoesNothingWhenBudgetNotFound() {
        when(budgetRepository.findById(9L)).thenReturn(Optional.empty());

        service.deleteCategoryBudgetById(9L);

        verify(budgetRepository, never()).deleteById(any());
        verifyNoInteractions(documentIndexer);
    }

    @Test
    void deleteSavingsBudgetByIdDeletesAndIndexesWhenFound() {
        SavingsBudgetEntity s = savings(20L, 1L, 5L, 500, 0, null, null, null);
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(savingsBudgetRepository.findById(20L)).thenReturn(Optional.of(s));
        when(budgetPlanRepository.findById(1L)).thenReturn(Optional.of(plan));

        service.deleteSavingsBudgetById(20L);

        verify(savingsBudgetRepository).deleteById(20L);
        verify(documentIndexer).indexBudgetPlan(plan);
    }

    // ================================================================================
    // computeSummary
    // ================================================================================

    @Test
    void computeSummaryComputesRemainingAndUtilizationWithNullSafeSums() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        plan.setPlannedIncome(10000.0);
        plan.setPlannedSavings(1000.0);
        List<Map<String, Object>> categories = List.of(categoryStatus(300.0));
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("income"), any(), any())).thenReturn(8000.0);
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("expense"), any(), any())).thenReturn(null);
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq("savings"), any(), any())).thenReturn(500.0);

        Map<String, Object> summary = service.computeSummary(plan, categories);

        assertEquals(10000.0, summary.get("plannedIncome"));
        assertEquals(300.0, summary.get("plannedExpense"));
        assertEquals(1000.0, summary.get("plannedSavings"));
        assertEquals(8000.0, summary.get("actualIncome"));
        assertEquals(0.0, summary.get("actualExpense"), "null sum must default to 0");
        assertEquals(500.0, summary.get("actualSavings"));
        assertEquals(10000.0 - 300.0 - 1000.0, summary.get("remaining"));
        assertEquals(((0.0 + 500.0) / 10000.0) * 100, summary.get("utilizationPercent"));
        assertEquals(1, summary.get("activeBudgetsCount"));
    }

    @Test
    void computeSummaryUtilizationIsZeroWhenPlannedIncomeIsZero() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        plan.setPlannedIncome(0.0);
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(100.0);

        Map<String, Object> summary = service.computeSummary(plan, List.of());

        assertEquals(0.0, summary.get("utilizationPercent"));
    }

    // ================================================================================
    // computeCategoryStatuses
    // ================================================================================

    @Test
    void computeCategoryStatusesMarksOverBudgetWithSuggestion() {
        // Must be the current month, not a hardcoded past date, or the plan is classified COMPLETED
        // instead of being scored OVER_BUDGET/NEAR_LIMIT/ON_TRACK.
        BudgetPlanEntity plan = planWithRange(1L, currentMonthStart(), currentMonthEnd());
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        CategoryEntity cat = category(5L, "Dining");
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(cat));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(5L), any(), any()))
                .thenReturn(1500.0);

        List<Map<String, Object>> statuses = service.computeCategoryStatuses(plan);

        Map<String, Object> status = statuses.get(0);
        assertEquals("OVER_BUDGET", status.get("status"));
        assertEquals(-500.0, status.get("remainingAmount"));
        assertEquals(150.0, status.get("percentUsed"));
        assertEquals("Dining is over budget by 500.", status.get("suggestion"));
        assertEquals("Dining", status.get("categoryName"));
    }

    @Test
    void computeCategoryStatusesMarksNearLimitAndFallsBackForMissingCategory() {
        BudgetPlanEntity plan = planWithRange(1L, currentMonthStart(), currentMonthEnd());
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(categoryRepository.findById(5L)).thenReturn(Optional.empty());
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(5L), any(), any()))
                .thenReturn(850.0);

        List<Map<String, Object>> statuses = service.computeCategoryStatuses(plan);

        Map<String, Object> status = statuses.get(0);
        assertEquals("NEAR_LIMIT", status.get("status"));
        assertEquals("Unknown", status.get("categoryName"), "missing category must fall back to Unknown");
        assertEquals("#6c757d", status.get("categoryColor"));
        assertTrue(((String) status.get("suggestion")).contains("85"));
    }

    @Test
    void computeCategoryStatusesMarksOnTrackWhenWellUnderBudgetAndActualSumIsNull() {
        BudgetPlanEntity plan = planWithRange(1L, currentMonthStart(), currentMonthEnd());
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, "Dining")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(5L), any(), any()))
                .thenReturn(null);

        List<Map<String, Object>> statuses = service.computeCategoryStatuses(plan);

        assertEquals("ON_TRACK", statuses.get(0).get("status"));
        assertEquals(0.0, statuses.get(0).get("actualAmount"), "null actual sum must default to 0");
    }

    @Test
    void computeCategoryStatusesMarksCompletedWhenPeriodHasEnded() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, "Dining")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(5L), any(), any()))
                .thenReturn(200.0);

        List<Map<String, Object>> statuses = service.computeCategoryStatuses(plan);

        assertEquals("COMPLETED", statuses.get(0).get("status"),
                "a plan whose period has ended must show COMPLETED even though actual spend is low");
    }

    @Test
    void computeCategoryStatusesSuggestsLowerSpendingWhenSignificantlyUnderElapsedPace() {
        // Period started 20 days ago and ends 10 days from now -> elapsedFraction ~ 0.667 (> 0.5),
        // deterministic regardless of the actual date the suite runs on.
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now().minusDays(20), LocalDate.now().plusDays(10));
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, "Dining")));
        // percentUsed (10%) well under elapsedFraction*100*0.6 (~40%)
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(5L), any(), any()))
                .thenReturn(100.0);

        List<Map<String, Object>> statuses = service.computeCategoryStatuses(plan);

        assertNotNull(statuses.get(0).get("suggestion"));
        assertTrue(((String) statuses.get(0).get("suggestion")).contains("lower than planned"));
    }

    // ================================================================================
    // computeSavingsStatuses
    // ================================================================================

    @Test
    void computeSavingsStatusesComputesCurrentAmountAndAchievedStatusWithAccountNames() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        SavingsBudgetEntity s = savings(20L, 1L, 5L, 1000.0, 200.0, 7L, 8L, null);
        AccountEntity storage = new AccountEntity();
        storage.setId(7L);
        storage.setAccountNickname("Emergency Fund");
        AccountEntity source = new AccountEntity();
        source.setId(8L);
        source.setAccountNickname("Salary");
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(s));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, "Emergency")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("savings"), eq(5L), any(), any()))
                .thenReturn(900.0);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(storage));
        when(accountRepository.findById(8L)).thenReturn(Optional.of(source));

        List<Map<String, Object>> statuses = service.computeSavingsStatuses(plan);

        Map<String, Object> status = statuses.get(0);
        assertEquals(1100.0, status.get("currentAmount"), "current amount = 200 initial + 900 contributed");
        assertEquals("GOAL_ACHIEVED", status.get("status"));
        assertEquals("Emergency Fund", status.get("storageAccountName"));
        assertEquals("Salary", status.get("sourceAccountName"));
    }

    @Test
    void computeSavingsStatusesInProgressWhenBelowTargetWithNoLinkedAccounts() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        SavingsBudgetEntity s = savings(20L, 1L, 5L, 1000.0, 0.0, null, null, "Gift");
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(s));
        when(categoryRepository.findById(5L)).thenReturn(Optional.empty());
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("savings"), eq(5L), any(), any()))
                .thenReturn(null);

        List<Map<String, Object>> statuses = service.computeSavingsStatuses(plan);

        Map<String, Object> status = statuses.get(0);
        assertEquals(0.0, status.get("currentAmount"));
        assertEquals("IN_PROGRESS", status.get("status"));
        assertNull(status.get("storageAccountName"));
        assertEquals("Gift", status.get("sourceDescription"));
    }

    // ================================================================================
    // computeBudgetScore
    // ================================================================================

    @Test
    void computeBudgetScoreGivesMaxScoreWithNoCategoriesOrSavingsAndIncomeAtTarget() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        Map<String, Object> summary = summaryMap(1000.0, 1000.0);

        int score = service.computeBudgetScore(plan, List.of(), List.of(), summary);

        // categoryScore=40 (empty) + savingsScore=30 (empty) + incomeScore=20 (actual==planned) + bonus=10 (no over budget)
        assertEquals(100, score);
    }

    @Test
    void computeBudgetScorePenalizesOverBudgetCategoriesAndRemovesBonus() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        List<Map<String, Object>> categories = List.of(categoryPercent(150.0), categoryPercent(50.0));
        Map<String, Object> summary = summaryMap(1000.0, 1000.0);

        int score = service.computeBudgetScore(plan, categories, List.of(), summary);

        // categoryScore = (1/2)*40=20, savingsScore=30, incomeScore=20, bonus=0 (one category over budget)
        assertEquals(70, score);
    }

    @Test
    void computeBudgetScoreCapsSavingsAchievementAtTargetEvenWhenOverAchieved() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        List<Map<String, Object>> savingsStatuses = List.of(savingsStatusMap(500.0, 1000.0));
        Map<String, Object> summary = summaryMap(1000.0, 1000.0);

        int score = service.computeBudgetScore(plan, List.of(), savingsStatuses, summary);

        assertEquals(100, score, "savingsScore must cap at 30 even though current (1000) exceeds target (500)");
    }

    @Test
    void computeBudgetScoreCapsIncomeScoreAtTwentyWhenActualExceedsPlanned() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        Map<String, Object> summary = summaryMap(2000.0, 1000.0);

        int score = service.computeBudgetScore(plan, List.of(), List.of(), summary);

        assertEquals(100, score, "incomeScore must cap at 20 even when actualIncome exceeds plannedIncome");
    }

    @Test
    void computeBudgetScoreGivesTwentyIncomeScoreWhenPlannedIncomeIsZero() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        Map<String, Object> summary = summaryMap(0.0, 0.0);

        int score = service.computeBudgetScore(plan, List.of(), List.of(), summary);

        assertEquals(100, score);
    }

    // ================================================================================
    // findSavingsBudgetStatus
    // ================================================================================

    @Test
    void findSavingsBudgetStatusReturnsEmptyWhenSavingsNotFound() {
        when(savingsBudgetRepository.findById(20L)).thenReturn(Optional.empty());
        assertTrue(service.findSavingsBudgetStatus(USER_ID, 20L).isEmpty());
    }

    @Test
    void findSavingsBudgetStatusReturnsEmptyWhenOwningPlanBelongsToAnotherUser() {
        SavingsBudgetEntity s = savings(20L, 1L, 5L, 1000, 0, null, null, null);
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        plan.setUserId(999L);
        when(savingsBudgetRepository.findById(20L)).thenReturn(Optional.of(s));
        when(budgetPlanRepository.findById(1L)).thenReturn(Optional.of(plan));

        assertTrue(service.findSavingsBudgetStatus(USER_ID, 20L).isEmpty());
    }

    @Test
    void findSavingsBudgetStatusReturnsComputedStatusWhenOwned() {
        SavingsBudgetEntity s = savings(20L, 1L, 5L, 1000.0, 0.0, null, null, null);
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.now(), LocalDate.now());
        when(savingsBudgetRepository.findById(20L)).thenReturn(Optional.of(s));
        when(budgetPlanRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(s));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, "Emergency")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("savings"), eq(5L), any(), any()))
                .thenReturn(0.0);

        Optional<Map<String, Object>> result = service.findSavingsBudgetStatus(USER_ID, 20L);

        assertTrue(result.isPresent());
        assertEquals(20L, result.get().get("id"));
    }

    // ================================================================================
    // checkAndNotifyThresholds
    // ================================================================================

    @Test
    void checkAndNotifyThresholdsFiresBudgetExceededForOverBudgetCategory() {
        // Must be the current month, not a hardcoded past date, or the plan is classified COMPLETED
        // and checkAndNotifyThresholds never reaches the over-budget notification branch.
        BudgetPlanEntity plan = planWithRange(1L, currentMonthStart(), currentMonthEnd());
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, "Dining")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(5L), any(), any()))
                .thenReturn(1500.0);
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of());

        service.checkAndNotifyThresholds(plan);

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("BUDGET_EXCEEDED"),
                contains("Dining"), contains("over budget"), eq("BUDGET_CATEGORY"), eq(10L));
    }

    @Test
    void checkAndNotifyThresholdsFiresThresholdWarningForNearLimitCategory() {
        BudgetPlanEntity plan = planWithRange(1L, currentMonthStart(), currentMonthEnd());
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, "Dining")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(5L), any(), any()))
                .thenReturn(850.0);
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of());

        service.checkAndNotifyThresholds(plan);

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("BUDGET_THRESHOLD"),
                anyString(), anyString(), eq("BUDGET_CATEGORY"), eq(10L));
    }

    @Test
    void checkAndNotifyThresholdsFiresGoalAchievedForCompletedSavings() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        SavingsBudgetEntity s = savings(20L, 1L, 6L, 500.0, 0.0, null, null, null);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of());
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(s));
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(category(6L, "Vacation")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("savings"), eq(6L), any(), any()))
                .thenReturn(500.0);

        service.checkAndNotifyThresholds(plan);

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("SAVINGS_GOAL_ACHIEVED"),
                contains("Vacation"), anyString(), eq("SAVINGS_BUDGET"), eq(20L));
    }

    @Test
    void checkAndNotifyThresholdsDoesNotNotifyForOnTrackOrInProgress() {
        BudgetPlanEntity plan = planWithRange(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        BudgetEntity b = budget(10L, 1L, 5L, 1000.0);
        SavingsBudgetEntity s = savings(20L, 1L, 6L, 500.0, 0.0, null, null, null);
        when(budgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(b));
        when(savingsBudgetRepository.findByBudgetPlanId(1L)).thenReturn(List.of(s));
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.of(category(5L, "Dining")));
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(anyLong(), anyString(), anyLong(), any(), any()))
                .thenReturn(10.0);

        service.checkAndNotifyThresholds(plan);

        verifyNoInteractions(notificationService);
    }
}
