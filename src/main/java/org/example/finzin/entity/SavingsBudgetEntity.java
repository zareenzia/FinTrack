package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_budgets", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"budgetPlanId", "categoryId"}, name = "uk_savings_budget_plan_category")
})
public class SavingsBudgetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long budgetPlanId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Double targetAmount;

    /** Money already saved toward this goal before it was tracked here — not backed by an in-app transaction. */
    @Column(nullable = false)
    private Double initialAmount;

    /** Account where this money is physically held (bank / cash / MFS) — optional. */
    @Column(nullable = true)
    private Long storageAccountId;

    /** Account the money originally came from (e.g. salary account) — optional. */
    @Column(nullable = true)
    private Long sourceAccountId;

    /** Free-text source when the money didn't come from a tracked account (e.g. "Bonus", "Gift from parents"). Mutually exclusive with sourceAccountId. */
    @Column(nullable = true)
    private String sourceDescription;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (initialAmount == null) initialAmount = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBudgetPlanId() { return budgetPlanId; }
    public void setBudgetPlanId(Long budgetPlanId) { this.budgetPlanId = budgetPlanId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(Double targetAmount) { this.targetAmount = targetAmount; }
    public Double getInitialAmount() { return initialAmount; }
    public void setInitialAmount(Double initialAmount) { this.initialAmount = initialAmount; }
    public Long getStorageAccountId() { return storageAccountId; }
    public void setStorageAccountId(Long storageAccountId) { this.storageAccountId = storageAccountId; }
    public Long getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(Long sourceAccountId) { this.sourceAccountId = sourceAccountId; }
    public String getSourceDescription() { return sourceDescription; }
    public void setSourceDescription(String sourceDescription) { this.sourceDescription = sourceDescription; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
