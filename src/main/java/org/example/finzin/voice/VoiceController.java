package org.example.finzin.voice;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.VoiceSettingsEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    private final VoiceService voiceService;

    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @PostMapping("/parse")
    public ResponseEntity<?> parse(HttpServletRequest request, @RequestBody ParseRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.transcript() == null || body.transcript().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "transcript is required"));
        }
        VoiceResultDTO result = voiceService.parseCommand(userId, body.transcript(), body.priorState());
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/history/{id}")
    public ResponseEntity<?> updateHistoryStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody HistoryUpdateRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.status() == null || body.status().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        boolean updated = voiceService.updateHistoryStatus(userId, id, body.status(), body.resolvedEntityType(), body.resolvedEntityId());
        if (!updated) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory(HttpServletRequest request) {
        return voiceService.getHistory(getUserId(request));
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteHistoryItem(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        boolean deleted = voiceService.deleteHistoryItem(userId, id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history")
    public ResponseEntity<?> clearHistory(HttpServletRequest request) {
        voiceService.clearHistory(getUserId(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings(HttpServletRequest request) {
        return toSettingsResponse(voiceService.getOrDefaultSettings(getUserId(request)));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(HttpServletRequest request, @RequestBody SettingsRequest body) {
        Long userId = getUserId(request);
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        VoiceSettingsEntity updated = voiceService.updateSettings(userId, body.enabled(), body.language(), body.speechProvider(),
                body.autoStopSilenceSeconds(), body.noiseReduction(), body.saveAudioRecordings(),
                body.maxRecordingLengthSeconds(), body.speechSpeed());
        return ResponseEntity.ok(toSettingsResponse(updated));
    }

    private Map<String, Object> toSettingsResponse(VoiceSettingsEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", entity.getEnabled());
        map.put("language", entity.getLanguage());
        map.put("speechProvider", entity.getSpeechProvider());
        map.put("autoStopSilenceSeconds", entity.getAutoStopSilenceSeconds());
        map.put("noiseReduction", entity.getNoiseReduction());
        map.put("saveAudioRecordings", entity.getSaveAudioRecordings());
        map.put("maxRecordingLengthSeconds", entity.getMaxRecordingLengthSeconds());
        map.put("speechSpeed", entity.getSpeechSpeed());
        return map;
    }

    private record ParseRequest(String transcript, VoicePriorState priorState) {
    }

    private record HistoryUpdateRequest(String status, String resolvedEntityType, Long resolvedEntityId) {
    }

    private record SettingsRequest(Boolean enabled, String language, String speechProvider, Integer autoStopSilenceSeconds,
                                    Boolean noiseReduction, Boolean saveAudioRecordings, Integer maxRecordingLengthSeconds,
                                    Double speechSpeed) {
    }
}
