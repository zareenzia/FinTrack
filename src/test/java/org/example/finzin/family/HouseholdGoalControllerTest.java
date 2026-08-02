package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdGoalContributionEntity;
import org.example.finzin.entity.HouseholdGoalEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.family.dto.HouseholdGoalResponse;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link HouseholdGoalController}. */
@WebMvcTest(HouseholdGoalController.class)
class HouseholdGoalControllerTest {

    private static final Long USER_ID = 42L;
    private static final Long HOUSEHOLD_ID = 1L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private HouseholdGoalService goalService;
    @MockitoBean private HouseholdService householdService;
    @MockitoBean private FamilyPermissionService permissionService;

    private HouseholdMemberEntity member(String role) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(HOUSEHOLD_ID);
        m.setUserId(USER_ID);
        m.setRole(role);
        return m;
    }

    private HouseholdGoalEntity goal(Long id, Long createdByUserId) {
        HouseholdGoalEntity g = new HouseholdGoalEntity();
        g.setId(id);
        g.setHouseholdId(HOUSEHOLD_ID);
        g.setName("Vacation Fund");
        g.setTargetAmount(2000.0);
        g.setCreatedByUserId(createdByUserId);
        g.setStatus("ACTIVE");
        return g;
    }

    private HouseholdGoalResponse goalResponse(Long id) {
        return new HouseholdGoalResponse(id, HOUSEHOLD_ID, "Vacation Fund", 2000.0, null, "ACTIVE",
                500.0, 25.0, List.of(), USER_ID, "Me", "2026-01-01T00:00:00");
    }

    // ── GET / ──────────────────────────────────────────────────────────────

    @Test
    void list_returnsMappedGoals() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdGoalEntity g = goal(3L, USER_ID);
        given(goalService.listForHousehold(HOUSEHOLD_ID)).willReturn(List.of(g));
        given(goalService.toResponse(g)).willReturn(goalResponse(3L));

        mockMvc.perform(get("/api/households/1/goals").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Vacation Fund"));
    }

    @Test
    void list_returnsForbidden_whenNotMember() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willThrow(FamilyException.notMember());

        mockMvc.perform(get("/api/households/1/goals").requestAttr("userId", USER_ID))
                .andExpect(status().isForbidden());
    }

    // ── POST / (create) ────────────────────────────────────────────────────

    @Test
    void create_returnsCreatedGoal() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdEntity h = new HouseholdEntity();
        h.setId(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        HouseholdGoalEntity saved = goal(4L, USER_ID);
        given(goalService.create(eq(h), eq(USER_ID), any())).willReturn(saved);
        given(goalService.toResponse(saved)).willReturn(goalResponse(4L));

        mockMvc.perform(post("/api/households/1/goals").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vacation Fund\",\"targetAmount\":2000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void create_returnsBadRequest_whenNameMissing() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(new HouseholdEntity());
        given(goalService.create(any(), eq(USER_ID), any())).willThrow(FamilyException.badRequest("Goal name is required"));

        mockMvc.perform(post("/api/households/1/goals").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\":2000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Goal name is required"));
    }

    // ── PUT /{goalId} ────────────────────────────────────────────────────

    @Test
    void update_returnsUpdatedGoal_whenCreator() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdGoalEntity existing = goal(3L, USER_ID);
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);
        HouseholdGoalEntity updated = goal(3L, USER_ID);
        updated.setTargetAmount(3000.0);
        given(goalService.update(eq(existing), any())).willReturn(updated);
        given(goalService.toResponse(updated)).willReturn(new HouseholdGoalResponse(3L, HOUSEHOLD_ID, "Vacation Fund", 3000.0, null,
                "ACTIVE", 500.0, 16.7, List.of(), USER_ID, "Me", "2026-01-01T00:00:00"));

        mockMvc.perform(put("/api/households/1/goals/3").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vacation Fund\",\"targetAmount\":3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(3000.0));
    }

    @Test
    void update_returnsForbidden_whenNotCreatorOrAdmin() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdGoalEntity existing = goal(3L, 999L); // created by someone else
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);

        mockMvc.perform(put("/api/households/1/goals/3").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vacation Fund\",\"targetAmount\":3000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the household administrator can do this."));
    }

    // ── PATCH /{goalId}/archive ────────────────────────────────────────────

    @Test
    void archive_returnsArchivedGoal_whenAdmin() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("ADMIN"));
        HouseholdGoalEntity existing = goal(3L, 999L); // created by someone else, but caller is admin
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);
        HouseholdGoalEntity archived = goal(3L, 999L);
        archived.setStatus("ARCHIVED");
        given(goalService.archive(existing)).willReturn(archived);
        given(goalService.toResponse(archived)).willReturn(new HouseholdGoalResponse(3L, HOUSEHOLD_ID, "Vacation Fund", 2000.0, null,
                "ARCHIVED", 500.0, 25.0, List.of(), 999L, "Someone", "2026-01-01T00:00:00"));

        mockMvc.perform(patch("/api/households/1/goals/3/archive").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    // ── DELETE /{goalId} ───────────────────────────────────────────────────

    @Test
    void delete_returnsNoContent_onSuccess() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdGoalEntity existing = goal(3L, USER_ID);
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);

        mockMvc.perform(delete("/api/households/1/goals/3").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(goalService).delete(existing);
    }

    @Test
    void delete_returnsConflict_whenGoalHasContributions() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdGoalEntity existing = goal(3L, USER_ID);
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);
        org.mockito.Mockito.doThrow(FamilyException.conflict("This goal has contributions recorded — archive it instead of deleting it."))
                .when(goalService).delete(existing);

        mockMvc.perform(delete("/api/households/1/goals/3").requestAttr("userId", USER_ID))
                .andExpect(status().isConflict());
    }

    // ── POST /{goalId}/contributions ──────────────────────────────────────

    @Test
    void contribute_returnsCreatedGoalResponse() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdEntity h = new HouseholdEntity();
        h.setId(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        HouseholdGoalEntity existing = goal(3L, USER_ID);
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);
        given(goalService.toResponse(existing)).willReturn(goalResponse(3L));

        mockMvc.perform(post("/api/households/1/goals/3/contributions").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));

        verify(goalService).contribute(eq(h), eq(existing), eq(USER_ID), any());
    }

    @Test
    void contribute_returnsBadRequest_whenTransactionMissing() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(new HouseholdEntity());
        HouseholdGoalEntity existing = goal(3L, USER_ID);
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);
        org.mockito.Mockito.doThrow(FamilyException.badRequest("transactionId is required"))
                .when(goalService).contribute(any(), eq(existing), eq(USER_ID), any());

        mockMvc.perform(post("/api/households/1/goals/3/contributions").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /{goalId}/contributions/{contributionId} ───────────────────

    @Test
    void removeContribution_returnsForbidden_whenNotAdminAndNotContributor() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdGoalEntity existing = goal(3L, USER_ID);
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);
        HouseholdGoalContributionEntity contribution = new HouseholdGoalContributionEntity();
        contribution.setId(10L);
        contribution.setUserId(999L); // someone else contributed
        given(goalService.requireContribution(10L, 3L)).willReturn(contribution);

        mockMvc.perform(delete("/api/households/1/goals/3/contributions/10").requestAttr("userId", USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeContribution_returnsNoContent_whenCallerIsContributor() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member("MEMBER"));
        HouseholdGoalEntity existing = goal(3L, 999L);
        given(goalService.requireGoal(3L, HOUSEHOLD_ID)).willReturn(existing);
        HouseholdGoalContributionEntity contribution = new HouseholdGoalContributionEntity();
        contribution.setId(10L);
        contribution.setUserId(USER_ID);
        given(goalService.requireContribution(10L, 3L)).willReturn(contribution);

        mockMvc.perform(delete("/api/households/1/goals/3/contributions/10").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(goalService).removeContribution(existing, contribution);
    }
}
