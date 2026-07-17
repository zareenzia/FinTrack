package org.example.finzin.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiMessageEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AIController {
    private final AIService aiService;
    private final ConversationService conversationService;

    public AIController(AIService aiService, ConversationService conversationService) {
        this.aiService = aiService;
        this.conversationService = conversationService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(HttpServletRequest request, @RequestBody ChatRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.message() == null || body.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty"));
        }
        try {
            AIService.ChatResult result = aiService.chat(userId, body.conversationId(), body.message());
            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("conversationId", result.conversationId);
            responseBody.put("message", result.message);
            if (result.debug != null) {
                responseBody.put("debug", result.debug);
            }
            return ResponseEntity.ok(responseBody);
        } catch (OpenAIException e) {
            HttpStatus status = "DISABLED".equals(e.getErrorTag()) || "NOT_CONFIGURED".equals(e.getErrorTag())
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : ("USER_RATE_LIMIT".equals(e.getErrorTag()) || "RATE_LIMIT".equals(e.getErrorTag()) ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY);
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", e.getUserMessage());
            errorBody.put("retryable", e.isRetryable());
            return ResponseEntity.status(status).body(errorBody);
        }
    }

    @GetMapping("/conversations")
    public List<Map<String, Object>> listConversations(HttpServletRequest request) {
        Long userId = getUserId(request);
        return conversationService.listForUser(userId).stream()
                .map(this::toConversationResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(HttpServletRequest request) {
        Long userId = getUserId(request);
        AiConversationEntity created = conversationService.create(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toConversationResponse(created));
    }

    @PutMapping("/conversations/{id}")
    public ResponseEntity<?> renameConversation(HttpServletRequest request, @PathVariable Long id, @RequestBody RenameRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.title() == null || body.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title cannot be empty"));
        }
        AiConversationEntity renamed = conversationService.rename(id, userId, body.title().trim());
        if (renamed == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Conversation not found"));
        }
        return ResponseEntity.ok(toConversationResponse(renamed));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        boolean deleted = conversationService.delete(id, userId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Conversation not found"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<?> getMessages(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        if (conversationService.findOwned(id, userId) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Conversation not found"));
        }
        List<Map<String, Object>> messages = conversationService.getAllMessages(id, userId).stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(messages);
    }

    private Map<String, Object> toConversationResponse(AiConversationEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("title", entity.getTitle() != null ? entity.getTitle() : "New chat");
        map.put("createdAt", entity.getCreatedAt().toString());
        map.put("updatedAt", entity.getUpdatedAt().toString());
        return map;
    }

    private Map<String, Object> toMessageResponse(AiMessageEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", entity.getRole());
        map.put("content", entity.getContent());
        map.put("createdAt", entity.getCreatedAt().toString());
        return map;
    }

    private record ChatRequest(Long conversationId, String message) {}
    private record RenameRequest(String title) {}
}
