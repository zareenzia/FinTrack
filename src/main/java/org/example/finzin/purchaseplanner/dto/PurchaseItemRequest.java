package org.example.finzin.purchaseplanner.dto;

/** Create/update body. {@code status}, when present, is only honored for PLANNING/WAITING/READY — use the
 * dedicated /cancel and /mark-purchased endpoints to move an item to CANCELLED/PURCHASED. */
public record PurchaseItemRequest(
        String itemName,
        Double estimatedPrice,
        String category,
        String needLevel,
        String priority,
        String brand,
        String store,
        String purchaseUrl,
        String notes,
        String targetMonth,
        String expectedPurchaseDate,
        Long linkedSavingsGoalId,
        String status
) {}
