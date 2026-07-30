package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "voice_settings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId"}, name = "uk_voice_settings_user")
})
public class VoiceSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String speechProvider;

    @Column(nullable = false)
    private Integer autoStopSilenceSeconds;

    /** Inert in V1 — no real noise-reduction DSP is applied yet. */
    @Column(nullable = false)
    private Boolean noiseReduction;

    /** Inert in V1 — no audio blobs are captured or stored yet. */
    @Column(nullable = false)
    private Boolean saveAudioRecordings;

    @Column(nullable = false)
    private Integer maxRecordingLengthSeconds;

    /** Inert in V1 — no TTS/speech-speed behavior is wired yet. */
    @Column(nullable = false)
    private Double speechSpeed;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
        if (language == null) language = "en-US";
        if (speechProvider == null) speechProvider = "browser";
        if (autoStopSilenceSeconds == null) autoStopSilenceSeconds = 3;
        if (noiseReduction == null) noiseReduction = false;
        if (saveAudioRecordings == null) saveAudioRecordings = false;
        if (maxRecordingLengthSeconds == null) maxRecordingLengthSeconds = 60;
        if (speechSpeed == null) speechSpeed = 1.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getSpeechProvider() { return speechProvider; }
    public void setSpeechProvider(String speechProvider) { this.speechProvider = speechProvider; }
    public Integer getAutoStopSilenceSeconds() { return autoStopSilenceSeconds; }
    public void setAutoStopSilenceSeconds(Integer autoStopSilenceSeconds) { this.autoStopSilenceSeconds = autoStopSilenceSeconds; }
    public Boolean getNoiseReduction() { return noiseReduction; }
    public void setNoiseReduction(Boolean noiseReduction) { this.noiseReduction = noiseReduction; }
    public Boolean getSaveAudioRecordings() { return saveAudioRecordings; }
    public void setSaveAudioRecordings(Boolean saveAudioRecordings) { this.saveAudioRecordings = saveAudioRecordings; }
    public Integer getMaxRecordingLengthSeconds() { return maxRecordingLengthSeconds; }
    public void setMaxRecordingLengthSeconds(Integer maxRecordingLengthSeconds) { this.maxRecordingLengthSeconds = maxRecordingLengthSeconds; }
    public Double getSpeechSpeed() { return speechSpeed; }
    public void setSpeechSpeed(Double speechSpeed) { this.speechSpeed = speechSpeed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
