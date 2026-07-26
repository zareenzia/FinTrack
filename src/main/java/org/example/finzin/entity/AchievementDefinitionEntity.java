package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A configuration row, not user-specific — seeded via {@code DatabaseMigration}. Badges are
 * folded in here rather than a separate table, since the spec's own "each achievement unlocks a
 * badge" is a 1:1 relationship. Adding a new achievement at a new threshold for an existing
 * {@code metricKey} is a pure insert into this table; only a genuinely new metric needs a Java
 * change (a new {@code ProgressService} resolver method).
 */
@Entity
@Table(name = "achievement_definitions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"code"}, name = "uk_achievement_code")
})
public class AchievementDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String icon;

    @Column(nullable = false)
    private String tierColor;

    /** COUNT | CUMULATIVE_SUM | SINGLE_VALUE_REACHED | STREAK_DAYS | CONSECUTIVE_PERIODS */
    @Column(nullable = false)
    private String criteriaType;

    /** Resolved by a small fixed set of ProgressService methods, e.g. "transactions.count". */
    @Column(nullable = false)
    private String metricKey;

    @Column(nullable = false)
    private Double threshold;

    /** Nullable — reserved for a future "N occurrences within a bounded window" achievement shape. */
    @Column(nullable = true)
    private Integer windowDays;

    @Column(nullable = false)
    private Integer xpReward;

    @Column(nullable = false)
    private Boolean isMilestone;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isMilestone == null) isMilestone = false;
        if (active == null) active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getTierColor() { return tierColor; }
    public void setTierColor(String tierColor) { this.tierColor = tierColor; }
    public String getCriteriaType() { return criteriaType; }
    public void setCriteriaType(String criteriaType) { this.criteriaType = criteriaType; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }
    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
    public Boolean getIsMilestone() { return isMilestone; }
    public void setIsMilestone(Boolean isMilestone) { this.isMilestone = isMilestone; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
