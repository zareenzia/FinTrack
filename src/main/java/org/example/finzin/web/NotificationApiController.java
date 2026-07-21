package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.NotificationEntity;
import org.example.finzin.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public List<Map<String, Object>> getNotifications(HttpServletRequest request) {
        Long userId = getUserId(request);
        return notificationService.getForUser(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/unread-count")
    public Map<String, Object> getUnreadCount(HttpServletRequest request) {
        Long userId = getUserId(request);
        return Map.of("unreadCount", notificationService.unreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markRead(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        NotificationEntity updated = notificationService.markRead(id, userId);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Notification not found"));
        }
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/read-all")
    public Map<String, Object> markAllRead(HttpServletRequest request) {
        Long userId = getUserId(request);
        int updated = notificationService.markAllRead(userId);
        return Map.of("updated", updated);
    }

    private Map<String, Object> toResponse(NotificationEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("type", entity.getType());
        map.put("title", entity.getTitle());
        map.put("message", entity.getMessage());
        map.put("relatedEntityType", entity.getRelatedEntityType());
        map.put("relatedEntityId", entity.getRelatedEntityId());
        map.put("isRead", entity.getIsRead());
        map.put("createdAt", entity.getCreatedAt().toString());
        return map;
    }
}
