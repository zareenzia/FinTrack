package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "household_invitations")
public class HouseholdInvitationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long householdId;

    @Column(nullable = false)
    private Long invitedByUserId;

    @Column(nullable = false)
    private Long inviteeUserId;

    /** Free-text, cosmetic only: "Spouse", "Parent", "Child", "Other" — carried over to HouseholdMemberEntity on acceptance. */
    @Column(nullable = true)
    private String relationshipLabel;

    /** PENDING, ACCEPTED, REJECTED, CANCELLED */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private LocalDateTime respondedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }
    public Long getInvitedByUserId() { return invitedByUserId; }
    public void setInvitedByUserId(Long invitedByUserId) { this.invitedByUserId = invitedByUserId; }
    public Long getInviteeUserId() { return inviteeUserId; }
    public void setInviteeUserId(Long inviteeUserId) { this.inviteeUserId = inviteeUserId; }
    public String getRelationshipLabel() { return relationshipLabel; }
    public void setRelationshipLabel(String relationshipLabel) { this.relationshipLabel = relationshipLabel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}
