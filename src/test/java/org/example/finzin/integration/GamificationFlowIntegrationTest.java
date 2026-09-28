package org.example.finzin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP flow through GamificationController + the real JwtAuthFilter/GamificationEventListener
 * chain + real Postgres.
 *
 * <p>Two distinct real triggers are exercised: (1) {@code JwtAuthFilter.markDailyActive} fires a
 * {@code DAILY_ACTIVE} gamification event on the very first authenticated request a fresh user
 * makes each day (awarding a flat daily-login XP and starting their activity streak at 1) — so the
 * first authenticated call in this test (GET /summary) already changes gamification state before
 * the controller method itself runs; and (2) logging a real transaction fires a
 * {@code TRANSACTION_LOGGED} event (AFTER_COMMIT), which awards per-action XP and — since this is
 * the user's very first ever transaction — unlocks the real seeded "TXN_FIRST" achievement
 * (see DatabaseMigration's achievement_definitions seed, threshold 1 on metric transactions.count),
 * awarding its XP reward too.
 */
class GamificationFlowIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void dailyActiveAndFirstTransactionAwardXpAndUnlockAchievement() throws Exception {
        RegisteredUser user = registerUser("gamer");

        // First authenticated request of the day for this brand-new user — triggers DAILY_ACTIVE.
        MvcResult afterLoginResult = mockMvc.perform(get("/api/gamification/summary")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterLogin = bodyOf(afterLoginResult);
        assertTrue(afterLogin.get("enabled").asBoolean());
        assertEquals(5, afterLogin.get("totalXp").asLong());
        assertEquals(1, afterLogin.get("currentStreak").asInt());
        assertEquals(0, afterLogin.get("achievementsUnlocked").asLong());
        assertTrue(afterLogin.get("achievementsTotal").asLong() > 0);

        long categoryId = createCategory(user, "Salary" + uniqueSuffix());
        Map<String, Object> transactionBody = new LinkedHashMap<>();
        transactionBody.put("amount", 100.0);
        transactionBody.put("description", "First paycheck");
        transactionBody.put("category_id", categoryId);
        transactionBody.put("transaction_type", "income");
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", user.authHeader())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(transactionBody)))
                .andExpect(status().isCreated());

        MvcResult afterTxResult = mockMvc.perform(get("/api/gamification/summary")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterTx = bodyOf(afterTxResult);
        // 5 (daily login) + 3 (income logged) + 10 (TXN_FIRST achievement reward) = 18
        assertEquals(18, afterTx.get("totalXp").asLong());
        assertEquals(1, afterTx.get("achievementsUnlocked").asLong());

        MvcResult achievementsResult = mockMvc.perform(get("/api/gamification/achievements")
                        .header("Authorization", user.authHeader())
                        .param("category", "Transactions"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode achievements = bodyOf(achievementsResult);
        JsonNode txFirst = null;
        for (JsonNode a : achievements) {
            if ("TXN_FIRST".equals(a.get("code").asText())) {
                txFirst = a;
                break;
            }
        }
        assertTrue(txFirst != null, "Expected the seeded TXN_FIRST achievement definition to be present");
        assertEquals("UNLOCKED", txFirst.get("status").asText());

        MvcResult historyResult = mockMvc.perform(get("/api/gamification/history")
                        .header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode history = bodyOf(historyResult);
        boolean hasDailyLogin = false;
        boolean hasIncomeLogged = false;
        boolean hasAchievementUnlocked = false;
        for (JsonNode entry : history) {
            String reason = entry.get("reason").asText();
            if ("DAILY_LOGIN".equals(reason)) hasDailyLogin = true;
            if ("INCOME_LOGGED".equals(reason)) hasIncomeLogged = true;
            if ("ACHIEVEMENT_UNLOCKED".equals(reason)) hasAchievementUnlocked = true;
        }
        assertTrue(hasDailyLogin, "Expected a DAILY_LOGIN xp history entry");
        assertTrue(hasIncomeLogged, "Expected an INCOME_LOGGED xp history entry");
        assertTrue(hasAchievementUnlocked, "Expected an ACHIEVEMENT_UNLOCKED xp history entry");
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
