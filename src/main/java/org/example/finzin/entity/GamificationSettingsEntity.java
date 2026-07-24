package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gamification_settings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId"}, name = "uk_gamification_settings_user")
})
public class GamificationSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private Boolean enableNotifications;

    @Column(nullable = false)
    private Boolean showDashboardWidget;

    @Column(nullable = false)
    private Boolean enableCelebrations;

    @Column(nullable = false)
    private Boolean enableChallenges;

    @Column(nullable = false)
    private Boolean enableStreakTracking;

    @Column(nullable = false)
    private Boolean showXp;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
        if (enableNotifications == null) enableNotifications = true;
        if (showDashboardWidget == null) showDashboardWidget = true;
        if (enableCelebrations == null) enableCelebrations = true;
        if (enableChallenges == null) enableChallenges = true;
        if (enableStreakTracking == null) enableStreakTracking = true;
        if (showXp == null) showXp = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getEnableNotifications() { return enableNotifications; }
    public void setEnableNotifications(Boolean enableNotifications) { this.enableNotifications = enableNotifications; }
    public Boolean getShowDashboardWidget() { return showDashboardWidget; }
    public void setShowDashboardWidget(Boolean showDashboardWidget) { this.showDashboardWidget = showDashboardWidget; }
    public Boolean getEnableCelebrations() { return enableCelebrations; }
    public void setEnableCelebrations(Boolean enableCelebrations) { this.enableCelebrations = enableCelebrations; }
    public Boolean getEnableChallenges() { return enableChallenges; }
    public void setEnableChallenges(Boolean enableChallenges) { this.enableChallenges = enableChallenges; }
    public Boolean getEnableStreakTracking() { return enableStreakTracking; }
    public void setEnableStreakTracking(Boolean enableStreakTracking) { this.enableStreakTracking = enableStreakTracking; }
    public Boolean getShowXp() { return showXp; }
    public void setShowXp(Boolean showXp) { this.showXp = showXp; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
