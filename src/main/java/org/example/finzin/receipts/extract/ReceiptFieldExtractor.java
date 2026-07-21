package org.example.finzin.receipts.extract;

/**
 * Abstraction over whatever turns raw OCR text into structured receipt fields, so the
 * implementation (AI text-completion today, something else later) can be swapped without
 * touching {@code ReceiptService}.
 */
public interface ReceiptFieldExtractor {
    ReceiptFieldExtractionResult extract(String ocrText);
}
