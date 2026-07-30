package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A lightweight per-user aggregate (count or cumulative sum) keyed by a metric name, e.g. "transactions.count". */
@Entity
@Table(name = "user_stat_counters", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "counterKey"}, name = "uk_user_stat_counter")
})
public class UserStatCounterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String counterKey;

    @Column(nullable = false)
    private Double counterValue;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
        if (counterValue == null) counterValue = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCounterKey() { return counterKey; }
    public void setCounterKey(String counterKey) { this.counterKey = counterKey; }
    public Double getCounterValue() { return counterValue; }
    public void setCounterValue(Double counterValue) { this.counterValue = counterValue; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
