package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.SidebarPreferenceEntity;
import org.example.finzin.repository.SidebarPreferenceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sidebar-preferences")
public class SidebarPreferenceApiController {

    private final SidebarPreferenceRepository repository;

    public SidebarPreferenceApiController(SidebarPreferenceRepository repository) {
        this.repository = repository;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    @GetMapping
    public ResponseEntity<?> getPreferences(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repository.findByUserId(userId)
                .map(e -> ResponseEntity.ok(Map.of("preferencesJson", e.getPreferencesJson() != null ? e.getPreferencesJson() : "[]")))
                .orElse(ResponseEntity.ok(Map.of("preferencesJson", "[]")));
    }

    @PutMapping
    public ResponseEntity<?> savePreferences(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        String json = body.getOrDefault("preferencesJson", "[]");
        SidebarPreferenceEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> { SidebarPreferenceEntity e = new SidebarPreferenceEntity(); e.setUserId(userId); return e; });
        entity.setPreferencesJson(json);
        repository.save(entity);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @DeleteMapping
    public ResponseEntity<?> resetPreferences(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        repository.findByUserId(userId).ifPresent(repository::delete);
        return ResponseEntity.ok(Map.of("status", "reset"));
    }
}
