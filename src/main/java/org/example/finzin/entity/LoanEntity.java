package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loans")
public class LoanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String loanName;

    /** PERSONAL, HOME, CAR, EDUCATION, BUSINESS, BORROWED, LENT */
    @Column(nullable = false)
    private String loanType;

    @Column(nullable = true)
    private String lenderBorrower;

    @Column(nullable = false)
    private Double principalAmount;

    @Column(nullable = true)
    private Double interestRate;

    @Column(nullable = true)
    private Double emiAmount;

    @Column(nullable = false)
    private LocalDate loanStartDate;

    @Column(nullable = true)
    private LocalDate loanEndDate;

    @Column(nullable = false)
    private Double remainingBalance;

    /** MONTHLY, WEEKLY, YEARLY, ONE_TIME */
    @Column(nullable = true)
    private String paymentFrequency;

    /** ACTIVE, CLOSED, OVERDUE */
    @Column(nullable = false)
    private String status;

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
    public String getLoanName() { return loanName; }
    public void setLoanName(String loanName) { this.loanName = loanName; }
    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }
    public String getLenderBorrower() { return lenderBorrower; }
    public void setLenderBorrower(String lenderBorrower) { this.lenderBorrower = lenderBorrower; }
    public Double getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(Double principalAmount) { this.principalAmount = principalAmount; }
    public Double getInterestRate() { return interestRate; }
    public void setInterestRate(Double interestRate) { this.interestRate = interestRate; }
    public Double getEmiAmount() { return emiAmount; }
    public void setEmiAmount(Double emiAmount) { this.emiAmount = emiAmount; }
    public LocalDate getLoanStartDate() { return loanStartDate; }
    public void setLoanStartDate(LocalDate loanStartDate) { this.loanStartDate = loanStartDate; }
    public LocalDate getLoanEndDate() { return loanEndDate; }
    public void setLoanEndDate(LocalDate loanEndDate) { this.loanEndDate = loanEndDate; }
    public Double getRemainingBalance() { return remainingBalance; }
    public void setRemainingBalance(Double remainingBalance) { this.remainingBalance = remainingBalance; }
    public String getPaymentFrequency() { return paymentFrequency; }
    public void setPaymentFrequency(String paymentFrequency) { this.paymentFrequency = paymentFrequency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
