package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_items")
public class PurchaseItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Double estimatedPrice;

    @Column(nullable = true)
    private String category;

    /** MUST_HAVE, SHOULD_HAVE, NICE_TO_HAVE */
    @Column(nullable = false)
    private String needLevel;

    /** CRITICAL, HIGH, MEDIUM, LOW */
    @Column(nullable = false)
    private String priority;

    @Column(nullable = true)
    private String brand;

    @Column(nullable = true)
    private String store;

    @Column(nullable = true, length = 1000)
    private String purchaseUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** "yyyy-MM", nullable ("unscheduled") */
    @Column(nullable = true, length = 7)
    private String targetMonth;

    @Column(nullable = true)
    private LocalDate expectedPurchaseDate;

    @Column(nullable = true, length = 500)
    private String imagePath;

    /** References SavingsBudgetEntity.id — no JPA FK, per app convention. */
    @Column(nullable = true)
    private Long linkedSavingsGoalId;

    /** Set once this item is marked purchased and linked to a real TransactionEntity. */
    @Column(nullable = true)
    private Long linkedTransactionId;

    /** PLANNING, WAITING, READY, PURCHASED, CANCELLED */
    @Column(nullable = false)
    private String status;

    /** TOO_EXPENSIVE, NOT_NEEDED, CHANGED_MIND, BOUGHT_ELSEWHERE */
    @Column(nullable = true, length = 30)
    private String cancelReason;

    /** CAN_BUY_NOW, WAIT, NOT_AFFORDABLE — internal, only for the notification scheduler's transition detection. */
    @Column(nullable = true, length = 20)
    private String lastAffordabilityStatus;

    @Column(nullable = true)
    private LocalDateTime purchasedAt;

    @Column(nullable = true)
    private LocalDateTime cancelledAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (needLevel == null) needLevel = "SHOULD_HAVE";
        if (priority == null) priority = "MEDIUM";
        if (status == null) status = "PLANNING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Double getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(Double estimatedPrice) { this.estimatedPrice = estimatedPrice; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getNeedLevel() { return needLevel; }
    public void setNeedLevel(String needLevel) { this.needLevel = needLevel; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
    public String getPurchaseUrl() { return purchaseUrl; }
    public void setPurchaseUrl(String purchaseUrl) { this.purchaseUrl = purchaseUrl; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getTargetMonth() { return targetMonth; }
    public void setTargetMonth(String targetMonth) { this.targetMonth = targetMonth; }
    public LocalDate getExpectedPurchaseDate() { return expectedPurchaseDate; }
    public void setExpectedPurchaseDate(LocalDate expectedPurchaseDate) { this.expectedPurchaseDate = expectedPurchaseDate; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public Long getLinkedSavingsGoalId() { return linkedSavingsGoalId; }
    public void setLinkedSavingsGoalId(Long linkedSavingsGoalId) { this.linkedSavingsGoalId = linkedSavingsGoalId; }
    public Long getLinkedTransactionId() { return linkedTransactionId; }
    public void setLinkedTransactionId(Long linkedTransactionId) { this.linkedTransactionId = linkedTransactionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getLastAffordabilityStatus() { return lastAffordabilityStatus; }
    public void setLastAffordabilityStatus(String lastAffordabilityStatus) { this.lastAffordabilityStatus = lastAffordabilityStatus; }
    public LocalDateTime getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(LocalDateTime purchasedAt) { this.purchasedAt = purchasedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
