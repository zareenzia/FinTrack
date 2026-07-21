package org.example.finzin.receipts.extract;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression coverage for the heuristic (non-AI) receipt field extractor, backed by real OCR
 * output from two different delivery-app receipt styles that each broke a different assumption:
 * decimal-only amounts, ISO-only dates, and "first OCR line = merchant" name detection.
 */
class HeuristicReceiptFieldExtractorTest {

    private final HeuristicReceiptFieldExtractor extractor = new HeuristicReceiptFieldExtractor();

    @Test
    void extractsMerchantAmountAndDateFromFoodpandaStyleReceipt() {
        String ocrText = """
                foodpanda
                Hey Zareen,
                Your order is in. Here's the rundown.
                Order details
                Order number qvv9-2630-wflh
                Order time 2026-07-21 20:30:02
                Store Al Kareem Restaurant
                Ordered items
                1 X Grilled Chicken With Mayonnaise Cup Tk 320.00
                1 X Butter Naan Tk 45.00
                Subtotal Tk 475.00
                Order Total Tk 590.75
                """;

        ReceiptFieldExtractionResult result = extractor.extract(ocrText);

        // The explicit "Store" label wins over the "foodpanda" app-brand text at the top — the
        // actual restaurant is the more useful merchant name for expense tracking than the
        // delivery platform that was just the ordering intermediary.
        assertEquals("Al Kareem Restaurant", result.merchantName());
        assertEquals(590.75, result.totalAmount());
        assertEquals(LocalDate.of(2026, 7, 21), result.receiptDate());
    }

    @Test
    void fallsBackToFirstLineWhenNoMerchantLabelIsPresent() {
        String ocrText = """
                foodpanda
                Hey Zareen,
                Your order is in. Here's the rundown.
                Order Total Tk 590.75
                """;

        ReceiptFieldExtractionResult result = extractor.extract(ocrText);

        assertEquals("foodpanda", result.merchantName());
    }

    @Test
    void extractsMerchantAmountAndDateFromPizzaBurgStyleReceiptDespiteOcrIconNoiseAndWholeAmounts() {
        // Verbatim Tesseract output from a real receipt — note the "©)" and "©" artifacts where a
        // location-pin icon was misread, and that every Taka amount here is a whole number.
        String ocrText = """
                Order #s6ko-2625-yxm2
                Delivered on 20 Jun 18:34
                ©) Order from
                Pizza Burg - Uttara
                © Delivered to
                74 Road Number 1, Dhaka
                1x BBQ Meat Machine Pizza Tk 455
                Medium
                Subtotal Tk 455
                Delivery fee Tk 22
                Vendor Packaging Fee Tk 20
                Platform Fee Tk 12
                Incl. VAT Tk 29
                Total (incl. VAT) Tk 538
                Paid with
                cash on delivery Tk 538
                """;

        ReceiptFieldExtractionResult result = extractor.extract(ocrText);

        assertEquals("Pizza Burg - Uttara", result.merchantName(), "must not grab the fee-line 'Vendor Packaging Fee' text");
        assertEquals(538.0, result.totalAmount(), "must match the 'Total' line even without a decimal fraction");
        assertEquals(LocalDate.of(Year.now().getValue(), 6, 20), result.receiptDate(), "month-name dates default to the current year when none is printed");
    }

    @Test
    void neverThrowsAndReturnsNullFieldsForUnrecognizableText() {
        ReceiptFieldExtractionResult result = extractor.extract("asdf qwer zxcv");

        assertNull(result.totalAmount());
        assertNull(result.receiptDate());
        assertEquals("HEURISTIC", result.source());
    }
}
