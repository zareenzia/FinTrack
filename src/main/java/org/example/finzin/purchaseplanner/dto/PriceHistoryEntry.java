package org.example.finzin.purchaseplanner.dto;

public record PriceHistoryEntry(
        Double oldPrice,
        Double newPrice,
        String changedAt
) {}
