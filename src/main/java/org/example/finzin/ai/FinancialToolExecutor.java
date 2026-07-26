package org.example.finzin.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.gamification.ChallengeService;
import org.example.finzin.gamification.GamificationQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the tool JSON-schema list handed to OpenAI (single source of truth, so declared tools
 * never drift from what's actually executable) and dispatches tool calls to
 * {@link FinancialContextService} and the Phase 2C coach services.
 *
 * SECURITY: every dispatch takes {@code userId} as a plain Java parameter supplied by the
 * caller (ultimately the JWT-derived request attribute in AIController) — userId is never a
 * property in any tool's JSON schema, so there is no argument path for the model to request
 * another user's data.
 */
@Component
public class FinancialToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(FinancialToolExecutor.class);

    private final FinancialContextService financialContextService;
    private final FinancialHealthService financialHealthService;
    private final InsightService insightService;
    private final RecommendationService recommendationService;
    private final MonthlyReportService monthlyReportService;
    private final GamificationQueryService gamificationQueryService;
    private final ChallengeService challengeService;
    private final ObjectMapper objectMapper;

    public FinancialToolExecutor(FinancialContextService financialContextService, FinancialHealthService financialHealthService,
                                  InsightService insightService, RecommendationService recommendationService,
                                  MonthlyReportService monthlyReportService, GamificationQueryService gamificationQueryService,
                                  ChallengeService challengeService, ObjectMapper objectMapper) {
        this.financialContextService = financialContextService;
        this.financialHealthService = financialHealthService;
        this.insightService = insightService;
        this.recommendationService = recommendationService;
        this.monthlyReportService = monthlyReportService;
        this.gamificationQueryService = gamificationQueryService;
        this.challengeService = challengeService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> getToolDefinitions() {
        return List.of(
                tool("getAccountBalances", "Returns the user's accounts (bank, cash, MFS, credit card) with current balances.",
                        Map.of()),
                tool("getMonthlyExpenses", "Returns total income/expense/savings and net for a given calendar month.",
                        Map.of("month", strParam("Month in YYYY-MM format. Omit for the current month.")), List.of()),
                tool("getExpenseByCategory", "Returns how much the user spent in a specific category, optionally for a given month.",
                        Map.of("categoryName", strParam("Category name, e.g. \"Food\" or \"Transportation\"."),
                               "month", strParam("Month in YYYY-MM format. Omit for the current month.")),
                        List.of("categoryName")),
                tool("getRecentTransactions", "Returns the user's most recent transactions.",
                        Map.of("limit", intParam("How many transactions to return (default 10, max 50).")), List.of()),
                tool("getBudgetStatus", "Returns the user's current active budget plan, category-by-category status, savings goals, and budget score.",
                        Map.of()),
                tool("getNetWorth", "Returns the user's net worth, available balance, total savings contributed, and total assets.",
                        Map.of()),
                tool("getSavings", "Returns how much the user has saved in total and the status of any active savings goals.",
                        Map.of()),
                tool("getAssets", "Returns the user's gold/jewelry assets, individually and totaled by value and weight.",
                        Map.of()),
                tool("getMonthComparison", "Compares income/expense/savings/net between two calendar months.",
                        Map.of("monthA", strParam("First month, YYYY-MM format. Omit for the current month."),
                               "monthB", strParam("Second month, YYYY-MM format. Omit for the current month.")),
                        List.of()),
                tool("getFinancialHealth", "Returns the user's financial health metrics: savings rate, expense ratio, income/cash-flow " +
                                "stability, budget utilization, asset and net worth growth, and an overall 0-100 health score with its breakdown.",
                        Map.of()),
                tool("getInsights", "Returns generated insights about notable spending/income/savings trends and changes (month-over-month and vs. trailing average).",
                        Map.of()),
                tool("getRecommendations", "Returns evidence-cited personalized recommendations (e.g. reduce a category's spending, build an emergency fund).",
                        Map.of()),
                tool("getBudgetCoachAdvice", "Returns remaining budget, overspending flags, and safe daily spending allowance for the user's current budget plan.",
                        Map.of()),
                tool("getSavingsCoachAdvice", "Returns savings trend, emergency fund target/progress, and savings goal status.",
                        Map.of()),
                tool("getMonthlyReport", "Returns a structured monthly report: income/expense/savings summary, asset growth, budget performance, " +
                                "category analysis, top purchases, financial health, recommendations, and goals for next month.",
                        Map.of("month", strParam("Month in YYYY-MM format. Omit for the current month.")), List.of()),
                tool("getGamificationStatus", "Returns the user's gamification status: level, XP, XP needed for the next level, " +
                                "daily-active streak, and how many achievements they've unlocked out of the total.",
                        Map.of()),
                tool("getAchievementProgress", "Returns the achievements the user is closest to unlocking next, with their current progress " +
                                "toward each one's threshold. Use this to answer \"what achievements am I close to?\" or \"how do I level up?\".",
                        Map.of("limit", intParam("How many nearest achievements to return (default 5, max 20).")), List.of()),
                tool("suggestChallenges", "Returns the user's active monthly challenges and current progress toward each — use this to suggest " +
                                "what the user could focus on this month.",
                        Map.of())
        );
    }

    /** Dispatches a single tool call by name. Never throws — tool-level failures become an {"error": ...} payload for the model. */
    public Map<String, Object> execute(String toolName, String argumentsJson, Long userId) {
        try {
            JsonNode args = (argumentsJson == null || argumentsJson.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(argumentsJson);
            return switch (toolName) {
                case "getAccountBalances" -> Map.of("accounts", financialContextService.getAccountBalances(userId));
                case "getMonthlyExpenses" -> financialContextService.getMonthlyExpenses(userId, textOrNull(args, "month"));
                case "getExpenseByCategory" -> financialContextService.getExpenseByCategory(userId, textOrNull(args, "categoryName"), textOrNull(args, "month"));
                case "getRecentTransactions" -> Map.of("transactions", financialContextService.getRecentTransactions(userId, intOrDefault(args, "limit", 10)));
                case "getBudgetStatus" -> financialContextService.getBudgetStatus(userId);
                case "getNetWorth" -> financialContextService.getNetWorth(userId);
                case "getSavings" -> financialContextService.getSavings(userId);
                case "getAssets" -> financialContextService.getAssets(userId);
                case "getMonthComparison" -> financialContextService.getMonthComparison(userId, textOrNull(args, "monthA"), textOrNull(args, "monthB"));
                case "getFinancialHealth" -> toMap(financialHealthService.calculate(userId));
                case "getInsights" -> Map.of("insights", insightService.generateInsights(userId));
                case "getRecommendations" -> Map.of("recommendations", recommendationService.generateRecommendations(userId));
                case "getBudgetCoachAdvice" -> toMap(recommendationService.getBudgetCoachAdvice(userId));
                case "getSavingsCoachAdvice" -> toMap(recommendationService.getSavingsCoachAdvice(userId));
                case "getMonthlyReport" -> toMap(monthlyReportService.generate(userId, textOrNull(args, "month")));
                case "getGamificationStatus" -> gamificationQueryService.summary(userId);
                case "getAchievementProgress" -> Map.of("achievements", gamificationQueryService.nearestToUnlocking(userId, intOrDefault(args, "limit", 5)));
                case "suggestChallenges" -> Map.of("challenges", challengeService.getCurrentChallengesWithDefinitions(userId));
                default -> Map.of("error", "Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.warn("Tool execution failed toolName={} errorType={}", toolName, e.getClass().getSimpleName());
            return Map.of("error", "Failed to retrieve that information right now.");
        }
    }

    /** Converts a record result into a Map so every branch of {@link #execute} returns a uniform type. */
    private Map<String, Object> toMap(Object record) {
        return objectMapper.convertValue(record, new TypeReference<Map<String, Object>>() {});
    }

    private static String textOrNull(JsonNode args, String field) {
        JsonNode node = args.get(field);
        return (node == null || node.isNull()) ? null : node.asText(null);
    }

    private static int intOrDefault(JsonNode args, String field, int defaultValue) {
        JsonNode node = args.get(field);
        return (node == null || node.isNull() || !node.canConvertToInt()) ? defaultValue : node.asInt();
    }

    private static Map<String, Object> strParam(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> intParam(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        return tool(name, description, properties, List.copyOf(properties.keySet()));
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        parameters.put("additionalProperties", false);

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("type", "function");
        def.put("name", name);
        def.put("description", description);
        def.put("parameters", parameters);
        def.put("strict", false); // "limit" is optional in getRecentTransactions; strict mode would require all properties
        return def;
    }
}
