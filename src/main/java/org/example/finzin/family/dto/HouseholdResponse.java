package org.example.finzin.family.dto;

import java.util.List;

public record HouseholdResponse(
        Boolean hasHousehold,
        Long id,
        String name,
        Long ownerId,
        String myRole,
        List<HouseholdMemberResponse> members,
        String createdAt
) {
    public static HouseholdResponse none() {
        return new HouseholdResponse(false, null, null, null, null, List.of(), null);
    }
}
