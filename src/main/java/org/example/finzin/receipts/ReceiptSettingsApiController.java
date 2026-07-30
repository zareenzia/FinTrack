package org.example.finzin.receipts;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.ReceiptSettingsEntity;
import org.example.finzin.receipts.dto.UpdateReceiptSettingsRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/receipts/settings")
public class ReceiptSettingsApiController {
    private final ReceiptSettingsService settingsService;

    public ReceiptSettingsApiController(ReceiptSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public Map<String, Object> getSettings(HttpServletRequest request) {
        Long userId = getUserId(request);
        return toResponse(settingsService.getOrDefault(userId));
    }

    @PutMapping
    public Map<String, Object> updateSettings(HttpServletRequest request, @RequestBody UpdateReceiptSettingsRequest body) {
        Long userId = getUserId(request);
        ReceiptSettingsEntity updated = settingsService.update(userId, body == null ? null : body.enabled());
        return toResponse(updated);
    }

    private Map<String, Object> toResponse(ReceiptSettingsEntity entity) {
        return Map.of("enabled", entity.getEnabled());
    }
}
