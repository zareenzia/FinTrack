package org.example.finzin.receipts;

/**
 * Unified error type for the receipt-scanning pipeline. Mirrors {@code org.example.finzin.ai.OpenAIException}
 * (errorTag + user-safe message); HTTP status mapping stays in the controller.
 */
public class ReceiptException extends RuntimeException {
    private final String errorTag;
    private final String userMessage;

    private ReceiptException(String errorTag, String userMessage) {
        super(errorTag);
        this.errorTag = errorTag;
        this.userMessage = userMessage;
    }

    public String getErrorTag() { return errorTag; }
    public String getUserMessage() { return userMessage; }

    public static ReceiptException ocrNotConfigured() {
        return new ReceiptException("OCR_NOT_CONFIGURED",
                "Receipt scanning isn't set up on this server yet (OCR engine not configured).");
    }

    public static ReceiptException ocrFailed() {
        return new ReceiptException("OCR_FAILED", "Could not read text from this image. Try a clearer photo.");
    }

    public static ReceiptException extractionFailed() {
        return new ReceiptException("EXTRACTION_FAILED", "Something went wrong analyzing this receipt. Please try again.");
    }

    public static ReceiptException disabled() {
        return new ReceiptException("SETTINGS_DISABLED", "Receipt Scanner is currently disabled. Enable it in Settings to scan receipts.");
    }

    public static ReceiptException alreadyLinked() {
        return new ReceiptException("ALREADY_LINKED", "This receipt is already linked to a transaction.");
    }
}
