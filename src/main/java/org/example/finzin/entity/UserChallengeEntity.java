package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_challenges", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "challengeId", "periodKey"}, name = "uk_user_challenge_period")
})
public class UserChallengeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long challengeId;

    /** "yyyy-MM" — the calendar month this instance belongs to. */
    @Column(nullable = false, length = 10)
    private String periodKey;

    @Column(nullable = false)
    private Double progressCurrent;

    @Column(nullable = false)
    private Double targetValue;

    /** IN_PROGRESS | COMPLETED | EXPIRED */
    @Column(nullable = false)
    private String status;

    @Column(nullable = true)
    private LocalDateTime completedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (progressCurrent == null) progressCurrent = 0.0;
        if (status == null) status = "IN_PROGRESS";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getChallengeId() { return challengeId; }
    public void setChallengeId(Long challengeId) { this.challengeId = challengeId; }
    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }
    public Double getProgressCurrent() { return progressCurrent; }
    public void setProgressCurrent(Double progressCurrent) { this.progressCurrent = progressCurrent; }
    public Double getTargetValue() { return targetValue; }
    public void setTargetValue(Double targetValue) { this.targetValue = targetValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
