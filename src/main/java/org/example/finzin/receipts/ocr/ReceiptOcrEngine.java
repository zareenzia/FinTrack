package org.example.finzin.receipts.ocr;

import java.nio.file.Path;

/**
 * Abstraction over whatever reads text out of a receipt image, so the engine (local Tesseract
 * today, a cloud OCR or vision-LLM later) can be swapped without touching {@code ReceiptService}.
 */
public interface ReceiptOcrEngine {
    OcrResult extractText(Path imageFile, String mimeType);

    /** False when the native engine/language data isn't available — callers must degrade gracefully, never crash. */
    boolean isAvailable();
}
