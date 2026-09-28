package org.example.finzin.ai;

import org.example.finzin.config.JwtAuthFilter;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AICoachApiController}. This controller has no validation or not-found
 * branches — every endpoint just reads {@code userId} off the request and delegates straight to a
 * mocked service, so the coverage here is: happy path shape/wiring per endpoint, plus one
 * representative check that the shared {@code getUserId(request)} helper falls back to user id 1L
 * when the JwtAuthFilter never set the "userId" request attribute (which is the actual behavior in
 * this codebase — there is no 401 path here).
 */
@WebMvcTest(controllers = AICoachApiController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class AICoachApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private FinancialHealthService financialHealthService;
    @MockitoBean private InsightService insightService;
    @MockitoBean private RecommendationService recommendationService;
    @MockitoBean private MonthlyReportService monthlyReportService;
    @MockitoBean private DashboardSummaryService dashboardSummaryService;

    @Test
    void getHealth_returnsCalculatedHealth_whenAuthenticated() throws Exception {
        FinancialHealthService.FinancialHealth health = new FinancialHealthService.FinancialHealth(
                12.5, 45.0, 80.0, 70.0, 55.0, 3.0, 4.0, "growing", 82, Map.of("income", 10.0));
        given(financialHealthService.calculate(42L)).willReturn(health);

        mockMvc.perform(get("/api/ai/health").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallHealthScore").value(82))
                .andExpect(jsonPath("$.growthNote").value("growing"));

        verify(financialHealthService).calculate(42L);
    }

    @Test
    void getHealth_defaultsToUserOne_whenUserIdAttributeMissing() throws Exception {
        FinancialHealthService.FinancialHealth health = new FinancialHealthService.FinancialHealth(
                0, 0, 0, 0, null, null, null, null, 0, Map.of());
        given(financialHealthService.calculate(1L)).willReturn(health);

        mockMvc.perform(get("/api/ai/health"))
                .andExpect(status().isOk());

        verify(financialHealthService).calculate(1L);
    }

    @Test
    void getInsights_wrapsServiceListUnderInsightsKey() throws Exception {
        InsightService.Insight insight = new InsightService.Insight(
                "Title", "Desc", "high", "food", java.time.LocalDate.of(2026, 1, 1), "budget", 0.9);
        given(insightService.generateInsights(42L)).willReturn(List.of(insight));

        mockMvc.perform(get("/api/ai/insights").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights[0].title").value("Title"))
                .andExpect(jsonPath("$.insights[0].priority").value("high"));
    }

    @Test
    void getRecommendations_wrapsServiceListUnderRecommendationsKey() throws Exception {
        RecommendationService.Recommendation rec = new RecommendationService.Recommendation(
                "Cut back", "You overspent", "medium", "dining", "evidence");
        given(recommendationService.generateRecommendations(42L)).willReturn(List.of(rec));

        mockMvc.perform(get("/api/ai/recommendations").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].title").value("Cut back"));
    }

    @Test
    void getBudgetCoachAdvice_returnsAdviceFromService() throws Exception {
        RecommendationService.BudgetCoachAdvice advice = new RecommendationService.BudgetCoachAdvice(
                true, "My Plan", "monthly", 60.0, 10, List.of());
        given(recommendationService.getBudgetCoachAdvice(42L)).willReturn(advice);

        mockMvc.perform(get("/api/ai/budget-coach").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBudget").value(true))
                .andExpect(jsonPath("$.planName").value("My Plan"));
    }

    @Test
    void getSavingsCoachAdvice_returnsAdviceFromService() throws Exception {
        RecommendationService.SavingsCoachAdvice advice = new RecommendationService.SavingsCoachAdvice(
                500.0, 5.0, 1000.0, 50.0, List.of());
        given(recommendationService.getSavingsCoachAdvice(42L)).willReturn(advice);

        mockMvc.perform(get("/api/ai/savings-coach").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSavingsContributed").value(500.0));
    }

    @Test
    void getDashboardSummary_returnsSummaryFromService() throws Exception {
        DashboardSummaryService.DashboardSummary summary = new DashboardSummaryService.DashboardSummary(
                true, "Insight text", Map.of(), Map.of(), Map.of(), "Recommendation text", 75);
        given(dashboardSummaryService.summarize(42L)).willReturn(summary);

        mockMvc.perform(get("/api/ai/dashboard-summary").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.healthScore").value(75));
    }

    @Test
    void getMonthlyReport_passesMonthQueryParamThrough() throws Exception {
        MonthlyReportService.MonthlyReport report = new MonthlyReportService.MonthlyReport(
                "2026-01", Map.of(), 2.0, Map.of(), List.of(), List.of(), null, List.of(), List.of());
        given(monthlyReportService.generate(eq(42L), eq("2026-01"))).willReturn(report);

        mockMvc.perform(get("/api/ai/monthly-report").param("month", "2026-01").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-01"));

        verify(monthlyReportService).generate(42L, "2026-01");
    }

    @Test
    void getMonthlyReport_passesNullMonth_whenQueryParamOmitted() throws Exception {
        MonthlyReportService.MonthlyReport report = new MonthlyReportService.MonthlyReport(
                "current", Map.of(), null, Map.of(), List.of(), List.of(), null, List.of(), List.of());
        given(monthlyReportService.generate(eq(42L), eq(null))).willReturn(report);

        mockMvc.perform(get("/api/ai/monthly-report").requestAttr("userId", 42L))
                .andExpect(status().isOk());

        verify(monthlyReportService).generate(42L, null);
    }
}
