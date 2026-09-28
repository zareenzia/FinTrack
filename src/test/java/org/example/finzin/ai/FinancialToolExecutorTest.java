package org.example.finzin.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.gamification.ChallengeService;
import org.example.finzin.gamification.GamificationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (no Spring context), matching {@code AIServiceTest}'s convention.
 *
 * SECURITY LOCK-IN: a prior audit confirmed that no tool call can be parameterized with another
 * user's id — userId is never a JSON-schema property on any tool definition, and {@link
 * FinancialToolExecutor#execute} takes userId as a plain Java parameter supplied by the caller,
 * completely independent of {@code argumentsJson}. Every dispatch test below deliberately smuggles
 * a {@code "userId": 999} key into the model-supplied arguments JSON and asserts the underlying
 * service is still invoked with the real (caller-supplied) user id — proving there is no argument
 * path, now or via a future careless edit, for the model to redirect a tool call at another user's
 * data. If this ever regresses (e.g. a tool starts reading "userId" out of the arguments), these
 * tests fail.
 */
@ExtendWith(MockitoExtension.class)
class FinancialToolExecutorTest {

    private static final Long REAL_USER_ID = 42L;
    private static final Long SMUGGLED_USER_ID = 999L;

    @Mock private FinancialContextService financialContextService;
    @Mock private FinancialHealthService financialHealthService;
    @Mock private InsightService insightService;
    @Mock private RecommendationService recommendationService;
    @Mock private MonthlyReportService monthlyReportService;
    @Mock private GamificationQueryService gamificationQueryService;
    @Mock private ChallengeService challengeService;

