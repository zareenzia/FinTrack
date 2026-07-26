package org.example.finzin.purchaseplanner.dto;

import java.util.List;

public record PurchaseItemDetailResponse(
        PurchaseItemResponse item,
        List<PurchaseItemActivityResponse> activityTimeline,
        List<PriceHistoryEntry> priceHistory
) {}
