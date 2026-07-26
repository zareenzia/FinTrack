package org.example.finzin.family.dto;

public record HouseholdMemberResponse(
        Long userId,
        String fullName,
        String username,
        String email,
        String role,
        String relationshipLabel,
        String joinedAt
) {}
