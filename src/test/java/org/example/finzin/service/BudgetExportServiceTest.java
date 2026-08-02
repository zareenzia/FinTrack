package org.example.finzin.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.finzin.entity.BudgetPlanEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test for BudgetExportService (no mocking needed — it has no collaborators, just pure
 * report generation from plain data). CSV output is checked with string assertions; the Excel and
 * PDF outputs are round-tripped through real Apache POI / PDFBox readers (both already runtime
 * dependencies of the class under test) to confirm the bytes are genuinely well-formed documents,
 * not just non-empty byte arrays.
 */
class BudgetExportServiceTest {

    private BudgetExportService service;
    private BudgetPlanEntity plan;

    @BeforeEach
    void setUp() {
        service = new BudgetExportService();
        plan = new BudgetPlanEntity();
        plan.setName("July Budget");
        plan.setPeriod("2026-07");
    }

    private Map<String, Object> summary(double plannedIncome, double plannedExpense, double plannedSavings,
                                         double actualIncome, double actualExpense, double actualSavings,
                                         double remaining, double utilizationPercent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plannedIncome", plannedIncome);
        m.put("plannedExpense", plannedExpense);
        m.put("plannedSavings", plannedSavings);
        m.put("actualIncome", actualIncome);
        m.put("actualExpense", actualExpense);
        m.put("actualSavings", actualSavings);
        m.put("remaining", remaining);
        m.put("utilizationPercent", utilizationPercent);
        return m;
    }

    private Map<String, Object> category(String name, double budget, double actual, double remaining,
                                          double percentUsed, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoryName", name);
        m.put("budgetAmount", budget);
        m.put("actualAmount", actual);
        m.put("remainingAmount", remaining);
        m.put("percentUsed", percentUsed);
        m.put("status", status);
        return m;
    }

    private Map<String, Object> savingsGoal(String name, double target, double current, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoryName", name);
        m.put("targetAmount", target);
        m.put("currentAmount", current);
        m.put("status", status);
        return m;
    }

    // ================================================================================
    // generateCsv
    // ================================================================================

    @Test
    void csvIncludesHeaderWithPlanNameAndPeriod() {
        String csv = service.generateCsv(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), List.of(), List.of(), 0);

