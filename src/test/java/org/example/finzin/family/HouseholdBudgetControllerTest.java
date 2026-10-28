package org.example.finzin.family;

import org.example.finzin.entity.HouseholdBudgetEntity;
import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.family.dto.HouseholdBudgetResponse;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link HouseholdBudgetController}. */
@WebMvcTest(controllers = HouseholdBudgetController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class HouseholdBudgetControllerTest {

    private static final Long USER_ID = 42L;
    private static final Long HOUSEHOLD_ID = 1L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private HouseholdBudgetService budgetService;
    @MockitoBean private HouseholdService householdService;
    @MockitoBean private FamilyPermissionService permissionService;

    private HouseholdMemberEntity member(String role) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(HOUSEHOLD_ID);
        m.setUserId(USER_ID);
        m.setRole(role);
        return m;
    }

    private HouseholdBudgetEntity budget(Long id) {
        HouseholdBudgetEntity b = new HouseholdBudgetEntity();
        b.setId(id);
        b.setHouseholdId(HOUSEHOLD_ID);
        b.setCategoryName("Groceries");
        b.setMonthlyLimit(500.0);
        b.setCreatedByUserId(USER_ID);
        return b;
    }

    private HouseholdBudgetResponse budgetResponse(Long id) {
        return new HouseholdBudgetResponse(id, HOUSEHOLD_ID, "Groceries", 500.0, 100.0, 20.0, "ON_TRACK", USER_ID, "Me");
    }

    // ── GET / ──────────────────────────────────────────────────────────────

    @Test
    void list_returnsMappedBudgets() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdBudgetEntity b = budget(5L);
        given(budgetService.listForHousehold(HOUSEHOLD_ID)).willReturn(List.of(b));
        given(budgetService.toResponse(b)).willReturn(budgetResponse(5L));

        mockMvc.perform(get("/api/households/1/budgets").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].categoryName").value("Groceries"));
    }

    @Test
    void list_returnsForbidden_whenNotMember() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willThrow(FamilyException.notMember());

        mockMvc.perform(get("/api/households/1/budgets").requestAttr("userId", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You are not a member of this household."));
    }

    // ── GET /category-suggestions ────────────────────────────────────────

    @Test
    void categorySuggestions_returnsList() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        given(budgetService.categorySuggestions(HOUSEHOLD_ID)).willReturn(List.of("Groceries", "Utilities"));

        mockMvc.perform(get("/api/households/1/budgets/category-suggestions").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Groceries"))
                .andExpect(jsonPath("$[1]").value("Utilities"));
    }

    // ── POST / (create) ────────────────────────────────────────────────────

    @Test
    void create_returnsCreatedBudget() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member("ADMIN"));
        HouseholdEntity h = new HouseholdEntity();
        h.setId(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        HouseholdBudgetEntity saved = budget(9L);
        given(budgetService.create(eq(h), eq(USER_ID), any())).willReturn(saved);
        given(budgetService.toResponse(saved)).willReturn(budgetResponse(9L));

        mockMvc.perform(post("/api/households/1/budgets").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Groceries\",\"monthlyLimit\":500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void create_returnsConflict_whenDuplicateCategory() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member("ADMIN"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(new HouseholdEntity());
        given(budgetService.create(any(), eq(USER_ID), any()))
                .willThrow(FamilyException.conflict("A household budget for \"Groceries\" already exists."));

        mockMvc.perform(post("/api/households/1/budgets").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Groceries\",\"monthlyLimit\":500}"))
                .andExpect(status().isConflict());
    }

    // ── PUT /{budgetId} ────────────────────────────────────────────────────

    @Test
    void update_returnsUpdatedBudget() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member("ADMIN"));
        HouseholdBudgetEntity existing = budget(5L);
        given(budgetService.requireBudget(5L, HOUSEHOLD_ID)).willReturn(existing);
        HouseholdBudgetEntity updated = budget(5L);
        updated.setMonthlyLimit(700.0);
        given(budgetService.update(eq(existing), any())).willReturn(updated);
        given(budgetService.toResponse(updated)).willReturn(new HouseholdBudgetResponse(5L, HOUSEHOLD_ID, "Groceries", 700.0, 100.0, 14.3, "ON_TRACK", USER_ID, "Me"));

        mockMvc.perform(put("/api/households/1/budgets/5").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Groceries\",\"monthlyLimit\":700}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyLimit").value(700.0));
    }

    @Test
    void update_returnsNotFound_whenBudgetNotInHousehold() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member("ADMIN"));
        given(budgetService.requireBudget(5L, HOUSEHOLD_ID)).willThrow(FamilyException.notFound("Household budget"));

        mockMvc.perform(put("/api/households/1/budgets/5").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Groceries\",\"monthlyLimit\":700}"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /{budgetId} ─────────────────────────────────────────────────

    @Test
    void delete_returnsNoContent_onSuccess() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member("ADMIN"));
        HouseholdBudgetEntity existing = budget(5L);
        given(budgetService.requireBudget(5L, HOUSEHOLD_ID)).willReturn(existing);

        mockMvc.perform(delete("/api/households/1/budgets/5").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(budgetService).delete(existing);
    }

    @Test
    void delete_returnsForbidden_whenNotAdmin() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willThrow(FamilyException.notAdmin());

        mockMvc.perform(delete("/api/households/1/budgets/5").requestAttr("userId", USER_ID))
                .andExpect(status().isForbidden());
    }
}
