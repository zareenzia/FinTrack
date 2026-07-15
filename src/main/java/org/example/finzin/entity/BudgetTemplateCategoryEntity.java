package org.example.finzin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "budget_template_categories")
public class BudgetTemplateCategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Double plannedAmount;

    /** true = this row is a savings goal target, false = an expense category budget */
    @Column(nullable = false)
    private Boolean isSavings;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Double getPlannedAmount() { return plannedAmount; }
    public void setPlannedAmount(Double plannedAmount) { this.plannedAmount = plannedAmount; }
    public Boolean getIsSavings() { return isSavings; }
    public void setIsSavings(Boolean isSavings) { this.isSavings = isSavings; }
}
