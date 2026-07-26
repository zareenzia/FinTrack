package org.example.finzin.family.dto;

import java.util.List;

public record SettlementSummaryResponse(
        String periodStart,
        String periodEnd,
        List<MemberBalance> balances,
        List<SuggestedTransfer> suggestedTransfers
) {}
