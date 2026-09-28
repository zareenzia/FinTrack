package org.example.finzin.web;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.BudgetTemplateCategoryEntity;
import org.example.finzin.entity.BudgetTemplateEntity;
import org.example.finzin.service.BudgetTemplateService;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BudgetTemplateApiController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class BudgetTemplateApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private BudgetTemplateService budgetTemplateService;

    private BudgetTemplateEntity template(Long id) {
        BudgetTemplateEntity e = new BudgetTemplateEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setName("Monthly Essentials");
        e.setPlannedIncome(50000.0);
        e.setPlannedSavings(10000.0);
        e.setNotes("note");
        return e;
    }

    // ── GET /api/budget-templates ─────────────────────────────────────────────

    @Test
    void listReturnsMappedTemplatesForUser() throws Exception {
        when(budgetTemplateService.listForUser(USER_ID)).thenReturn(List.of(template(1L)));
        when(budgetTemplateService.getRows(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/budget-templates").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Monthly Essentials"))
                .andExpect(jsonPath("$[0].plannedIncome").value(50000.0));
    }

    // ── POST /api/budget-templates ────────────────────────────────────────────

    @Test
    void createReturnsBadRequestWhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/budget-templates")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Template name is required"));
    }

    @Test
    void createReturnsBadRequestWhenNameBlank() throws Exception {
        mockMvc.perform(post("/api/budget-templates")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Template name is required"));
    }

    @Test
    void createReturnsCreatedTemplateOnSuccess() throws Exception {
        when(budgetTemplateService.save(any(BudgetTemplateEntity.class), anyList())).thenAnswer(inv -> {
            BudgetTemplateEntity e = inv.getArgument(0);
            e.setId(9L);
            return e;
        });
        when(budgetTemplateService.getRows(9L)).thenReturn(List.of());

        mockMvc.perform(post("/api/budget-templates")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Monthly Essentials\",\"plannedIncome\":50000.0,\"plannedSavings\":10000.0," +
                                "\"categories\":[{\"categoryId\":3,\"plannedAmount\":5000.0,\"isSavings\":false}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.name").value("Monthly Essentials"))
                .andExpect(jsonPath("$.plannedIncome").value(50000.0))
                .andExpect(jsonPath("$.plannedSavings").value(10000.0));

        verify(budgetTemplateService).save(any(BudgetTemplateEntity.class), argThatHasOneRow());
    }

    private List<BudgetTemplateCategoryEntity> argThatHasOneRow() {
        return org.mockito.ArgumentMatchers.argThat(rows -> rows != null && rows.size() == 1
                && rows.get(0).getCategoryId().equals(3L) && rows.get(0).getPlannedAmount().equals(5000.0));
    }

    // ── PUT /api/budget-templates/{id} ────────────────────────────────────────

    @Test
    void updateReturns404WhenNotOwned() throws Exception {
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(put("/api/budget-templates/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Template not found"));
    }

    @Test
    void updateReturnsBadRequestWhenNameMissing() throws Exception {
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(template(1L));

        mockMvc.perform(put("/api/budget-templates/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Template name is required"));
    }

    @Test
    void updateReturnsUpdatedTemplateOnSuccess() throws Exception {
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(template(1L));
        when(budgetTemplateService.save(any(BudgetTemplateEntity.class), anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(budgetTemplateService.getRows(1L)).thenReturn(List.of());

        mockMvc.perform(put("/api/budget-templates/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed Template\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Template"));
    }

    // ── DELETE /api/budget-templates/{id} ─────────────────────────────────────

    @Test
    void deleteReturns404WhenNotOwned() throws Exception {
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(delete("/api/budget-templates/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());

        verify(budgetTemplateService, never()).delete(any());
    }

    @Test
    void deleteReturnsNoContentOnSuccess() throws Exception {
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(template(1L));

        mockMvc.perform(delete("/api/budget-templates/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(budgetTemplateService).delete(1L);
    }

    // ── POST /api/budget-templates/{id}/apply ─────────────────────────────────

    @Test
    void applyReturns404WhenNotOwned() throws Exception {
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(post("/api/budget-templates/1/apply")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"MONTH\",\"period\":\"2026-08\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void applyReturnsBadRequestWhenRequiredFieldsMissing() throws Exception {
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(template(1L));

        mockMvc.perform(post("/api/budget-templates/1/apply")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"MONTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("periodType, period, startDate, and endDate are required"));
    }

    @Test
    void applyReturnsCreatedPlanOnSuccess() throws Exception {
        BudgetTemplateEntity ownedTemplate = template(1L);
        when(budgetTemplateService.findOwnedById(1L, USER_ID)).thenReturn(ownedTemplate);
        BudgetPlanEntity plan = new BudgetPlanEntity();
        plan.setId(77L);
        plan.setName("Monthly Essentials");
        plan.setPeriod("2026-08");
        when(budgetTemplateService.applyTemplate(eq(ownedTemplate), eq(null), eq("MONTH"), eq("2026-08"),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)))).thenReturn(plan);

        mockMvc.perform(post("/api/budget-templates/1/apply")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"MONTH\",\"period\":\"2026-08\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.name").value("Monthly Essentials"))
                .andExpect(jsonPath("$.period").value("2026-08"));
    }
}
