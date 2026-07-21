package org.example.finzin.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST surface for the Phase 2C AI Financial Coach services — kept separate from
 * {@link AIController} so that one stays scoped to chat/conversations.
 */
@RestController
@RequestMapping("/api/ai")
public class AICoachApiController {

    private final FinancialHealthService financialHealthService;
    private final InsightService insightService;
    private final RecommendationService recommendationService;
    private final MonthlyReportService monthlyReportService;
    private final DashboardSummaryService dashboardSummaryService;

    public AICoachApiController(FinancialHealthService financialHealthService, InsightService insightService,
                                 RecommendationService recommendationService, MonthlyReportService monthlyReportService,
                                 DashboardSummaryService dashboardSummaryService) {
        this.financialHealthService = financialHealthService;
        this.insightService = insightService;
        this.recommendationService = recommendationService;
        this.monthlyReportService = monthlyReportService;
        this.dashboardSummaryService = dashboardSummaryService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping("/health")
    public FinancialHealthService.FinancialHealth getHealth(HttpServletRequest request) {
        return financialHealthService.calculate(getUserId(request));
    }

    @GetMapping("/insights")
    public Map<String, Object> getInsights(HttpServletRequest request) {
        return Map.of("insights", insightService.generateInsights(getUserId(request)));
    }

    @GetMapping("/recommendations")
    public Map<String, Object> getRecommendations(HttpServletRequest request) {
        return Map.of("recommendations", recommendationService.generateRecommendations(getUserId(request)));
    }

    @GetMapping("/budget-coach")
    public RecommendationService.BudgetCoachAdvice getBudgetCoachAdvice(HttpServletRequest request) {
        return recommendationService.getBudgetCoachAdvice(getUserId(request));
    }

    @GetMapping("/savings-coach")
    public RecommendationService.SavingsCoachAdvice getSavingsCoachAdvice(HttpServletRequest request) {
        return recommendationService.getSavingsCoachAdvice(getUserId(request));
    }

    @GetMapping("/dashboard-summary")
    public DashboardSummaryService.DashboardSummary getDashboardSummary(HttpServletRequest request) {
        return dashboardSummaryService.summarize(getUserId(request));
    }

    @GetMapping("/monthly-report")
    public MonthlyReportService.MonthlyReport getMonthlyReport(HttpServletRequest request, @RequestParam(required = false) String month) {
        return monthlyReportService.generate(getUserId(request), month);
    }
}
