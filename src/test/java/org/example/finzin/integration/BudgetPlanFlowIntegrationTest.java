package org.example.finzin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP flow through BudgetPlanApiController + BudgetPlanService + real Postgres: create a
 * monthly budget plan, allocate a category budget, log a real expense against that category, and
 * confirm the plan's live-computed spent-vs-budgeted status reflects it.
 */
class BudgetPlanFlowIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void categorySpendIsReflectedInBudgetPlanStatus() throws Exception {
        RegisteredUser user = registerUser("budgetflow");

        Map<String, Object> categoryBody = new LinkedHashMap<>();
        categoryBody.put("name", "Dining Out " + uniqueSuffix());
        MvcResult categoryResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(categoryBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = bodyOf(categoryResult).get("id").asLong();

        YearMonth currentMonth = YearMonth.now();
        String period = currentMonth.toString();
        Map<String, Object> planBody = new LinkedHashMap<>();
        planBody.put("name", "Monthly Plan " + uniqueSuffix());
        planBody.put("periodType", "MONTH");
        planBody.put("period", period);
        planBody.put("startDate", currentMonth.atDay(1).toString());
        planBody.put("endDate", currentMonth.atEndOfMonth().toString());
        planBody.put("plannedIncome", 5000.0);
        planBody.put("plannedSavings", 500.0);
        planBody.put("notes", "Integration test plan");

        MvcResult planResult = mockMvc.perform(post("/api/budget-plans")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(planBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode planJson = bodyOf(planResult);
        long planId = planJson.get("id").asLong();
        assertEquals("ACTIVE", planJson.get("status").asText());

        Map<String, Object> categoryBudgetBody = new LinkedHashMap<>();
        categoryBudgetBody.put("categoryId", categoryId);
        categoryBudgetBody.put("amount", 1000.0);
        mockMvc.perform(post("/api/budget-plans/" + planId + "/categories")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(categoryBudgetBody)))
                .andExpect(status().isCreated());

        Map<String, Object> expenseBody = new LinkedHashMap<>();
        expenseBody.put("amount", 300.0);
        expenseBody.put("description", "Dinner with friends");
        expenseBody.put("category_id", categoryId);
        expenseBody.put("transaction_type", "expense");
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isCreated());

        MvcResult fullResult = mockMvc.perform(get("/api/budget-plans/" + planId + "/full")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode full = bodyOf(fullResult);

        JsonNode categoryStatus = null;
        for (JsonNode c : full.get("categories")) {
            if (c.get("categoryId").asLong() == categoryId) {
                categoryStatus = c;
                break;
            }
        }
        assertTrue(categoryStatus != null, "Expected a category status entry for the budgeted category");
        assertEquals(1000.0, categoryStatus.get("budgetAmount").asDouble(), 0.001);
        assertEquals(300.0, categoryStatus.get("actualAmount").asDouble(), 0.001);
        assertEquals(700.0, categoryStatus.get("remainingAmount").asDouble(), 0.001);
        assertEquals(30.0, categoryStatus.get("percentUsed").asDouble(), 0.001);
        assertEquals("ON_TRACK", categoryStatus.get("status").asText());

        JsonNode summary = full.get("summary");
        assertEquals(1000.0, summary.get("plannedExpense").asDouble(), 0.001);
        assertEquals(300.0, summary.get("actualExpense").asDouble(), 0.001);

        MvcResult currentResult = mockMvc.perform(get("/api/budget-plans/current")
                        .header("Authorization", user.authHeader())
                        .param("month", period))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode current = bodyOf(currentResult);
        assertTrue(current.get("hasCurrent").asBoolean());
        assertEquals(planId, current.get("id").asLong());
    }

    @Test
    void overspendingACategoryMarksItOverBudget() throws Exception {
        RegisteredUser user = registerUser("budgetover");

        Map<String, Object> categoryBody = new LinkedHashMap<>();
        categoryBody.put("name", "Entertainment " + uniqueSuffix());
        MvcResult categoryResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(categoryBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = bodyOf(categoryResult).get("id").asLong();

        YearMonth currentMonth = YearMonth.now();
        Map<String, Object> planBody = new LinkedHashMap<>();
        planBody.put("name", "Overspend Plan " + uniqueSuffix());
        planBody.put("periodType", "MONTH");
        planBody.put("period", currentMonth.toString());
        planBody.put("startDate", currentMonth.atDay(1).toString());
        planBody.put("endDate", currentMonth.atEndOfMonth().toString());
        planBody.put("plannedIncome", 2000.0);
        planBody.put("plannedSavings", 0.0);
        MvcResult planResult = mockMvc.perform(post("/api/budget-plans")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(planBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long planId = bodyOf(planResult).get("id").asLong();

        Map<String, Object> categoryBudgetBody = new LinkedHashMap<>();
        categoryBudgetBody.put("categoryId", categoryId);
        categoryBudgetBody.put("amount", 100.0);
        mockMvc.perform(post("/api/budget-plans/" + planId + "/categories")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(categoryBudgetBody)))
                .andExpect(status().isCreated());

        Map<String, Object> expenseBody = new LinkedHashMap<>();
        expenseBody.put("amount", 150.0);
        expenseBody.put("description", "Concert tickets");
        expenseBody.put("category_id", categoryId);
        expenseBody.put("transaction_type", "expense");
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isCreated());

        MvcResult fullResult = mockMvc.perform(get("/api/budget-plans/" + planId + "/full")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode categoryStatus = bodyOf(fullResult).get("categories").get(0);
        assertEquals("OVER_BUDGET", categoryStatus.get("status").asText());
        assertEquals(-50.0, categoryStatus.get("remainingAmount").asDouble(), 0.001);
    }
}
