package org.example.finzin.family.dto;

import java.util.List;

public record SharedExpenseResponse(
        Long id,
        Long householdId,
        Long transactionId,
        Long payerUserId,
        String payerName,
        Double totalAmount,
        String splitMethod,
        String description,
        String category,
        String expenseDate,
        List<ShareView> shares,
        String createdAt
) {
    public record ShareView(Long userId, String userName, Double shareAmount, Double sharePercent, Boolean isPayer) {}
}
