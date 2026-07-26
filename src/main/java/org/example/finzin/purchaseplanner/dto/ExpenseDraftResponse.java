package org.example.finzin.purchaseplanner.dto;

/** Prefill payload for the frontend's local "Log Expense" modal — never used to create a transaction server-side. */
public record ExpenseDraftResponse(
        Double amount,
        String description,
        Long categoryId,
        String categoryName,
        String date,
        String details
) {}
