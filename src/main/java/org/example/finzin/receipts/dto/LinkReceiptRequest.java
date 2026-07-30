package org.example.finzin.receipts.dto;

public record LinkReceiptRequest(Long transactionId, String merchantName, String notes) {
}
