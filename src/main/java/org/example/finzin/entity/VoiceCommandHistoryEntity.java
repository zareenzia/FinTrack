package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "voice_command_history")
public class VoiceCommandHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = true, length = 500)
    private String audioPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalTranscript;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String correctedText;

    @Column(nullable = false)
    private String intent;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String parsedJson;

    /** "AI" or "HEURISTIC" — which parser tier produced this result. */
    @Column(nullable = false)
    private String source;

    /** "pending" | "completed" | "cancelled" — an audit trail, not a source of truth (see resolvedEntityId). */
    @Column(nullable = false)
    private String status;

    @Column(nullable = true)
    private String resolvedEntityType;

    @Column(nullable = true)
    private Long resolvedEntityId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (source == null) source = "AI";
        if (status == null) status = "pending";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }
    public String getOriginalTranscript() { return originalTranscript; }
    public void setOriginalTranscript(String originalTranscript) { this.originalTranscript = originalTranscript; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getParsedJson() { return parsedJson; }
    public void setParsedJson(String parsedJson) { this.parsedJson = parsedJson; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolvedEntityType() { return resolvedEntityType; }
    public void setResolvedEntityType(String resolvedEntityType) { this.resolvedEntityType = resolvedEntityType; }
    public Long getResolvedEntityId() { return resolvedEntityId; }
    public void setResolvedEntityId(Long resolvedEntityId) { this.resolvedEntityId = resolvedEntityId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
