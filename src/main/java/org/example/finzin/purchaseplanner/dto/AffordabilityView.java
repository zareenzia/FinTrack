package org.example.finzin.purchaseplanner.dto;

/** statusLabel is the literal display string: "✅ Can Buy Now" / "⚠ Wait N Months" / "❌ Not Affordable". */
public record AffordabilityView(
        String status,
        String statusLabel,
        Integer monthsRemaining,
        Double netAvailableNow,
        Double monthlySavingsCapacity
) {}
