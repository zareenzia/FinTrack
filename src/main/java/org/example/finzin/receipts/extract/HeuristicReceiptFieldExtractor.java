package org.example.finzin.receipts.extract;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure regex/pattern-matching fallback used whenever the AI extractor is unconfigured or fails.
 * Always succeeds (never throws) so a draft can still be produced, just with low confidence.
 */
@Component
public class HeuristicReceiptFieldExtractor implements ReceiptFieldExtractor {

    // Requires a currency marker so we never mistake an order number, phone number, or time
    // (e.g. "18:34") for an amount — decimals are optional since many receipts show whole Taka.
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?:BDT|Tk\\.?|USD|\\$|৳)\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)\\b");
    private static final Pattern TOTAL_LINE_PATTERN = Pattern.compile("(?i)\\b(grand\\s*total|total\\s*amount|total|amount\\s*due)\\b");

    private static final Pattern[] DATE_PATTERNS = {
            Pattern.compile("\\b(\\d{4}-\\d{1,2}-\\d{1,2})\\b"),
            Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b"),
    };
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
    };
    // "20 Jun", "20 Jun 2026" — common on order-confirmation receipts that omit the year.
    private static final Pattern MONTH_NAME_DATE_PATTERN =
            Pattern.compile("(?i)\\b(\\d{1,2})\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\b(?:\\s+(\\d{4}))?");

    // Lines introducing the merchant/store name, either inline ("Store    Al Kareem Restaurant")
    // or as a label whose value is on the next line ("Order from" / "Pizza Burg - Uttara").
    // Deliberately NOT "vendor"/"merchant" — those collide with common fee line items like
    // "Vendor Packaging Fee" and would grab the fee description instead of the actual merchant.
    private static final Pattern MERCHANT_LABEL_PATTERN =
            Pattern.compile("(?i)^(order\\s*from|store|restaurant)\\b[:\\-]?\\s*(.*)$");
    // Heading-like first lines to skip when falling back to "first real line" for merchant name.
    private static final Pattern HEADING_LIKE_PATTERN =
            Pattern.compile("(?i)^(order\\s*#|order\\s*number|receipt\\s*#|invoice\\s*#|delivered\\s|hey\\s|your\\s*order)");
    // OCR frequently misreads small icons/pins/glyphs preceding a label as stray symbols
    // (e.g. a location-pin icon became "©)" ahead of "Order from") — strip those before matching.
    private static final Pattern LEADING_NOISE_PATTERN = Pattern.compile("^[^A-Za-z0-9]+");

    @Override
    public ReceiptFieldExtractionResult extract(String ocrText) {
        String text = ocrText == null ? "" : ocrText;
        String[] lines = text.split("\\r?\\n");
        String merchantName = guessMerchantName(lines);
        Double totalAmount = guessTotalAmount(lines);
        LocalDate receiptDate = guessDate(text);

        Map<String, Double> confidence = new LinkedHashMap<>();
        if (merchantName != null) confidence.put("merchantName", 0.4);
        if (totalAmount != null) confidence.put("totalAmount", 0.45);
        if (receiptDate != null) confidence.put("receiptDate", 0.35);

        double overall = confidence.isEmpty() ? 0.0
                : confidence.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return new ReceiptFieldExtractionResult(
                merchantName, null, null, receiptDate, null, null,
                totalAmount, null, null, null, null,
                List.of(), null, null,
                overall, confidence, "HEURISTIC",
                "AI extraction unavailable — fields were guessed with basic pattern matching. Please double-check everything."
        );
    }

    private String guessMerchantName(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String trimmed = stripLeadingNoise(lines[i]);
            if (trimmed.isEmpty()) continue;
            Matcher m = MERCHANT_LABEL_PATTERN.matcher(trimmed);
            if (m.find()) {
                String inline = m.group(2) == null ? "" : m.group(2).trim();
                if (!inline.isEmpty()) return inline;
                // Label alone on its own line — the value is the next non-blank line.
                for (int j = i + 1; j < lines.length; j++) {
                    String next = stripLeadingNoise(lines[j]);
                    if (!next.isEmpty()) return next;
                }
            }
        }
        // No explicit label found — fall back to the first line that isn't an order-number/heading.
        for (String line : lines) {
            String trimmed = stripLeadingNoise(line);
            if (trimmed.length() >= 2 && trimmed.length() <= 60 && !HEADING_LIKE_PATTERN.matcher(trimmed).find()) {
                return trimmed;
            }
        }
        return null;
    }

    private String stripLeadingNoise(String line) {
        return LEADING_NOISE_PATTERN.matcher(line.trim()).replaceFirst("").trim();
    }

    private Double guessTotalAmount(String[] lines) {
        Double best = null;
        for (String line : lines) {
            boolean looksLikeTotal = TOTAL_LINE_PATTERN.matcher(line).find();
            Matcher m = AMOUNT_PATTERN.matcher(line);
            while (m.find()) {
                Double value = parseAmount(m.group(1));
                if (value == null) continue;
                if (looksLikeTotal) return value; // an explicit "total" line wins outright
                if (best == null || value > best) best = value; // otherwise, best guess = largest figure on the receipt
            }
        }
        return best;
    }

    private Double parseAmount(String raw) {
        try {
            String normalized = raw.replace(",", "");
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate guessDate(String text) {
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                String candidate = m.group(1);
                for (DateTimeFormatter fmt : DATE_FORMATTERS) {
                    try {
                        return LocalDate.parse(candidate, fmt);
                    } catch (DateTimeParseException ignored) {
                        // try the next formatter
                    }
                }
            }
        }
        Matcher monthName = MONTH_NAME_DATE_PATTERN.matcher(text);
        if (monthName.find()) {
            try {
                int day = Integer.parseInt(monthName.group(1));
                int month = parseMonthAbbreviation(monthName.group(2));
                int year = monthName.group(3) != null ? Integer.parseInt(monthName.group(3)) : Year.now().getValue();
                if (month > 0) return LocalDate.of(year, month, day);
            } catch (Exception ignored) {
                // fall through to null below
            }
        }
        return null;
    }

    private int parseMonthAbbreviation(String abbrev) {
        String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        String lower = abbrev.toLowerCase(Locale.ROOT);
        for (int i = 0; i < months.length; i++) {
            if (months[i].equals(lower)) return i + 1;
        }
        return -1;
    }
}
