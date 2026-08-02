package org.example.finzin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP flow through FamilyController + HouseholdService/InvitationService/SharedExpenseService
 * + real Postgres: user A creates a household, invites user B by email, user B accepts, user A logs
 * a personal transaction and shares it into the household ledger, and both members can see it.
 */
class FamilyFlowIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void invitationAcceptanceAndSharedExpenseAreVisibleToBothMembers() throws Exception {
        RegisteredUser owner = registerUser("famowner");
        RegisteredUser invitee = registerUser("faminvitee");

        Map<String, Object> createHouseholdBody = Map.of("name", "The Test Household " + uniqueSuffix());
        MvcResult createResult = mockMvc.perform(post("/api/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(createHouseholdBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode household = bodyOf(createResult);
        long householdId = household.get("id").asLong();
        assertTrue(household.get("hasHousehold").asBoolean());
        assertEquals("ADMIN", household.get("myRole").asText());

        Map<String, Object> inviteBody = new LinkedHashMap<>();
        inviteBody.put("emailOrUsername", invitee.email());
        inviteBody.put("relationshipLabel", "Sibling");
        MvcResult inviteResult = mockMvc.perform(post("/api/households/" + householdId + "/invitations")
                        .header("Authorization", owner.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(inviteBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode invitation = bodyOf(inviteResult);
        long invitationId = invitation.get("id").asLong();
        assertEquals("PENDING", invitation.get("status").asText());
        assertEquals(invitee.userId(), invitation.get("inviteeUserId").asLong());

        MvcResult pendingForInviteeResult = mockMvc.perform(get("/api/households/invitations/mine")
                        .header("Authorization", invitee.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pendingForInvitee = bodyOf(pendingForInviteeResult);
        assertEquals(1, pendingForInvitee.size());
        assertEquals(invitationId, pendingForInvitee.get(0).get("id").asLong());

        MvcResult acceptResult = mockMvc.perform(post("/api/households/invitations/" + invitationId + "/accept")
                        .header("Authorization", invitee.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode acceptedHousehold = bodyOf(acceptResult);
        assertEquals("MEMBER", acceptedHousehold.get("myRole").asText());
        assertEquals(2, acceptedHousehold.get("members").size());

        // Owner's own view now also shows both members.
        MvcResult mineResult = mockMvc.perform(get("/api/households/mine")
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(2, bodyOf(mineResult).get("members").size());

        long categoryId = createCategory(owner, "Rent" + uniqueSuffix());
        Map<String, Object> transactionBody = new LinkedHashMap<>();
        transactionBody.put("amount", 1000.0);
        transactionBody.put("description", "Shared Rent");
        transactionBody.put("category_id", categoryId);
        transactionBody.put("transaction_type", "expense");
        MvcResult transactionResult = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", owner.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(transactionBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long transactionId = bodyOf(transactionResult).get("id").asLong();

        Map<String, Object> shareOwner = new LinkedHashMap<>();
        shareOwner.put("userId", owner.userId());
        Map<String, Object> shareInvitee = new LinkedHashMap<>();
        shareInvitee.put("userId", invitee.userId());

        Map<String, Object> sharedExpenseBody = new LinkedHashMap<>();
        sharedExpenseBody.put("transactionId", transactionId);
        sharedExpenseBody.put("splitMethod", "EQUAL");
        sharedExpenseBody.put("shares", List.of(shareOwner, shareInvitee));

        MvcResult sharedExpenseResult = mockMvc.perform(post("/api/households/" + householdId + "/expenses")
                        .header("Authorization", owner.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(sharedExpenseBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode sharedExpense = bodyOf(sharedExpenseResult);
        assertEquals(1000.0, sharedExpense.get("totalAmount").asDouble(), 0.001);
        assertEquals(2, sharedExpense.get("shares").size());
        double shareSum = 0;
        for (JsonNode share : sharedExpense.get("shares")) {
            shareSum += share.get("shareAmount").asDouble();
        }
        assertEquals(1000.0, shareSum, 0.001);

        // Both members can see the shared expense in the household's ledger.
        MvcResult ownerViewResult = mockMvc.perform(get("/api/households/" + householdId + "/expenses")
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, bodyOf(ownerViewResult).size());

        MvcResult inviteeViewResult = mockMvc.perform(get("/api/households/" + householdId + "/expenses")
                        .header("Authorization", invitee.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode inviteeView = bodyOf(inviteeViewResult);
        assertEquals(1, inviteeView.size());
        assertEquals("Shared Rent", inviteeView.get(0).get("description").asText());
    }

    @Test
    void nonMemberCannotSeeHouseholdInvitationsOrExpenses() throws Exception {
        RegisteredUser owner = registerUser("famsec");
        RegisteredUser outsider = registerUser("famoutsider");

        Map<String, Object> createHouseholdBody = Map.of("name", "Private Household " + uniqueSuffix());
        MvcResult createResult = mockMvc.perform(post("/api/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(createHouseholdBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long householdId = bodyOf(createResult).get("id").asLong();

        mockMvc.perform(get("/api/households/" + householdId + "/expenses")
                        .header("Authorization", outsider.authHeader()))
                .andExpect(status().isForbidden());

        Map<String, Object> inviteBody = new LinkedHashMap<>();
        inviteBody.put("emailOrUsername", outsider.email());
        mockMvc.perform(post("/api/households/" + householdId + "/invitations")
                        .header("Authorization", outsider.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(inviteBody)))
                .andExpect(status().isForbidden());
    }

    private long createCategory(RegisteredUser user, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return bodyOf(result).get("id").asLong();
    }
}
