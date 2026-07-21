package org.example.finzin.receipts.extract;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Structured fields pulled out of a receipt's OCR text, plus a per-field confidence map
 * (keys: merchantName/totalAmount/receiptDate/predictedCategoryLabel) and an overall score.
 * {@code source} is "AI" or "HEURISTIC" — surfaced to the frontend so low-trust extractions are flagged.
 */
public record ReceiptFieldExtractionResult(
        String merchantName,
        String receiptNumber,
        String invoiceNumber,
        LocalDate receiptDate,
        String receiptTime,
        String currency,
        Double totalAmount,
        Double taxAmount,
        Double discountAmount,
        Double subtotalAmount,
        String paymentMethod,
        List<ReceiptLineItem> items,
        String predictedCategoryLabel,
        String suggestedNotes,
        Double confidenceScore,
        Map<String, Double> fieldConfidence,
        String source,
        String warning
) {
}
