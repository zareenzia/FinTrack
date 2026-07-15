package org.example.finzin.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.finzin.entity.BudgetPlanEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BudgetExportService {

    public String generateCsv(BudgetPlanEntity plan, Map<String, Object> summary,
                               List<Map<String, Object>> categories, List<Map<String, Object>> savings, int score) {
        StringBuilder sb = new StringBuilder();
        sb.append("Monthly Budget Report - ").append(plan.getName()).append(" (").append(plan.getPeriod()).append(")\n\n");

        sb.append("Summary\n");
        appendCsvRow(sb, "Planned Income", num(summary.get("plannedIncome")));
        appendCsvRow(sb, "Planned Expense", num(summary.get("plannedExpense")));
        appendCsvRow(sb, "Planned Savings", num(summary.get("plannedSavings")));
        appendCsvRow(sb, "Actual Income", num(summary.get("actualIncome")));
        appendCsvRow(sb, "Actual Expense", num(summary.get("actualExpense")));
        appendCsvRow(sb, "Actual Savings", num(summary.get("actualSavings")));
        appendCsvRow(sb, "Remaining Budget", num(summary.get("remaining")));
        appendCsvRow(sb, "Budget Utilization %", num(summary.get("utilizationPercent")));
        appendCsvRow(sb, "Savings Rate %", savingsRate(summary));
        appendCsvRow(sb, "Budget Score", String.valueOf(score));
        sb.append("\n");

        sb.append("Category,Budget,Actual,Remaining,Percent Used,Status\n");
        for (Map<String, Object> c : categories) {
            sb.append(csvEscape((String) c.get("categoryName"))).append(",")
                    .append(num(c.get("budgetAmount"))).append(",")
                    .append(num(c.get("actualAmount"))).append(",")
                    .append(num(c.get("remainingAmount"))).append(",")
                    .append(num(c.get("percentUsed"))).append(",")
                    .append(c.get("status")).append("\n");
        }
        sb.append("\n");

        sb.append("Over Budget Categories\n");
        boolean anyOver = false;
        for (Map<String, Object> c : categories) {
            if ("OVER_BUDGET".equals(c.get("status"))) {
                anyOver = true;
                sb.append(csvEscape((String) c.get("categoryName"))).append(",")
                        .append(num(c.get("remainingAmount"))).append("\n");
            }
        }
        if (!anyOver) sb.append("None\n");
        sb.append("\n");

        sb.append("Savings Goal,Target,Current,Status\n");
        for (Map<String, Object> s : savings) {
            sb.append(csvEscape((String) s.get("categoryName"))).append(",")
                    .append(num(s.get("targetAmount"))).append(",")
                    .append(num(s.get("currentAmount"))).append(",")
                    .append(s.get("status")).append("\n");
        }
        return sb.toString();
    }

    public byte[] generateExcel(BudgetPlanEntity plan, Map<String, Object> summary,
                                 List<Map<String, Object>> categories, List<Map<String, Object>> savings, int score) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);

            XSSFSheet summarySheet = workbook.createSheet("Summary");
            int r = 0;
            r = writeExcelRow(summarySheet, r, boldStyle, "Monthly Budget Report - " + plan.getName() + " (" + plan.getPeriod() + ")");
            r++;
            r = writeExcelRow(summarySheet, r, boldStyle, "Planned Income", num(summary.get("plannedIncome")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Planned Expense", num(summary.get("plannedExpense")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Planned Savings", num(summary.get("plannedSavings")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Actual Income", num(summary.get("actualIncome")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Actual Expense", num(summary.get("actualExpense")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Actual Savings", num(summary.get("actualSavings")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Remaining Budget", num(summary.get("remaining")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Budget Utilization %", num(summary.get("utilizationPercent")));
            r = writeExcelRow(summarySheet, r, boldStyle, "Savings Rate %", savingsRate(summary));
            writeExcelRow(summarySheet, r, boldStyle, "Budget Score", String.valueOf(score));
            for (int i = 0; i < 2; i++) summarySheet.autoSizeColumn(i);

            XSSFSheet catSheet = workbook.createSheet("Categories");
            r = writeExcelHeader(catSheet, boldStyle, "Category", "Budget", "Actual", "Remaining", "Percent Used", "Status");
            for (Map<String, Object> c : categories) {
                r = writeExcelRow(catSheet, r, null, (String) c.get("categoryName"), num(c.get("budgetAmount")),
                        num(c.get("actualAmount")), num(c.get("remainingAmount")), num(c.get("percentUsed")), (String) c.get("status"));
            }
            for (int i = 0; i < 6; i++) catSheet.autoSizeColumn(i);

            XSSFSheet savingsSheet = workbook.createSheet("Savings");
            writeExcelHeader(savingsSheet, boldStyle, "Savings Goal", "Target", "Current", "Status");
            r = 1;
            for (Map<String, Object> s : savings) {
                r = writeExcelRow(savingsSheet, r, null, (String) s.get("categoryName"), num(s.get("targetAmount")),
                        num(s.get("currentAmount")), (String) s.get("status"));
            }
            for (int i = 0; i < 4; i++) savingsSheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generatePdf(BudgetPlanEntity plan, Map<String, Object> summary,
                               List<Map<String, Object>> categories, List<Map<String, Object>> savings, int score) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            float lineHeight = 16;

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                y = pdfLine(cs, bold, 14, margin, y, "Monthly Budget Report - " + plan.getName() + " (" + plan.getPeriod() + ")");
                y -= lineHeight;

                y = pdfLine(cs, bold, 12, margin, y, "Summary");
                y = pdfLine(cs, regular, 10, margin, y, "Planned Income: " + num(summary.get("plannedIncome")));
                y = pdfLine(cs, regular, 10, margin, y, "Planned Expense: " + num(summary.get("plannedExpense")));
                y = pdfLine(cs, regular, 10, margin, y, "Planned Savings: " + num(summary.get("plannedSavings")));
                y = pdfLine(cs, regular, 10, margin, y, "Actual Income: " + num(summary.get("actualIncome")));
                y = pdfLine(cs, regular, 10, margin, y, "Actual Expense: " + num(summary.get("actualExpense")));
                y = pdfLine(cs, regular, 10, margin, y, "Actual Savings: " + num(summary.get("actualSavings")));
                y = pdfLine(cs, regular, 10, margin, y, "Remaining Budget: " + num(summary.get("remaining")));
                y = pdfLine(cs, regular, 10, margin, y, "Budget Utilization: " + num(summary.get("utilizationPercent")) + "%");
                y = pdfLine(cs, regular, 10, margin, y, "Savings Rate: " + savingsRate(summary) + "%");
                y = pdfLine(cs, regular, 10, margin, y, "Budget Score: " + score + " / 100");
                y -= lineHeight;

                y = pdfLine(cs, bold, 12, margin, y, "Categories");
                for (Map<String, Object> c : categories) {
                    y = pdfLine(cs, regular, 10, margin, y, String.format(Locale.ROOT, "%s: budget %s, actual %s, %s (%s%%)",
                            c.get("categoryName"), num(c.get("budgetAmount")), num(c.get("actualAmount")), c.get("status"), num(c.get("percentUsed"))));
                    if (y < margin) break;
                }
                y -= lineHeight;

                if (y > margin && !savings.isEmpty()) {
                    y = pdfLine(cs, bold, 12, margin, y, "Savings Goals");
                    for (Map<String, Object> s : savings) {
                        y = pdfLine(cs, regular, 10, margin, y, String.format(Locale.ROOT, "%s: target %s, current %s (%s)",
                                s.get("categoryName"), num(s.get("targetAmount")), num(s.get("currentAmount")), s.get("status")));
                        if (y < margin) break;
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private float pdfLine(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text == null ? "" : text.replaceAll("[^\\x00-\\x7F]", "?"));
        cs.endText();
        return y - (size + 4);
    }

    private int writeExcelHeader(XSSFSheet sheet, CellStyle boldStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(boldStyle);
        }
        return 1;
    }

    private int writeExcelRow(XSSFSheet sheet, int rowIndex, CellStyle firstColStyle, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            if (i == 0 && firstColStyle != null) cell.setCellStyle(firstColStyle);
        }
        return rowIndex + 1;
    }

    private void appendCsvRow(StringBuilder sb, String label, String value) {
        sb.append(csvEscape(label)).append(",").append(value).append("\n");
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String num(Object value) {
        if (value == null) return "0";
        double d = ((Number) value).doubleValue();
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private String savingsRate(Map<String, Object> summary) {
        double income = ((Number) summary.get("actualIncome")).doubleValue();
        double savings = ((Number) summary.get("actualSavings")).doubleValue();
        double rate = income == 0 ? 0 : (savings / income) * 100;
        return String.format(Locale.ROOT, "%.2f", rate);
    }
}
