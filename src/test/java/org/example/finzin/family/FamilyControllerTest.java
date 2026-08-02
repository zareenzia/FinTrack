package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdInvitationEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.SettlementEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.family.dto.FamilyDashboardSummaryResponse;
import org.example.finzin.family.dto.HouseholdResponse;
import org.example.finzin.family.dto.InvitationResponse;
import org.example.finzin.family.dto.SettlementResponse;
import org.example.finzin.family.dto.SettlementSummaryResponse;
import org.example.finzin.family.dto.SharedExpenseResponse;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link FamilyController}. */
@WebMvcTest(FamilyController.class)
class FamilyControllerTest {

    private static final Long USER_ID = 42L;
    private static final Long HOUSEHOLD_ID = 1L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private HouseholdService householdService;
    @MockitoBean private InvitationService invitationService;
    @MockitoBean private SharedExpenseService sharedExpenseService;
    @MockitoBean private SettlementService settlementService;
    @MockitoBean private FamilyPermissionService permissionService;

    private HouseholdEntity household(Long id) {
        HouseholdEntity h = new HouseholdEntity();
        h.setId(id);
        h.setName("The Does");
        h.setOwnerId(USER_ID);
        return h;
    }

    private HouseholdMemberEntity member(Long householdId, Long userId, String role) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(householdId);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    private HouseholdResponse householdResponse(Long id) {
        return new HouseholdResponse(true, id, "The Does", USER_ID, "ADMIN", List.of(), "2026-01-01T00:00:00");
    }

    // ── GET /mine ──────────────────────────────────────────────────────────

