package org.example.finzin.receipts.extract;

public record ReceiptLineItem(String name, Double quantity, Double unitPrice, Double totalPrice) {
}
