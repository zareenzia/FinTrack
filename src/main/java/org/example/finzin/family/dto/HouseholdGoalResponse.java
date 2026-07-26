package org.example.finzin.family.dto;

import java.util.List;

public record HouseholdGoalResponse(
        Long id,
        Long householdId,
        String name,
        Double targetAmount,
        String targetDate,
        String status,
        Double totalContributed,
        Double percentComplete,
        List<ContributionView> contributions,
        Long createdByUserId,
        String createdByName,
        String createdAt
) {
    public record ContributionView(Long id, Long userId, String userName, Double amount, String note, String contributedAt) {}
}