    @Test
    void mine_returnsNoneResponse_whenUserHasNoMembership() throws Exception {
        given(householdService.currentMembership(USER_ID)).willReturn(null);

        mockMvc.perform(get("/api/households/mine").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHousehold").value(false));
    }

    @Test
    void mine_returnsHouseholdResponse_whenMember() throws Exception {
        given(householdService.currentMembership(USER_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(household(HOUSEHOLD_ID));
        given(householdService.toResponse(any(HouseholdEntity.class), eq(USER_ID))).willReturn(householdResponse(HOUSEHOLD_ID));

        mockMvc.perform(get("/api/households/mine").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHousehold").value(true))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void mine_defaultsToUserOne_whenUnauthenticated() throws Exception {
        given(householdService.currentMembership(1L)).willReturn(null);

        mockMvc.perform(get("/api/households/mine"))
                .andExpect(status().isOk());

        verify(householdService).currentMembership(1L);
    }

    // ── POST / (create) ───────────────────────────────────────────────────

    @Test
    void create_returnsCreatedHousehold() throws Exception {
        given(householdService.create(USER_ID, "The Does")).willReturn(household(HOUSEHOLD_ID));
        given(householdService.toResponse(any(HouseholdEntity.class), eq(USER_ID))).willReturn(householdResponse(HOUSEHOLD_ID));

        mockMvc.perform(post("/api/households").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"The Does\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_returnsConflict_whenAlreadyInHousehold() throws Exception {
        given(householdService.create(USER_ID, "The Does")).willThrow(FamilyException.conflict("You already belong to a household."));

        mockMvc.perform(post("/api/households").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"The Does\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You already belong to a household."));
    }

    // ── PUT /{id} (update) ─────────────────────────────────────────────────

    @Test
    void update_returnsForbidden_whenNotAdmin() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willThrow(FamilyException.notAdmin());

        mockMvc.perform(put("/api/households/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the household administrator can do this."));
    }

    @Test
    void update_returnsUpdatedHousehold_onSuccess() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(household(HOUSEHOLD_ID));
        given(householdService.update(any(HouseholdEntity.class), eq("New Name"))).willReturn(household(HOUSEHOLD_ID));
        given(householdService.toResponse(any(HouseholdEntity.class), eq(USER_ID))).willReturn(householdResponse(HOUSEHOLD_ID));

        mockMvc.perform(put("/api/households/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── DELETE /{id} ───────────────────────────────────────────────────────

    @Test
    void delete_returnsNoContent_onSuccess() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);

        mockMvc.perform(delete("/api/households/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(householdService).delete(h);
    }

    @Test
    void delete_returnsNotFound_whenHouseholdMissing() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willThrow(FamilyException.notFound("Household"));

        mockMvc.perform(delete("/api/households/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    // ── POST /{id}/transfer-ownership ─────────────────────────────────────

    @Test
    void transferOwnership_returnsBadRequest_whenNewOwnerIdMissing() throws Exception {
        // A literal JSON "null" body never reaches the controller: Spring rejects a null-deserialized
        // non-optional @RequestBody with HttpMessageNotReadableException before the handler method
        // runs, so the response is a framework-generated 400 with no body (not the controller's
        // "newOwnerUserId is required" JSON) and no service is ever invoked.
        mockMvc.perform(post("/api/households/1/transfer-ownership").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(permissionService, householdService);
    }

    @Test
    void transferOwnership_returnsUpdatedHousehold_onSuccess() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        given(householdService.toResponse(h, USER_ID)).willReturn(householdResponse(HOUSEHOLD_ID));

        mockMvc.perform(post("/api/households/1/transfer-ownership").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerUserId\":7}"))
                .andExpect(status().isOk());

        verify(householdService).transferOwnership(h, 7L);
    }

    // ── POST /{id}/leave ───────────────────────────────────────────────────

    @Test
    void leave_returnsNoContent_onSuccess() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);

        mockMvc.perform(post("/api/households/1/leave").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(householdService).leave(h, USER_ID);
    }

    // ── DELETE /{id}/members/{memberUserId} ───────────────────────────────

    @Test
    void removeMember_returnsNoContent_onSuccess() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);

        mockMvc.perform(delete("/api/households/1/members/9").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(householdService).removeMember(h, 9L);
    }

    // ── POST /{id}/invitations ─────────────────────────────────────────────

    @Test
    void invite_returnsCreatedInvitation() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        HouseholdInvitationEntity invitation = new HouseholdInvitationEntity();
        invitation.setId(3L);
        given(invitationService.invite(h, USER_ID, "friend@example.com", "Sibling")).willReturn(invitation);
        given(invitationService.toResponse(invitation)).willReturn(new InvitationResponse(
                3L, HOUSEHOLD_ID, "The Does", USER_ID, "Jane", 5L, "Friend", "Sibling", "PENDING", "2026-01-01T00:00:00", null));

        mockMvc.perform(post("/api/households/1/invitations").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrUsername\":\"friend@example.com\",\"relationshipLabel\":\"Sibling\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void invite_returnsBadRequest_whenNoMatchingUser() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(household(HOUSEHOLD_ID));
        given(invitationService.invite(any(HouseholdEntity.class), eq(USER_ID), eq("nobody"), isNull()))
                .willThrow(FamilyException.badRequest("No registered user found with that email or username."));

        mockMvc.perform(post("/api/households/1/invitations").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrUsername\":\"nobody\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /{id}/invitations ──────────────────────────────────────────────

    @Test
    void pendingInvitationsForHousehold_returnsMappedList() throws Exception {
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        HouseholdInvitationEntity invitation = new HouseholdInvitationEntity();
        invitation.setId(3L);
        given(invitationService.pendingForHousehold(HOUSEHOLD_ID)).willReturn(List.of(invitation));
        given(invitationService.toResponse(invitation)).willReturn(new InvitationResponse(
                3L, HOUSEHOLD_ID, "The Does", USER_ID, "Jane", 5L, "Friend", null, "PENDING", "2026-01-01T00:00:00", null));

        mockMvc.perform(get("/api/households/1/invitations").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));
    }

    // ── GET /invitations/mine ──────────────────────────────────────────────

    @Test
    void pendingInvitationsForMe_returnsMappedList() throws Exception {
        HouseholdInvitationEntity invitation = new HouseholdInvitationEntity();
        invitation.setId(4L);
        given(invitationService.pendingForUser(USER_ID)).willReturn(List.of(invitation));
        given(invitationService.toResponse(invitation)).willReturn(new InvitationResponse(
                4L, HOUSEHOLD_ID, "The Does", 8L, "Jane", USER_ID, "Me", null, "PENDING", "2026-01-01T00:00:00", null));

        mockMvc.perform(get("/api/households/invitations/mine").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4));
    }

    // ── POST /invitations/{invitationId}/accept ───────────────────────────

    @Test
    void acceptInvitation_returnsHousehold_onSuccess() throws Exception {
        HouseholdInvitationEntity invitation = new HouseholdInvitationEntity();
        invitation.setId(3L);
        invitation.setHouseholdId(HOUSEHOLD_ID);
        given(invitationService.requireInvitation(3L)).willReturn(invitation);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(household(HOUSEHOLD_ID));
        given(householdService.toResponse(any(HouseholdEntity.class), eq(USER_ID))).willReturn(householdResponse(HOUSEHOLD_ID));

        mockMvc.perform(post("/api/households/invitations/3/accept").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(invitationService).accept(invitation, USER_ID);
    }

    @Test
    void acceptInvitation_returnsNotFound_whenInvitationMissing() throws Exception {
        given(invitationService.requireInvitation(3L)).willThrow(FamilyException.notFound("Invitation"));

        mockMvc.perform(post("/api/households/invitations/3/accept").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    // ── POST /invitations/{invitationId}/reject ───────────────────────────

    @Test
    void rejectInvitation_returnsNoContent_onSuccess() throws Exception {
        HouseholdInvitationEntity invitation = new HouseholdInvitationEntity();
        invitation.setId(3L);
        given(invitationService.requireInvitation(3L)).willReturn(invitation);

        mockMvc.perform(post("/api/households/invitations/3/reject").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(invitationService).reject(invitation, USER_ID);
    }

    // ── DELETE /invitations/{invitationId} ────────────────────────────────

    @Test
    void cancelInvitation_returnsNoContent_onSuccess() throws Exception {
        HouseholdInvitationEntity invitation = new HouseholdInvitationEntity();
        invitation.setId(3L);
        invitation.setHouseholdId(HOUSEHOLD_ID);
        given(invitationService.requireInvitation(3L)).willReturn(invitation);
        given(permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));

        mockMvc.perform(delete("/api/households/invitations/3").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(invitationService).cancel(invitation);
    }

    // ── POST /{id}/expenses ────────────────────────────────────────────────

    @Test
    void createSharedExpense_returnsCreated() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        SharedTransactionEntity saved = new SharedTransactionEntity();
        saved.setId(11L);
        given(sharedExpenseService.createSharedExpense(eq(h), eq(USER_ID), any())).willReturn(saved);
        given(sharedExpenseService.toResponse(saved)).willReturn(new SharedExpenseResponse(
                11L, HOUSEHOLD_ID, 100L, USER_ID, "Me", 50.0, "EQUAL", "Groceries", "Food", "2026-01-01", List.of(), "2026-01-01T00:00:00"));

        mockMvc.perform(post("/api/households/1/expenses").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":100,\"splitMethod\":\"EQUAL\",\"shares\":[{\"userId\":42}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));
    }

    // ── GET /{id}/expenses ─────────────────────────────────────────────────

    @Test
    void listSharedExpenses_returnsMappedList_withoutMonthFilter() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setId(11L);
        given(sharedExpenseService.listForHousehold(HOUSEHOLD_ID, null, null)).willReturn(List.of(tx));
        given(sharedExpenseService.toResponse(tx)).willReturn(new SharedExpenseResponse(
                11L, HOUSEHOLD_ID, 100L, USER_ID, "Me", 50.0, "EQUAL", "Groceries", "Food", "2026-01-01", List.of(), "2026-01-01T00:00:00"));

        mockMvc.perform(get("/api/households/1/expenses").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11));
    }

    @Test
    void listSharedExpenses_returnsBadRequest_forUnparsableMonth() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));

        mockMvc.perform(get("/api/households/1/expenses").requestAttr("userId", USER_ID).param("month", "not-a-month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("month must be in yyyy-MM format"));
    }

    @Test
    void listSharedExpenses_filtersByMonth_whenProvided() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        given(sharedExpenseService.listForHousehold(HOUSEHOLD_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/households/1/expenses").requestAttr("userId", USER_ID).param("month", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── DELETE /{id}/expenses/{expenseId} ──────────────────────────────────

    @Test
    void unshareExpense_returnsForbidden_whenNotAdminAndNotPayer() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setId(11L);
        tx.setPayerUserId(999L);
        given(sharedExpenseService.requireSharedTransaction(11L, HOUSEHOLD_ID)).willReturn(tx);

        mockMvc.perform(delete("/api/households/1/expenses/11").requestAttr("userId", USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void unshareExpense_returnsNoContent_whenCallerIsPayer() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setId(11L);
        tx.setPayerUserId(USER_ID);
        given(sharedExpenseService.requireSharedTransaction(11L, HOUSEHOLD_ID)).willReturn(tx);

        mockMvc.perform(delete("/api/households/1/expenses/11").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(sharedExpenseService).unshareExpense(tx);
    }

    // ── GET /{id}/settlements/summary ──────────────────────────────────────

    @Test
    void settlementSummary_returnsSummary() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(household(HOUSEHOLD_ID));
        given(settlementService.computeSummary(any(HouseholdEntity.class))).willReturn(
                new SettlementSummaryResponse("2026-01-01", "2026-01-31", List.of(), List.of()));

        mockMvc.perform(get("/api/households/1/settlements/summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-01-01"));
    }

    // ── POST /{id}/settlements ─────────────────────────────────────────────

    @Test
    void recordSettlement_returnsCreated() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        SettlementEntity saved = new SettlementEntity();
        saved.setId(6L);
        given(settlementService.recordSettlement(eq(h), eq(USER_ID), any())).willReturn(saved);
        given(settlementService.toResponse(saved)).willReturn(new SettlementResponse(
                6L, HOUSEHOLD_ID, USER_ID, "Me", 8L, "Jane", 20.0, null, "2026-01-01T00:00:00"));

        mockMvc.perform(post("/api/households/1/settlements").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":8,\"amount\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6));
    }

    @Test
    void recordSettlement_returnsBadRequest_whenAmountInvalid() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(household(HOUSEHOLD_ID));
        given(settlementService.recordSettlement(any(HouseholdEntity.class), eq(USER_ID), any()))
                .willThrow(FamilyException.badRequest("amount must be a positive number"));

        mockMvc.perform(post("/api/households/1/settlements").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":8,\"amount\":-1}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /{id}/settlements ──────────────────────────────────────────────

    @Test
    void settlementHistory_returnsMappedList() throws Exception {
        given(permissionService.requireMember(USER_ID, HOUSEHOLD_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "MEMBER"));
        SettlementEntity settlement = new SettlementEntity();
        settlement.setId(6L);
        given(settlementService.history(HOUSEHOLD_ID)).willReturn(List.of(settlement));
        given(settlementService.toResponse(settlement)).willReturn(new SettlementResponse(
                6L, HOUSEHOLD_ID, USER_ID, "Me", 8L, "Jane", 20.0, null, "2026-01-01T00:00:00"));

        mockMvc.perform(get("/api/households/1/settlements").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(6));
    }

    // ── GET /dashboard-summary ─────────────────────────────────────────────

    @Test
    void dashboardSummary_returnsNone_whenNoMembership() throws Exception {
        given(householdService.currentMembership(USER_ID)).willReturn(null);

        mockMvc.perform(get("/api/households/dashboard-summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHousehold").value(false));
    }

    @Test
    void dashboardSummary_returnsSummary_whenMember() throws Exception {
        given(householdService.currentMembership(USER_ID)).willReturn(member(HOUSEHOLD_ID, USER_ID, "ADMIN"));
        HouseholdEntity h = household(HOUSEHOLD_ID);
        given(householdService.requireHousehold(HOUSEHOLD_ID)).willReturn(h);
        given(settlementService.computeDashboardSummary(h, USER_ID)).willReturn(
                new FamilyDashboardSummaryResponse(true, "The Does", 2, 100.0, -20.0, 3));

        mockMvc.perform(get("/api/households/dashboard-summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHousehold").value(true))
                .andExpect(jsonPath("$.householdName").value("The Does"));
    }
}
