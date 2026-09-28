package org.example.finzin.web;

import org.example.finzin.entity.BudgetEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.SavingsBudgetEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.service.BudgetExportService;
import org.example.finzin.service.BudgetPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BudgetPlanApiController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@RecordApplicationEvents
class BudgetPlanApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private org.example.finzin.service.JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private BudgetPlanService budgetPlanService;

    @MockitoBean
    private BudgetExportService budgetExportService;

    @MockitoBean
    private CategoryRepository categoryRepository;

    private BudgetPlanEntity plan(Long id) {
        BudgetPlanEntity e = new BudgetPlanEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setName("August Budget");
        e.setPeriodType("MONTH");
        e.setPeriod("2026-08");
        e.setStartDate(LocalDate.of(2026, 8, 1));
        e.setEndDate(LocalDate.of(2026, 8, 31));
        e.setPlannedIncome(50000.0);
        e.setPlannedSavings(10000.0);
        e.setNotes("notes");
        e.setStatus("ACTIVE");
        return e;
    }

    private void stubComputedViews(BudgetPlanEntity p) {
        when(budgetPlanService.computeCategoryStatuses(p)).thenReturn(List.of());
        when(budgetPlanService.computeSavingsStatuses(p)).thenReturn(List.of());
        when(budgetPlanService.computeSummary(eq(p), anyList())).thenReturn(Map.of(
                "plannedIncome", 50000.0, "plannedExpense", 0.0, "plannedSavings", 10000.0,
                "actualIncome", 0.0, "actualExpense", 0.0, "actualSavings", 0.0,
                "remaining", 40000.0, "utilizationPercent", 0.0, "activeBudgetsCount", 0));
        when(budgetPlanService.computeBudgetScore(eq(p), anyList(), anyList(), any())).thenReturn(80);
    }

    // ── GET /api/budget-plans ──────────────────────────────────────────────────

    @Test
    void listReturnsSummaryMappedPlans() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.listForUser(USER_ID, null, null, null, null)).thenReturn(List.of(p));
        stubComputedViews(p);

        mockMvc.perform(get("/api/budget-plans").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("August Budget"))
                .andExpect(jsonPath("$[0].score").value(80))
                .andExpect(jsonPath("$[0].categoryCount").value(0));
    }

    // ── GET /api/budget-plans/current ─────────────────────────────────────────

    @Test
    void currentReturnsBadRequestForUnparseableMonth() throws Exception {
        mockMvc.perform(get("/api/budget-plans/current").requestAttr("userId", USER_ID).param("month", "not-a-month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("month must be in yyyy-MM format"));
    }

    @Test
    void currentReturnsHasCurrentFalseWhenNoPlanMatches() throws Exception {
        when(budgetPlanService.getPlanForDate(eq(USER_ID), any())).thenReturn(null);

        mockMvc.perform(get("/api/budget-plans/current").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCurrent").value(false));
    }

    @Test
    void currentReturnsPlanSummaryWhenFound() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.getPlanForDate(USER_ID, LocalDate.of(2026, 8, 1))).thenReturn(p);
        stubComputedViews(p);

        mockMvc.perform(get("/api/budget-plans/current").requestAttr("userId", USER_ID).param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCurrent").value(true))
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── GET /api/budget-plans/{id}/full ────────────────────────────────────────

    @Test
    void fullReturns404WhenNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/budget-plans/1/full").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Budget plan not found"));
    }

    @Test
    void fullReturnsCategoriesSavingsSummaryAndScore() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        stubComputedViews(p);

        mockMvc.perform(get("/api/budget-plans/1/full").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.score").value(80))
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.savings").isArray())
                .andExpect(jsonPath("$.summary.plannedIncome").value(50000.0));
    }

    // ── POST /api/budget-plans ─────────────────────────────────────────────────

    @Test
    void createReturnsBadRequestWhenValidationFails() throws Exception {
        when(budgetPlanService.validate(any(), any(), any(), any(), any())).thenReturn("Budget name is required");

        mockMvc.perform(post("/api/budget-plans")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"MONTH\",\"period\":\"2026-08\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Budget name is required"));
    }

    @Test
    void createReturnsCreatedPlanAndPublishesGamificationEvent() throws Exception {
        when(budgetPlanService.validate(eq("August Budget"), eq("MONTH"), eq("2026-08"),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)))).thenReturn(null);
        when(budgetPlanService.save(any(BudgetPlanEntity.class))).thenAnswer(inv -> {
            BudgetPlanEntity e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });

        mockMvc.perform(post("/api/budget-plans")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"August Budget\",\"periodType\":\"MONTH\",\"period\":\"2026-08\"," +
                                "\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\",\"plannedIncome\":50000,\"plannedSavings\":10000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("August Budget"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // ApplicationEventPublisher can't be @MockitoBean'd here: Spring registers it as a resolvable
        // dependency bound directly to the real ApplicationContext (see AbstractApplicationContext's
        // prepareBeanFactory), which bypasses normal bean-type lookup, so the controller always gets
        // the real publisher regardless of any mock bean of that type. ApplicationEvents (enabled via
        // @RecordApplicationEvents) is the supported way to assert an event was actually published.
        assertEquals(1, applicationEvents.stream(GamificationEvent.class).count());
    }

    // ── PUT /api/budget-plans/{id} ─────────────────────────────────────────────

    @Test
    void updateReturns404WhenNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(put("/api/budget-plans/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"periodType\":\"MONTH\",\"period\":\"2026-08\"," +
                                "\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReturnsBadRequestWhenValidationFails() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(plan(1L));
        when(budgetPlanService.validate(any(), any(), any(), any(), any())).thenReturn("endDate must be on or after startDate");

        mockMvc.perform(put("/api/budget-plans/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"periodType\":\"MONTH\",\"period\":\"2026-08\"," +
                                "\"startDate\":\"2026-08-31\",\"endDate\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("endDate must be on or after startDate"));
    }

    @Test
    void updateReturnsUpdatedPlanOnSuccess() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(plan(1L));
        when(budgetPlanService.validate(any(), any(), any(), any(), any())).thenReturn(null);
        when(budgetPlanService.save(any(BudgetPlanEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/budget-plans/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"periodType\":\"MONTH\",\"period\":\"2026-08\"," +
                                "\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    // ── PATCH /api/budget-plans/{id}/archive ──────────────────────────────────

    @Test
    void archiveReturns404WhenNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(patch("/api/budget-plans/1/archive").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveReturnsPlanOnSuccess() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);

        mockMvc.perform(patch("/api/budget-plans/1/archive").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(budgetPlanService).archive(p);
    }

    // ── DELETE /api/budget-plans/{id} ─────────────────────────────────────────

    @Test
    void deleteReturns404WhenNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(delete("/api/budget-plans/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());

        verify(budgetPlanService, never()).delete(any());
    }

    @Test
    void deleteReturnsNoContentOnSuccess() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);

        mockMvc.perform(delete("/api/budget-plans/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(budgetPlanService).delete(p);
    }

    // ── POST /api/budget-plans/{id}/duplicate ─────────────────────────────────

    @Test
    void duplicateReturns404WhenSourceNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(post("/api/budget-plans/1/duplicate")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"MONTH\",\"period\":\"2026-09\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateFallsBackToSourceNameWhenNameBlank() throws Exception {
        BudgetPlanEntity source = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(source);
        when(budgetPlanService.validate(eq("August Budget"), eq("MONTH"), eq("2026-09"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)))).thenReturn(null);
        BudgetPlanEntity copy = plan(2L);
        copy.setPeriod("2026-09");
        when(budgetPlanService.duplicate(eq(source), any(), eq("MONTH"), eq("2026-09"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)))).thenReturn(copy);

        mockMvc.perform(post("/api/budget-plans/1/duplicate")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"MONTH\",\"period\":\"2026-09\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    // ── POST /api/budget-plans/copy-previous ──────────────────────────────────

    @Test
    void copyPreviousReturnsBadRequestWhenBodyMissing() throws Exception {
        // A literal JSON "null" body never reaches the controller: Spring rejects a null-deserialized
        // non-optional @RequestBody with HttpMessageNotReadableException before the handler method
        // runs, so the response is a framework-generated 400 with no body (not the controller's
        // "Missing request body" JSON) and the service is never invoked.
        mockMvc.perform(post("/api/budget-plans/copy-previous")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(budgetPlanService);
    }

    @Test
    void copyPreviousCreatesAndCopiesFromPreviousPlan() throws Exception {
        when(budgetPlanService.validate(eq("September Budget"), eq("MONTH"), eq("2026-09"),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)))).thenReturn(null);
        when(budgetPlanService.save(any(BudgetPlanEntity.class))).thenAnswer(inv -> {
            BudgetPlanEntity e = inv.getArgument(0);
            e.setId(6L);
            return e;
        });
        BudgetPlanEntity result = plan(6L);
        result.setPeriod("2026-09");
        when(budgetPlanService.copyFromPreviousPlan(any(BudgetPlanEntity.class))).thenReturn(result);

        mockMvc.perform(post("/api/budget-plans/copy-previous")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"September Budget\",\"periodType\":\"MONTH\",\"period\":\"2026-09\"," +
                                "\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6));
    }

    // ── POST /api/budget-plans/{id}/categories ────────────────────────────────

    @Test
    void upsertCategoryReturns404WhenPlanNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(post("/api/budget-plans/1/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":3,\"amount\":500}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void upsertCategoryReturnsBadRequestWhenAmountNotPositive() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(plan(1L));

        mockMvc.perform(post("/api/budget-plans/1/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":3,\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("categoryId and a positive amount are required"));
    }

    @Test
    void upsertCategoryReturnsBadRequestWhenCategoryNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(plan(1L));
        CategoryEntity foreignCategory = new CategoryEntity(999L, "Food", "", "#fff", "tag");
        foreignCategory.setId(3L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(foreignCategory));

        mockMvc.perform(post("/api/budget-plans/1/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":3,\"amount\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid category"));
    }

    @Test
    void upsertCategoryReturnsCreatedOnSuccess() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        CategoryEntity category = new CategoryEntity(USER_ID, "Food", "", "#fff", "tag");
        category.setId(3L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        BudgetEntity saved = new BudgetEntity();
        saved.setId(8L);
        saved.setCategoryId(3L);
        saved.setBudgetAmount(500.0);
        when(budgetPlanService.upsertCategoryBudget(p, 3L, 500.0)).thenReturn(saved);

        mockMvc.perform(post("/api/budget-plans/1/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":3,\"amount\":500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.categoryId").value(3))
                .andExpect(jsonPath("$.budgetAmount").value(500.0));
    }

    // ── DELETE /api/budget-plans/{id}/categories/{budgetId} ───────────────────

    @Test
    void deleteCategoryReturns404WhenPlanNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(delete("/api/budget-plans/1/categories/8").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategoryReturns404WhenBudgetIdNotInPlan() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        when(budgetPlanService.computeCategoryStatuses(p)).thenReturn(List.of(Map.of("id", 99L)));

        mockMvc.perform(delete("/api/budget-plans/1/categories/8").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Category budget not found"));

        verify(budgetPlanService, never()).deleteCategoryBudgetById(any());
    }

    @Test
    void deleteCategoryReturnsNoContentOnSuccess() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        when(budgetPlanService.computeCategoryStatuses(p)).thenReturn(List.of(Map.of("id", 8L)));

        mockMvc.perform(delete("/api/budget-plans/1/categories/8").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(budgetPlanService).deleteCategoryBudgetById(8L);
    }

    // ── POST /api/budget-plans/{id}/savings ───────────────────────────────────

    @Test
    void upsertSavingsReturnsBadRequestWhenNegativeInitialAmount() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        CategoryEntity category = new CategoryEntity(USER_ID, "Emergency Fund", "", "#fff", "tag");
        category.setId(4L);
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));

        mockMvc.perform(post("/api/budget-plans/1/savings")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":4,\"amount\":1000,\"initialAmount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("initialAmount cannot be negative"));
    }

    @Test
    void upsertSavingsReturnsCreatedOnSuccess() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        CategoryEntity category = new CategoryEntity(USER_ID, "Emergency Fund", "", "#fff", "tag");
        category.setId(4L);
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
        SavingsBudgetEntity saved = new SavingsBudgetEntity();
        saved.setId(11L);
        saved.setCategoryId(4L);
        saved.setTargetAmount(1000.0);
        saved.setInitialAmount(200.0);
        when(budgetPlanService.upsertSavingsBudget(p, 4L, 1000.0, 200.0, null, null, null)).thenReturn(saved);

        mockMvc.perform(post("/api/budget-plans/1/savings")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":4,\"amount\":1000,\"initialAmount\":200}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.targetAmount").value(1000.0))
                .andExpect(jsonPath("$.initialAmount").value(200.0));
    }

    // ── DELETE /api/budget-plans/{id}/savings/{savingsId} ─────────────────────

    @Test
    void deleteSavingsReturns404WhenSavingsIdNotInPlan() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        when(budgetPlanService.computeSavingsStatuses(p)).thenReturn(List.of());

        mockMvc.perform(delete("/api/budget-plans/1/savings/11").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Savings goal not found"));
    }

    @Test
    void deleteSavingsReturnsNoContentOnSuccess() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        when(budgetPlanService.computeSavingsStatuses(p)).thenReturn(List.of(Map.of("id", 11L)));

        mockMvc.perform(delete("/api/budget-plans/1/savings/11").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(budgetPlanService).deleteSavingsBudgetById(11L);
    }

    // ── GET /api/budget-plans/{id}/export/{format} ────────────────────────────

    @Test
    void exportReturns404WhenNotOwned() throws Exception {
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/budget-plans/1/export/csv").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportReturnsBadRequestForUnsupportedFormat() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        stubComputedViews(p);

        mockMvc.perform(get("/api/budget-plans/1/export/json").requestAttr("userId", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("format must be csv, excel, or pdf"));
    }

    @Test
    void exportCsvReturnsCsvContentTypeAndAttachmentHeader() throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        stubComputedViews(p);
        when(budgetExportService.generateCsv(eq(p), any(), any(), any(), eq(80))).thenReturn("csv,data");

        mockMvc.perform(get("/api/budget-plans/1/export/csv").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"budget-2026-08.csv\""));
    }

    @ParameterizedTest
    @CsvSource({
            "excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, budget-2026-08.xlsx",
            "pdf, application/pdf, budget-2026-08.pdf"
    })
    void exportBinaryFormatsReturnCorrectContentTypeAndFilename(String format, String contentType, String filename) throws Exception {
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        stubComputedViews(p);
        when(budgetExportService.generateExcel(eq(p), any(), any(), any(), eq(80))).thenReturn(new byte[]{1, 2, 3});
        when(budgetExportService.generatePdf(eq(p), any(), any(), any(), eq(80))).thenReturn(new byte[]{4, 5, 6});

        mockMvc.perform(get("/api/budget-plans/1/export/" + format).requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", contentType))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + filename + "\""));
    }

    @Test
    void exportReturns500WhenExportServiceThrowsIOException() throws Exception {
        // generateCsv() is plain StringBuilder work and doesn't declare `throws IOException` (Mockito
        // rejects stubbing a checked exception a method can't actually throw), so this exercises the
        // excel path instead, whose POI-backed generateExcel() genuinely does.
        BudgetPlanEntity p = plan(1L);
        when(budgetPlanService.findOwnedById(1L, USER_ID)).thenReturn(p);
        stubComputedViews(p);
        when(budgetExportService.generateExcel(eq(p), any(), any(), any(), eq(80)))
                .thenThrow(new java.io.IOException("disk full"));

        mockMvc.perform(get("/api/budget-plans/1/export/excel").requestAttr("userId", USER_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to generate export"));
    }
}
