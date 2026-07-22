package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.AppearancePreferenceEntity;
import org.example.finzin.repository.AppearancePreferenceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/appearance-preferences")
public class AppearancePreferenceApiController {

    private final AppearancePreferenceRepository repository;

    public AppearancePreferenceApiController(AppearancePreferenceRepository repository) {
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
        Map<String, Object> result = new HashMap<>();
        repository.findByUserId(userId).ifPresentOrElse(e -> {
            result.put("theme", e.getTheme());
            result.put("colorTheme", e.getColorTheme());
        }, () -> {
            result.put("theme", null);
            result.put("colorTheme", null);
        });
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<?> savePreferences(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        AppearancePreferenceEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> { AppearancePreferenceEntity e = new AppearancePreferenceEntity(); e.setUserId(userId); return e; });
        if (body.containsKey("theme")) entity.setTheme(body.get("theme"));
        if (body.containsKey("colorTheme")) entity.setColorTheme(body.get("colorTheme"));
        repository.save(entity);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }
}
