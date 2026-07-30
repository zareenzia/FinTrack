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
    void extractsTotalAmountFromPosStyleReceiptWithNoCurrencySymbolAtAll() {
        // A dine-in POS ticket — unlike delivery-app receipts, nothing here is prefixed with a
        // currency marker (no "Tk"/"BDT"/"৳"), just bare decimal figures next to each label.
        String ocrText = """
                TABAQ COFFEE
                Airport Centre Point
                Dhaka,Bangladesh
                Phone:+8801794243436

                BIN:006492854-0101
                Mushak-6.3
                Govt.Invoice
                Date:22-Jul-26                Time:6:36 PM
                Ticket Token Number 132

                -1 Hot Chocolate            420.00 420.00
                -1 Lime Fizz                250.00 250.00

                Ticket Total:                        670.00
                Vat.-5.00%:                            31.90

                Gross Total:                          670.00
                -Visa Card:                           670.00
                -TOTAL PAYMENT:                        670.00
                -RETURNED AMOUNT:                        0.00
                """;

        ReceiptFieldExtractionResult result = extractor.extract(ocrText);

        assertEquals(670.0, result.totalAmount(), "must match a bare 'Ticket Total:'/'Gross Total:' figure even with zero currency markers anywhere on the receipt");
        assertEquals(LocalDate.of(2026, 7, 22), result.receiptDate(), "must parse 'Date:22-Jul-26' — hyphens instead of spaces, 2-digit year instead of 4");
    }

    @Test
    void extractsAmountAndDateFromVerbatimGarbledOcrOfPosReceipt() {
        // Actual Tesseract output for the same physical receipt as the test above — OCR mangled
        // much more of it ("Ticket Total" became "Sekepe Total", stray "=", "—", "~", "|", "Zé"
        // noise throughout) but the total/date lines survived legibly enough to still extract.
        String ocrText = """
                Airport Centre Point
                Dhaka Bangladesh
                Phone : +8801794243436 yn :
                KIN:006492854-0101
                Mushak-6 .3 =
                Govt. Invoice . —
                Code-SCL 7938358256
                ee ee
                Date :22-Jul-26 Time :6:36 PM
                ~ Number Of Guests :0
                CHALAN No:92361
                | Ticket Token Number 132
                caty Teen Name Price T.Price
                eter chocolate 420.00 420.00
                -~] Lime Fizz 250.00 250.00
                -] FUDGE BROWNIE LOYALTY GIFT 0.00 0,00
                Sekepe Total aa 670.00
                Included:
                Yat.-5.00%: ; _ etd
                (ee SS Se ee ee TTT
                | Gross Total: 670.00
                Payments:
                -Visa Card: 670.00
                -TOTAL PAYMENT: 670.00
                -RETURNED AMOUNT: 0.00
                PALD
                -ottery Cupon NO:SCL7938358256
                Powerd by: www.3ssoftltd.com
                Phone ; 01329692488
                Zé
                """;

        ReceiptFieldExtractionResult result = extractor.extract(ocrText);

        assertEquals(670.0, result.totalAmount(), "'Sekepe Total' still contains the bare word 'Total', so the bare-decimal match must still fire");
        assertEquals(LocalDate.of(2026, 7, 22), result.receiptDate());
    }

    @Test
    void neverThrowsAndReturnsNullFieldsForUnrecognizableText() {
        ReceiptFieldExtractionResult result = extractor.extract("asdf qwer zxcv");

        assertNull(result.totalAmount());
        assertNull(result.receiptDate());
        assertEquals("HEURISTIC", result.source());
    }
}
