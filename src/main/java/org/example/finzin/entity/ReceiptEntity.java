package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipts")
public class ReceiptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Null until the draft is confirmed and linked to a real transaction. */
    @Column(nullable = true)
    private Long transactionId;

    private String merchantName;
    private String receiptNumber;
    private String invoiceNumber;
    private LocalDate receiptDate;

    /** Raw text (e.g. "18:42") — OCR timestamps are often partial/ambiguous, not worth a LocalTime parse. */
    private String receiptTime;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    @Column(nullable = false)
    private String imagePath;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    private Double extractedAmount;
    private String extractedCurrency;
    private Double extractedTax;
    private Double extractedDiscount;
    private Double extractedSubtotal;
    private String paymentMethod;

    /** Jackson-serialized List<ReceiptLineItem> — this codebase has no dedicated JSON column type anywhere. */
    @Column(columnDefinition = "TEXT")
    private String itemsJson;

    /** No FK — AI/heuristic guesses may not match any real category. */
    private Long predictedCategoryId;
    private String predictedCategoryLabel;

    @Column(columnDefinition = "TEXT")
    private String suggestedNotes;

    private Double confidenceScore;

    /** Jackson-serialized Map<String,Double>, keyed by merchantName/totalAmount/receiptDate/predictedCategoryLabel. */
    @Column(columnDefinition = "TEXT")
    private String fieldConfidenceJson;

    @Column(nullable = false)
    private String extractionSource; // "AI" | "HEURISTIC"

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getReceiptDate() { return receiptDate; }
    public void setReceiptDate(LocalDate receiptDate) { this.receiptDate = receiptDate; }
    public String getReceiptTime() { return receiptTime; }
    public void setReceiptTime(String receiptTime) { this.receiptTime = receiptTime; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public Double getExtractedAmount() { return extractedAmount; }
    public void setExtractedAmount(Double extractedAmount) { this.extractedAmount = extractedAmount; }
    public String getExtractedCurrency() { return extractedCurrency; }
    public void setExtractedCurrency(String extractedCurrency) { this.extractedCurrency = extractedCurrency; }
    public Double getExtractedTax() { return extractedTax; }
    public void setExtractedTax(Double extractedTax) { this.extractedTax = extractedTax; }
    public Double getExtractedDiscount() { return extractedDiscount; }
    public void setExtractedDiscount(Double extractedDiscount) { this.extractedDiscount = extractedDiscount; }
    public Double getExtractedSubtotal() { return extractedSubtotal; }
    public void setExtractedSubtotal(Double extractedSubtotal) { this.extractedSubtotal = extractedSubtotal; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
    public Long getPredictedCategoryId() { return predictedCategoryId; }
    public void setPredictedCategoryId(Long predictedCategoryId) { this.predictedCategoryId = predictedCategoryId; }
    public String getPredictedCategoryLabel() { return predictedCategoryLabel; }
    public void setPredictedCategoryLabel(String predictedCategoryLabel) { this.predictedCategoryLabel = predictedCategoryLabel; }
    public String getSuggestedNotes() { return suggestedNotes; }
    public void setSuggestedNotes(String suggestedNotes) { this.suggestedNotes = suggestedNotes; }
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getFieldConfidenceJson() { return fieldConfidenceJson; }
    public void setFieldConfidenceJson(String fieldConfidenceJson) { this.fieldConfidenceJson = fieldConfidenceJson; }
    public String getExtractionSource() { return extractionSource; }
    public void setExtractionSource(String extractionSource) { this.extractionSource = extractionSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
