package org.example.finzin.family.dto;

public record SettlementResponse(
        Long id,
        Long householdId,
        Long fromUserId,
        String fromUserName,
        Long toUserId,
        String toUserName,
        Double amount,
        String note,
        String settledAt
) {}
