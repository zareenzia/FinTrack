package org.example.finzin.family.dto;

/** emailOrUsername is resolved against UserRepository at invite time. */
public record InvitationRequest(String emailOrUsername, String relationshipLabel) {}
