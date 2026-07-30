package org.example.finzin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "shared_transaction_shares")
public class SharedTransactionShareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sharedTransactionId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Double shareAmount;

    /** Only meaningful for the PERCENTAGE split method. */
    @Column(nullable = true)
    private Double sharePercent;

    @Column(nullable = false)
    private Boolean isPayer;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSharedTransactionId() { return sharedTransactionId; }
    public void setSharedTransactionId(Long sharedTransactionId) { this.sharedTransactionId = sharedTransactionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Double getShareAmount() { return shareAmount; }
    public void setShareAmount(Double shareAmount) { this.shareAmount = shareAmount; }
    public Double getSharePercent() { return sharePercent; }
    public void setSharePercent(Double sharePercent) { this.sharePercent = sharePercent; }
    public Boolean getIsPayer() { return isPayer; }
    public void setIsPayer(Boolean isPayer) { this.isPayer = isPayer; }
}
