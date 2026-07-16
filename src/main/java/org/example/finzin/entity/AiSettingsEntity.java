package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_settings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId"}, name = "uk_ai_settings_user")
})
public class AiSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private Integer maxTokens;

    @Column(nullable = false)
    private Double temperature;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private Boolean developerMode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (provider == null) provider = "openai";
        if (model == null) model = "gpt-5";
        if (maxTokens == null) maxTokens = 800;
        if (temperature == null) temperature = 0.3;
        if (enabled == null) enabled = true;
        if (developerMode == null) developerMode = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getDeveloperMode() { return developerMode; }
    public void setDeveloperMode(Boolean developerMode) { this.developerMode = developerMode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
