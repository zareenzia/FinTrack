package org.example.finzin.purchaseplanner.dto;

import java.util.List;

public record PurchaseAnalyticsResponse(
        Double totalWishlistValue,
        Double averageItemCost,
        Integer purchasedThisYear,
        Integer cancelledCount,
        Double moneySavedByCancelling,
        Integer mustHaveCount,
        Integer shouldHaveCount,
        Integer niceToHaveCount,
        List<MonthlyCount> monthlyPlannedPurchases,
        List<MonthlyCount> completedPurchasesPerMonth
) {}
