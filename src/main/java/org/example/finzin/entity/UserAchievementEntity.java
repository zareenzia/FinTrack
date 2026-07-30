package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_achievements", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "achievementId"}, name = "uk_user_achievement")
})
public class UserAchievementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long achievementId;

    /** LOCKED | UNLOCKED */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Double progressCurrent;

    @Column(nullable = false)
    private Double progressTarget;

    @Column(nullable = true)
    private LocalDateTime unlockedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
        if (status == null) status = "LOCKED";
        if (progressCurrent == null) progressCurrent = 0.0;
        if (progressTarget == null) progressTarget = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAchievementId() { return achievementId; }
    public void setAchievementId(Long achievementId) { this.achievementId = achievementId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getProgressCurrent() { return progressCurrent; }
    public void setProgressCurrent(Double progressCurrent) { this.progressCurrent = progressCurrent; }
    public Double getProgressTarget() { return progressTarget; }
    public void setProgressTarget(Double progressTarget) { this.progressTarget = progressTarget; }
    public LocalDateTime getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(LocalDateTime unlockedAt) { this.unlockedAt = unlockedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
