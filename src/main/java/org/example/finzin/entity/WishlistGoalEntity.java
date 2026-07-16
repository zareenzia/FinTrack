package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "wishlist_goals")
public class WishlistGoalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String goalName;

    @Column(nullable = true)
    private String category;

    @Column(nullable = false)
    private Double targetAmount;

    @Column(nullable = false)
    private Double savedAmount;

    @Column(nullable = true)
    private LocalDate targetDate;

    /** HIGH, MEDIUM, LOW */
    @Column(nullable = false)
    private String priority;

    /** IN_PROGRESS, COMPLETED, CANCELLED */
    @Column(nullable = false)
    private String status;

    @Column(nullable = true)
    private String icon;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(Double targetAmount) { this.targetAmount = targetAmount; }
    public Double getSavedAmount() { return savedAmount; }
    public void setSavedAmount(Double savedAmount) { this.savedAmount = savedAmount; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