    private FinancialToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FinancialToolExecutor(financialContextService, financialHealthService, insightService,
                recommendationService, monthlyReportService, gamificationQueryService, challengeService, new ObjectMapper());
    }

    private String smuggledArgs(String extraFieldsJson) {
        if (extraFieldsJson == null || extraFieldsJson.isBlank()) {
            return "{\"userId\":" + SMUGGLED_USER_ID + "}";
        }
        return "{\"userId\":" + SMUGGLED_USER_ID + "," + extraFieldsJson + "}";
    }

    // ── security lock-in: no tool schema exposes userId as a parameter ──────────────────────

    @Test
    void noToolDefinitionEverExposesUserIdAsAModelSuppliedParameter() {
        List<Map<String, Object>> definitions = executor.getToolDefinitions();
        assertFalse(definitions.isEmpty());

        for (Map<String, Object> tool : definitions) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) tool.get("parameters");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) parameters.get("required");

            assertFalse(properties.containsKey("userId"),
                    "tool \"" + tool.get("name") + "\" must never expose userId as a schema property");
            assertFalse(required.contains("userId"),
                    "tool \"" + tool.get("name") + "\" must never require userId as a schema property");
        }
    }

    // ── security lock-in: every dispatch ignores any userId smuggled into arguments ─────────

    @Test
    void getAccountBalancesAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getAccountBalances", smuggledArgs(null), REAL_USER_ID);
        verify(financialContextService).getAccountBalances(REAL_USER_ID);
    }

    @Test
    void getMonthlyExpensesAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getMonthlyExpenses", smuggledArgs("\"month\":\"2026-01\""), REAL_USER_ID);
        verify(financialContextService).getMonthlyExpenses(REAL_USER_ID, "2026-01");
    }

    @Test
    void getExpenseByCategoryAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getExpenseByCategory", smuggledArgs("\"categoryName\":\"Food\""), REAL_USER_ID);
        verify(financialContextService).getExpenseByCategory(REAL_USER_ID, "Food", null);
    }

    @Test
    void getRecentTransactionsAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getRecentTransactions", smuggledArgs("\"limit\":5"), REAL_USER_ID);
        verify(financialContextService).getRecentTransactions(REAL_USER_ID, 5);
    }

    @Test
    void getBudgetStatusAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getBudgetStatus", smuggledArgs(null), REAL_USER_ID);
        verify(financialContextService).getBudgetStatus(REAL_USER_ID);
    }

    @Test
    void getNetWorthAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getNetWorth", smuggledArgs(null), REAL_USER_ID);
        verify(financialContextService).getNetWorth(REAL_USER_ID);
    }

    @Test
    void getSavingsAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getSavings", smuggledArgs(null), REAL_USER_ID);
        verify(financialContextService).getSavings(REAL_USER_ID);
    }

    @Test
    void getAssetsAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getAssets", smuggledArgs(null), REAL_USER_ID);
        verify(financialContextService).getAssets(REAL_USER_ID);
    }

    @Test
    void getMonthComparisonAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getMonthComparison", smuggledArgs("\"monthA\":\"2026-01\",\"monthB\":\"2026-02\""), REAL_USER_ID);
        verify(financialContextService).getMonthComparison(REAL_USER_ID, "2026-01", "2026-02");
    }

    @Test
    void getFinancialHealthAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        when(financialHealthService.calculate(REAL_USER_ID)).thenReturn(healthFixture());
        executor.execute("getFinancialHealth", smuggledArgs(null), REAL_USER_ID);
        verify(financialHealthService).calculate(REAL_USER_ID);
    }

    @Test
    void getInsightsAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getInsights", smuggledArgs(null), REAL_USER_ID);
        verify(insightService).generateInsights(REAL_USER_ID);
    }

    @Test
    void getRecommendationsAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getRecommendations", smuggledArgs(null), REAL_USER_ID);
        verify(recommendationService).generateRecommendations(REAL_USER_ID);
    }

    @Test
    void getBudgetCoachAdviceAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        when(recommendationService.getBudgetCoachAdvice(REAL_USER_ID))
                .thenReturn(new RecommendationService.BudgetCoachAdvice(false, null, null, null, null, List.of()));
        executor.execute("getBudgetCoachAdvice", smuggledArgs(null), REAL_USER_ID);
        verify(recommendationService).getBudgetCoachAdvice(REAL_USER_ID);
    }

    @Test
    void getSavingsCoachAdviceAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        when(recommendationService.getSavingsCoachAdvice(REAL_USER_ID))
                .thenReturn(new RecommendationService.SavingsCoachAdvice(0, null, 0, 0, List.of()));
        executor.execute("getSavingsCoachAdvice", smuggledArgs(null), REAL_USER_ID);
        verify(recommendationService).getSavingsCoachAdvice(REAL_USER_ID);
    }

    @Test
    void getMonthlyReportAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        when(monthlyReportService.generate(REAL_USER_ID, "2026-02")).thenReturn(
                new MonthlyReportService.MonthlyReport("2026-02", Map.of(), null, Map.of(), List.of(), List.of(),
                        healthFixture(), List.of(), List.of()));
        executor.execute("getMonthlyReport", smuggledArgs("\"month\":\"2026-02\""), REAL_USER_ID);
        verify(monthlyReportService).generate(REAL_USER_ID, "2026-02");
    }

    @Test
    void getGamificationStatusAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getGamificationStatus", smuggledArgs(null), REAL_USER_ID);
        verify(gamificationQueryService).summary(REAL_USER_ID);
    }

    @Test
    void getAchievementProgressAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("getAchievementProgress", smuggledArgs("\"limit\":3"), REAL_USER_ID);
        verify(gamificationQueryService).nearestToUnlocking(REAL_USER_ID, 3);
    }

    @Test
    void suggestChallengesAlwaysUsesTheCallerSuppliedUserIdNeverASmuggledOne() {
        executor.execute("suggestChallenges", smuggledArgs(null), REAL_USER_ID);
        verify(challengeService).getCurrentChallengesWithDefinitions(REAL_USER_ID);
    }

    // ── general dispatch behavior ────────────────────────────────────────────────────────────

    @Test
    void unknownToolNameReturnsAnErrorPayloadRatherThanThrowing() {
        Map<String, Object> result = executor.execute("someToolThatDoesNotExist", "{}", REAL_USER_ID);

        assertEquals("Unknown tool: someToolThatDoesNotExist", result.get("error"));
    }

    @Test
    void exceptionFromTheUnderlyingServiceIsSwallowedIntoAGenericErrorPayload() {
        when(financialContextService.getAccountBalances(REAL_USER_ID)).thenThrow(new RuntimeException("DB is down"));

        Map<String, Object> result = executor.execute("getAccountBalances", "{}", REAL_USER_ID);

        assertEquals("Failed to retrieve that information right now.", result.get("error"));
    }

    @Test
    void blankArgumentsJsonIsTreatedAsAnEmptyObjectRatherThanThrowing() {
        Map<String, Object> result = executor.execute("getAccountBalances", "", REAL_USER_ID);

        assertTrue(result.containsKey("accounts"));
        verify(financialContextService).getAccountBalances(REAL_USER_ID);
    }

    @Test
    void nullArgumentsJsonIsTreatedAsAnEmptyObjectRatherThanThrowing() {
        executor.execute("getAccountBalances", null, REAL_USER_ID);
        verify(financialContextService).getAccountBalances(REAL_USER_ID);
    }

    @Test
    void getRecentTransactionsFallsBackToDefaultLimitWhenArgumentIsNotAnInteger() {
        executor.execute("getRecentTransactions", "{\"limit\":\"not-a-number\"}", REAL_USER_ID);
        verify(financialContextService).getRecentTransactions(REAL_USER_ID, 10);
    }

    private FinancialHealthService.FinancialHealth healthFixture() {
        return new FinancialHealthService.FinancialHealth(20.0, 50.0, 80.0, 75.0, 60.0, 5.0, 10.0, "growing", 72, Map.of());
    }
}
