package org.example.finzin.purchaseplanner.dto;

public record PurchaseDashboardSummaryResponse(
        Double totalWishlistValue,
        Integer mustHaveCount,
        Integer shouldHaveCount,
        Integer niceToHaveCount,
        Integer readyToBuyCount,
        Double moneyPlannedThisMonth,
        Integer activeCount
) {}
