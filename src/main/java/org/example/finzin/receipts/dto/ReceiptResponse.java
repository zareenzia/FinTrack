package org.example.finzin.receipts.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.ReceiptEntity;
import org.example.finzin.receipts.extract.ReceiptLineItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReceiptResponse(
        Long id,
        String status,
        Long transactionId,
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
        Long predictedCategoryId,
        String predictedCategoryLabel,
        String suggestedNotes,
        Double confidenceScore,
        Map<String, Double> fieldConfidence,
        String extractionSource,
        String ocrText,
        String imageUrl,
        String mimeType,
        Long fileSizeBytes,
        String warning,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReceiptResponse from(ReceiptEntity e, ObjectMapper objectMapper, String warning) {
        List<ReceiptLineItem> items = readJson(objectMapper, e.getItemsJson(), new TypeReference<List<ReceiptLineItem>>() {}, List.of());
        Map<String, Double> fieldConfidence = readJson(objectMapper, e.getFieldConfidenceJson(), new TypeReference<Map<String, Double>>() {}, Map.of());
        return new ReceiptResponse(
                e.getId(),
                e.getTransactionId() == null ? "DRAFT" : "CONFIRMED",
                e.getTransactionId(),
                e.getMerchantName(),
                e.getReceiptNumber(),
                e.getInvoiceNumber(),
                e.getReceiptDate(),
                e.getReceiptTime(),
                e.getExtractedCurrency(),
                e.getExtractedAmount(),
                e.getExtractedTax(),
                e.getExtractedDiscount(),
                e.getExtractedSubtotal(),
                e.getPaymentMethod(),
                items,
                e.getPredictedCategoryId(),
                e.getPredictedCategoryLabel(),
                e.getSuggestedNotes(),
                e.getConfidenceScore(),
                fieldConfidence,
                e.getExtractionSource(),
                e.getOcrText(),
                "/api/receipts/" + e.getId() + "/image",
                e.getMimeType(),
                e.getFileSizeBytes(),
                warning,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private static <T> T readJson(ObjectMapper mapper, String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }
}
