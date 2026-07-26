package org.example.finzin.family.dto;

public record HouseholdBudgetResponse(
        Long id,
        Long householdId,
        String categoryName,
        Double monthlyLimit,
        Double spentThisMonth,
        Double percentUsed,
        String status,
        Long createdByUserId,
        String createdByName
) {}
