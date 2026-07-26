package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One immutable log row per purchase-item event. Serves both "Activity Timeline" (all rows) and
 * "Price History" (rows filtered to activityType=PRICE_CHANGE) — avoids a separate table/JSON column.
 */
@Entity
@Table(name = "purchase_item_activity")
public class PurchaseItemActivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long purchaseItemId;

    @Column(nullable = false)
    private Long userId;

    /** CREATED, STATUS_CHANGE, PRICE_CHANGE, NEED_LEVEL_CHANGE, MONTH_CHANGE, EDITED, CANCELLED, PURCHASED */
    @Column(nullable = false, length = 30)
    private String activityType;

    @Column(nullable = true, length = 50)
    private String fieldName;

    @Column(nullable = true, length = 500)
    private String oldValue;

    @Column(nullable = true, length = 500)
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPurchaseItemId() { return purchaseItemId; }
    public void setPurchaseItemId(Long purchaseItemId) { this.purchaseItemId = purchaseItemId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
