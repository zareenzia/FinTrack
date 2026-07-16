package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gold_prices")
public class GoldPriceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GRAM | VORI | ANA | RATI */
    @Column(nullable = false)
    private String unit;

    /** 22K | 21K | 18K | TRADITIONAL */
    @Column(nullable = false)
    private String purity;

    private Double marketPrice;

    private Double oldSellingPrice;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private LocalDateTime retrievedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getPurity() { return purity; }
    public void setPurity(String purity) { this.purity = purity; }
    public Double getMarketPrice() { return marketPrice; }
    public void setMarketPrice(Double marketPrice) { this.marketPrice = marketPrice; }
    public Double getOldSellingPrice() { return oldSellingPrice; }
    public void setOldSellingPrice(Double oldSellingPrice) { this.oldSellingPrice = oldSellingPrice; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public void setRetrievedAt(LocalDateTime retrievedAt) { this.retrievedAt = retrievedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
