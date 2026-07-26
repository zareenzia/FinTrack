package org.example.finzin.purchaseplanner.dto;

/** All scores are out of 5 (rendered as star ratings); overallReadinessPercent is 0-100. */
public record DecisionMatrixView(
        Double needScore,
        Double urgencyScore,
        Double budgetScore,
        Double savingsScore,
        Double overallReadinessPercent
) {}
