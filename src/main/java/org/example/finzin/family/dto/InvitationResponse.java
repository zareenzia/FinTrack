package org.example.finzin.family.dto;

public record InvitationResponse(
        Long id,
        Long householdId,
        String householdName,
        Long invitedByUserId,
        String invitedByName,
        Long inviteeUserId,
        String inviteeName,
        String relationshipLabel,
        String status,
        String createdAt,
        String respondedAt
) {}
