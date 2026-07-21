package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "net_worth_snapshots", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "snapshotMonth"}, name = "uk_networth_snapshot_user_month")
})
public class NetWorthSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** "yyyy-MM" */
    @Column(nullable = false, length = 7)
    private String snapshotMonth;

    @Column(nullable = false)
    private Double netWorth;

    @Column(nullable = false)
    private Double totalAssets;

    @Column(nullable = false)
    private Double balance;

    @Column(nullable = false)
    private Double totalSavingsContributed;

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
    public String getSnapshotMonth() { return snapshotMonth; }
    public void setSnapshotMonth(String snapshotMonth) { this.snapshotMonth = snapshotMonth; }
    public Double getNetWorth() { return netWorth; }
    public void setNetWorth(Double netWorth) { this.netWorth = netWorth; }
    public Double getTotalAssets() { return totalAssets; }
    public void setTotalAssets(Double totalAssets) { this.totalAssets = totalAssets; }
    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
    public Double getTotalSavingsContributed() { return totalSavingsContributed; }
    public void setTotalSavingsContributed(Double totalSavingsContributed) { this.totalSavingsContributed = totalSavingsContributed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
