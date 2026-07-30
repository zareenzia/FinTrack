package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Links an existing, already-created personal {@link TransactionEntity} (type="savings") into a
 * household goal's contribution ledger — mirrors {@link SharedTransactionEntity} exactly. The real
 * money movement (and its effect on the contributor's own account balance) is owned entirely by the
 * normal transaction system; this row only records that it counts toward the joint goal. */
@Entity
@Table(name = "household_goal_contributions")
public class HouseholdGoalContributionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long householdGoalId;

    @Column(nullable = false)
    private Long userId;

    /** References TransactionEntity.id — no JPA FK, per app convention. */
    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private LocalDate contributedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (contributedAt == null) contributedAt = LocalDate.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseholdGoalId() { return householdGoalId; }
    public void setHouseholdGoalId(Long householdGoalId) { this.householdGoalId = householdGoalId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDate getContributedAt() { return contributedAt; }
    public void setContributedAt(LocalDate contributedAt) { this.contributedAt = contributedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