        assertTrue(csv.startsWith("Monthly Budget Report - July Budget (2026-07)"));
    }

    @Test
    void csvIncludesFormattedSummaryValuesAndScore() {
        String csv = service.generateCsv(plan,
                summary(10000, 3000, 1000, 8000, 0, 500, 5700.5, 33.333), List.of(), List.of(), 87);

        assertTrue(csv.contains("Planned Income,10000.00"));
        assertTrue(csv.contains("Planned Expense,3000.00"));
        assertTrue(csv.contains("Actual Expense,0.00"));
        assertTrue(csv.contains("Remaining Budget,5700.50"));
        assertTrue(csv.contains("Budget Utilization %,33.33"));
        assertTrue(csv.contains("Budget Score,87"));
    }

    @Test
    void csvSavingsRateIsComputedFromActualIncomeAndActualSavings() {
        String csv = service.generateCsv(plan, summary(1000, 0, 0, 2000, 0, 400, 0, 0), List.of(), List.of(), 0);

        assertTrue(csv.contains("Savings Rate %,20.00"), "400 / 2000 * 100 = 20.00");
    }

    @Test
    void csvSavingsRateIsZeroWhenActualIncomeIsZero() {
        String csv = service.generateCsv(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), List.of(), List.of(), 0);

        assertTrue(csv.contains("Savings Rate %,0.00"));
    }

    @Test
    void csvListsOneRowPerCategoryWithAllFields() {
        List<Map<String, Object>> categories = List.of(
                category("Dining", 1000.0, 850.0, 150.0, 85.0, "NEAR_LIMIT"));

        String csv = service.generateCsv(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), categories, List.of(), 0);

        assertTrue(csv.contains("Dining,1000.00,850.00,150.00,85.00,NEAR_LIMIT"));
    }

    @Test
    void csvOverBudgetSectionListsOnlyOverBudgetCategoriesWithRemainingAmount() {
        List<Map<String, Object>> categories = List.of(
                category("Dining", 1000.0, 1500.0, -500.0, 150.0, "OVER_BUDGET"),
                category("Groceries", 500.0, 200.0, 300.0, 40.0, "ON_TRACK"));

        String csv = service.generateCsv(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), categories, List.of(), 0);

        assertTrue(csv.contains("Over Budget Categories\nDining,-500.00"));
        assertTrue(!csv.substring(csv.indexOf("Over Budget Categories")).contains("Groceries,300.00"));
    }

    @Test
    void csvOverBudgetSectionPrintsNoneWhenNoCategoryIsOverBudget() {
        List<Map<String, Object>> categories = List.of(
                category("Groceries", 500.0, 200.0, 300.0, 40.0, "ON_TRACK"));

        String csv = service.generateCsv(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), categories, List.of(), 0);

        assertTrue(csv.contains("Over Budget Categories\nNone\n"));
    }

    @Test
    void csvEscapesCategoryNamesContainingCommasWithQuotes() {
        List<Map<String, Object>> categories = List.of(
                category("Food, Drinks", 100.0, 50.0, 50.0, 50.0, "ON_TRACK"));

        String csv = service.generateCsv(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), categories, List.of(), 0);

        assertTrue(csv.contains("\"Food, Drinks\",100.00"));
    }

    @Test
    void csvListsSavingsGoalRows() {
        List<Map<String, Object>> savings = List.of(savingsGoal("Vacation", 1000.0, 400.0, "IN_PROGRESS"));

        String csv = service.generateCsv(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), List.of(), savings, 0);

        assertTrue(csv.contains("Vacation,1000.00,400.00,IN_PROGRESS"));
    }

    // ================================================================================
    // generateExcel
    // ================================================================================

    @Test
    void excelProducesAWorkbookWithSummaryCategoriesAndSavingsSheets() throws Exception {
        List<Map<String, Object>> categories = List.of(
                category("Dining", 1000.0, 850.0, 150.0, 85.0, "NEAR_LIMIT"));
        List<Map<String, Object>> savings = List.of(savingsGoal("Vacation", 1000.0, 400.0, "IN_PROGRESS"));

        byte[] bytes = service.generateExcel(plan, summary(10000, 3000, 1000, 8000, 0, 500, 5700, 33.3),
                categories, savings, 87);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("Summary"));
            assertNotNull(workbook.getSheet("Categories"));
            assertNotNull(workbook.getSheet("Savings"));

            XSSFSheet catSheet = workbook.getSheet("Categories");
            Row header = catSheet.getRow(0);
            assertEquals("Category", header.getCell(0).getStringCellValue());
            Row dataRow = catSheet.getRow(1);
            assertEquals("Dining", dataRow.getCell(0).getStringCellValue());
            assertEquals("NEAR_LIMIT", dataRow.getCell(5).getStringCellValue());
        }
    }

    @Test
    void excelCategorySheetHasNoDataRowsBeyondHeaderWhenNoCategories() throws Exception {
        byte[] bytes = service.generateExcel(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), List.of(), List.of(), 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet catSheet = workbook.getSheet("Categories");
            assertEquals(0, catSheet.getLastRowNum(), "only the header row (index 0) should exist");
        }
    }

    // ================================================================================
    // generatePdf
    // ================================================================================

    @Test
    void pdfProducesASingleLoadablePageDocument() throws Exception {
        List<Map<String, Object>> categories = List.of(
                category("Dining", 1000.0, 850.0, 150.0, 85.0, "NEAR_LIMIT"));
        List<Map<String, Object>> savings = List.of(savingsGoal("Vacation", 1000.0, 400.0, "IN_PROGRESS"));

        byte[] bytes = service.generatePdf(plan, summary(10000, 3000, 1000, 8000, 0, 500, 5700, 33.3),
                categories, savings, 87);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    void pdfSanitizesNonAsciiCharactersInsteadOfThrowing() throws Exception {
        List<Map<String, Object>> categories = List.of(
                category("Café éè", 100.0, 50.0, 50.0, 50.0, "ON_TRACK"));

        byte[] bytes = service.generatePdf(plan, summary(0, 0, 0, 0, 0, 0, 0, 0), categories, List.of(), 0);

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }
}
