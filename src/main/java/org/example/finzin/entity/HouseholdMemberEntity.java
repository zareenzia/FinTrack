package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "household_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"householdId", "userId"}, name = "uk_household_member")
})
public class HouseholdMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long householdId;

    @Column(nullable = false)
    private Long userId;

    /** ADMIN, MEMBER — exactly one ADMIN per household at a time (the "owner"). */
    @Column(nullable = false)
    private String role;

    /** Free-text, cosmetic only: "Spouse", "Parent", "Child", "Other". */
    @Column(nullable = true)
    private String relationshipLabel;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
        if (role == null) role = "MEMBER";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRelationshipLabel() { return relationshipLabel; }
    public void setRelationshipLabel(String relationshipLabel) { this.relationshipLabel = relationshipLabel; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
