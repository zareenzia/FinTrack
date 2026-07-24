package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A configuration row, seeded via {@code DatabaseMigration} — same shape as
 * {@link AchievementDefinitionEntity}. {@code metricKey} reuses the exact same counter/sum metric
 * vocabulary {@link org.example.finzin.gamification.ProgressService} already increments (e.g.
 * "transactions.count"), so progress is recorded for free at the same event call sites that already
 * feed the lifetime achievement counters — no separate period-scoped query logic needed.
 */
@Entity
@Table(name = "challenge_definitions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"code"}, name = "uk_challenge_code")
})
public class ChallengeDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    /** MONTHLY only in V1 — WEEKLY/DAILY reserved for later without a schema change. */
    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String metricKey;

    @Column(nullable = false)
    private Double targetValue;

    @Column(nullable = false)
    private Integer xpReward;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (scope == null) scope = "MONTHLY";
        if (active == null) active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public Double getTargetValue() { return targetValue; }
    public void setTargetValue(Double targetValue) { this.targetValue = targetValue; }
    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
