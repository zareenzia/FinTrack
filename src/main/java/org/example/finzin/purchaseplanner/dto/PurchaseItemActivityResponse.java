package org.example.finzin.purchaseplanner.dto;

public record PurchaseItemActivityResponse(
        Long id,
        String activityType,
        String fieldName,
        String oldValue,
        String newValue,
        String note,
        String createdAt
) {}
