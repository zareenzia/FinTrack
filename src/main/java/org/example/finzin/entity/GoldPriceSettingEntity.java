package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gold_price_settings")
public class GoldPriceSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    /** AUTOMATIC | MANUAL */
    @Column(nullable = false)
    private String mode = "AUTOMATIC";

    /** JSON: {"22K":{"GRAM":12345.0},"21K":{"GRAM":11900.0}, ...} */
    @Column(columnDefinition = "TEXT")
    private String manualPricesJson;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getManualPricesJson() { return manualPricesJson; }
    public void setManualPricesJson(String manualPricesJson) { this.manualPricesJson = manualPricesJson; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
