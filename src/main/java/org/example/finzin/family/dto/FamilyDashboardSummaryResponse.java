package org.example.finzin.family.dto;

public record FamilyDashboardSummaryResponse(
        Boolean hasHousehold,
        String householdName,
        Integer memberCount,
        Double thisMonthSharedTotal,
        Double yourNetBalance,
        Integer sharedExpenseCountThisMonth
) {
    public static FamilyDashboardSummaryResponse none() {
        return new FamilyDashboardSummaryResponse(false, null, null, null, null, null);
    }
}
