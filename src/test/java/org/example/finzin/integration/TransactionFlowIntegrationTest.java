package org.example.finzin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP flow through AccountApiController + FinanceApiController + AccountBalanceService +
 * real Postgres: create an account, log income/expense transactions against it, and confirm the
 * account's currentBalance and the transaction list reflect exactly what was posted.
 */
class TransactionFlowIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void creatingIncomeAndExpenseTransactionsUpdatesAccountBalance() throws Exception {
        RegisteredUser user = registerUser("txnflow");

        Map<String, Object> accountBody = new LinkedHashMap<>();
        accountBody.put("accountType", "BANK");
        accountBody.put("accountNickname", "Main Checking");
        accountBody.put("bankName", "Test Bank");
        accountBody.put("openingBalance", 1000.0);

        MvcResult accountResult = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(accountBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode accountJson = bodyOf(accountResult);
        long accountId = accountJson.get("id").asLong();
        assertEquals(1000.0, accountJson.get("currentBalance").asDouble(), 0.001);

        long salaryCategoryId = createCategory(user, "Salary" + uniqueSuffix());
        long groceriesCategoryId = createCategory(user, "Groceries" + uniqueSuffix());

        Map<String, Object> incomeBody = new LinkedHashMap<>();
        incomeBody.put("amount", 500.0);
        incomeBody.put("description", "Paycheck");
        incomeBody.put("category_id", salaryCategoryId);
        incomeBody.put("transaction_type", "income");
        incomeBody.put("sourceAccountId", accountId);

        MvcResult incomeResult = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(incomeBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode incomeJson = bodyOf(incomeResult);
        assertEquals("income", incomeJson.get("transaction_type").asText());
        assertEquals(500.0, incomeJson.get("amount").asDouble(), 0.001);

        Map<String, Object> expenseBody = new LinkedHashMap<>();
        expenseBody.put("amount", 200.0);
        expenseBody.put("description", "Grocery shopping");
        expenseBody.put("category_id", groceriesCategoryId);
        expenseBody.put("transaction_type", "expense");
        expenseBody.put("sourceAccountId", accountId);

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isCreated());

        // 1000 opening + 500 income - 200 expense = 1300
        MvcResult accountsListResult = mockMvc.perform(get("/api/accounts")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode accountsList = bodyOf(accountsListResult);
        JsonNode updatedAccount = findById(accountsList, accountId);
        assertEquals(1300.0, updatedAccount.get("currentBalance").asDouble(), 0.001);

        MvcResult allTxResult = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode allTx = bodyOf(allTxResult);
        assertEquals(2, allTx.size());

        MvcResult expenseOnlyResult = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .param("type", "expense"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode expenseOnly = bodyOf(expenseOnlyResult);
        assertEquals(1, expenseOnly.size());
        assertEquals("expense", expenseOnly.get(0).get("transaction_type").asText());
        assertEquals(200.0, expenseOnly.get(0).get("amount").asDouble(), 0.001);

        MvcResult byCategoryResult = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .param("category_id", String.valueOf(salaryCategoryId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode byCategory = bodyOf(byCategoryResult);
        assertEquals(1, byCategory.size());
        assertEquals("Paycheck", byCategory.get(0).get("description").asText());
    }

    @Test
    void deletingAnAccountWithTransactionsIsRejected() throws Exception {
        RegisteredUser user = registerUser("txndel");

        Map<String, Object> accountBody = new LinkedHashMap<>();
        accountBody.put("accountType", "CASH");
        accountBody.put("accountNickname", "Wallet");
        MvcResult accountResult = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(accountBody)))
                .andExpect(status().isCreated())
                .andReturn();
        long accountId = bodyOf(accountResult).get("id").asLong();

        long categoryId = createCategory(user, "Misc" + uniqueSuffix());
        Map<String, Object> expenseBody = new LinkedHashMap<>();
        expenseBody.put("amount", 50.0);
        expenseBody.put("description", "Coffee");
        expenseBody.put("category_id", categoryId);
        expenseBody.put("transaction_type", "expense");
        expenseBody.put("sourceAccountId", accountId);
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isCreated());

        MvcResult deleteResult = mockMvc.perform(delete("/api/accounts/" + accountId)
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(bodyOf(deleteResult).get("error").asText().toLowerCase().contains("transaction"));
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

    private JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) return node;
        }
        throw new AssertionError("No element with id " + id + " found in " + array);
    }
}
