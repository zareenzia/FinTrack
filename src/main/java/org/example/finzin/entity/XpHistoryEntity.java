package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row per XP award, append-only. {@code sourceId} is always non-null (real entity IDs are
 * stringified; calendar-scoped events like DAILY_ACTIVE synthesize a date string) so the single
 * plain unique constraint below dedups every event type uniformly — a partial/filtered index
 * scoped to "sourceId IS NOT NULL" would provide zero protection for exactly the events that have
 * no natural entity id, since Postgres treats every NULL as distinct under a plain UNIQUE constraint.
 */
@Entity
@Table(name = "xp_history", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "sourceType", "sourceId", "reason"}, name = "uk_xp_history_source")
})
public class XpHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private String sourceId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
