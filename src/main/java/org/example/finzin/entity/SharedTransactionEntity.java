package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Links an existing, already-created personal {@link TransactionEntity} into a household's shared
 * ledger. The real expense (and its effect on the payer's account balance) is owned entirely by the
 * normal transaction system — this row only records that it was shared, by whom, and how it was split. */
@Entity
@Table(name = "shared_transactions")
public class SharedTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long householdId;

    /** References TransactionEntity.id — no JPA FK, per app convention. */
    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private Long payerUserId;

    @Column(nullable = false)
    private Double totalAmount;

    /** EQUAL, PERCENTAGE, FIXED_AMOUNT */
    @Column(nullable = false)
    private String splitMethod;

    @Column(nullable = false)
    private String description;

    @Column(nullable = true)
    private String category;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public Long getPayerUserId() { return payerUserId; }
    public void setPayerUserId(Long payerUserId) { this.payerUserId = payerUserId; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public String getSplitMethod() { return splitMethod; }
    public void setSplitMethod(String splitMethod) { this.splitMethod = splitMethod; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
