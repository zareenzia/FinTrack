package org.example.finzin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categories", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"userId", "name"}, name = "uk_user_category_name")
})
public class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private String icon;

    /** null or "general" = compatible with any transaction type */
    @Column(nullable = true)
    private String categoryType;

    public CategoryEntity() {
    }

    public CategoryEntity(Long userId, String name, String description, String color, String icon) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.color = color;
        this.icon = icon;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(String categoryType) {
        this.categoryType = categoryType;
    }
}
