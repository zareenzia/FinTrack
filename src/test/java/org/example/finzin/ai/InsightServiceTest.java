package org.example.finzin.ai;

import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test verifying the threshold rules (>=15% MoM notable, >1.5x trailing average
 * unusual) against fixed mocked sums, mirroring {@code AIServiceTest}'s convention.
 *
 * MoM comparisons use month-to-date ranges (day 1 through today, both this month and last) rather
 * than full calendar months — otherwise a partial current month would always look like a huge drop
 * against a full previous month. The trailing 3-month average used for "unusual spend" detection
 * still uses full prior months, so this test stubs both range shapes where a month serves both roles.
 */
@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long CATEGORY_ID = 1L;

    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;

    private InsightService service;

    @BeforeEach
    void setUp() {
        service = new InsightService(transactionRepository, categoryRepository);

        CategoryEntity dining = new CategoryEntity();
        dining.setId(CATEGORY_ID);
        dining.setName("Dining");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(dining));

        YearMonth current = YearMonth.now();
        YearMonth prev1 = current.minusMonths(1);
        YearMonth prev2 = current.minusMonths(2);
        YearMonth prev3 = current.minusMonths(3);
        int throughDay = LocalDate.now().getDayOfMonth();

        // Dining: month-to-date is 18% above the same point last month, but not above the 3-month average.
        stubCategoryMonthToDate(current, throughDay, 118.0);
        stubCategoryMonthToDate(prev1, throughDay, 100.0);
        stubCategoryFullMonth(prev1, 100.0);
        stubCategoryFullMonth(prev2, 100.0);
        stubCategoryFullMonth(prev3, 100.0);

        // Income: unchanged month-over-month -> no insight.
        stubOverallMonthToDate("income", current, throughDay, 1000.0);
        stubOverallMonthToDate("income", prev1, throughDay, 1000.0);

        // Savings: month-to-date is 25% above the same point last month, but not above the 3-month average.
        stubOverallMonthToDate("savings", current, throughDay, 250.0);
        stubOverallMonthToDate("savings", prev1, throughDay, 200.0);
        stubOverallFullMonth("savings", prev1, 200.0);
        stubOverallFullMonth("savings", prev2, 200.0);
        stubOverallFullMonth("savings", prev3, 200.0);
    }

    private void stubCategoryMonthToDate(YearMonth month, int throughDay, double value) {
        int clampedDay = Math.min(throughDay, month.lengthOfMonth());
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atDay(clampedDay).plusDays(1).atStartOfDay();
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(CATEGORY_ID), eq(start), eq(end)))
                .thenReturn(value);
    }

    private void stubCategoryFullMonth(YearMonth month, double value) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        when(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(eq(USER_ID), eq("expense"), eq(CATEGORY_ID), eq(start), eq(end)))
                .thenReturn(value);
    }

    private void stubOverallMonthToDate(String type, YearMonth month, int throughDay, double value) {
        int clampedDay = Math.min(throughDay, month.lengthOfMonth());
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atDay(clampedDay).plusDays(1).atStartOfDay();
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq(type), eq(start), eq(end))).thenReturn(value);
    }

    private void stubOverallFullMonth(String type, YearMonth month, double value) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        when(transactionRepository.sumByUserIdAndTypeAndDateRange(eq(USER_ID), eq(type), eq(start), eq(end))).thenReturn(value);
    }

    @Test
    void notableCategoryIncreaseProducesMoMInsight() {
        List<InsightService.Insight> insights = service.generateInsights(USER_ID);

        assertTrue(insights.stream().anyMatch(i -> i.category().equals("Dining") && i.title().contains("increased")),
                "Expected a Dining MoM increase insight, got: " + insights);
    }

    @Test
    void unchangedIncomeProducesNoInsight() {
        List<InsightService.Insight> insights = service.generateInsights(USER_ID);

        assertTrue(insights.stream().noneMatch(i -> "Income".equals(i.category())));
    }

    @Test
    void notableSavingsIncreaseProducesMoMInsightButNotUnusual() {
        List<InsightService.Insight> insights = service.generateInsights(USER_ID);

        long savingsInsights = insights.stream().filter(i -> "Savings".equals(i.category())).count();
        assertEquals(1, savingsInsights, "Expected exactly one savings insight (MoM), got: " + insights);
        assertTrue(insights.stream().anyMatch(i -> "Savings".equals(i.category()) && i.title().toLowerCase().contains("saved")));
    }
}
