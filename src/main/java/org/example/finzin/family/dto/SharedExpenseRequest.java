package org.example.finzin.family.dto;

import java.util.List;

/** transactionId must already exist and belong to the caller (created via the normal POST /api/transactions
 * flow) — this endpoint only links it into the household ledger, it never creates a transaction itself. */
public record SharedExpenseRequest(
        Long transactionId,
        String splitMethod,
        List<ShareInput> shares
) {
    public record ShareInput(Long userId, Double amount, Double percent) {}
}
