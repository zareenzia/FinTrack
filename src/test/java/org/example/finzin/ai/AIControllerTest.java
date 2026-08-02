package org.example.finzin.ai;

import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiMessageEntity;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AIController}. Covers the chat endpoint's validation/error-mapping branches
 * and the conversation CRUD endpoints' not-found / happy-path branches.
 */
@WebMvcTest(AIController.class)
class AIControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private AIService aiService;
    @MockitoBean private ConversationService conversationService;

    private AiConversationEntity conversation(Long id, String title) {
        AiConversationEntity e = new AiConversationEntity();
        e.setId(id);
        e.setUserId(42L);
        e.setTitle(title);
        e.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 11, 0));
        return e;
    }

    private AiMessageEntity message(String role, String content) {
        AiMessageEntity m = new AiMessageEntity();
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 5));
        return m;
    }

    // ── POST /api/ai/chat ──────────────────────────────────────────────────

    @Test
    void chat_returnsMessageAndConversationId_withoutDebugKey_whenDebugNull() throws Exception {
        given(aiService.chat(42L, 5L, "Hello")).willReturn(new AIService.ChatResult(5L, "Hi there", null));

        mockMvc.perform(post("/api/ai/chat").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":5,\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(5))
                .andExpect(jsonPath("$.message").value("Hi there"))
                .andExpect(jsonPath("$.debug").doesNotExist());

        verify(aiService).chat(42L, 5L, "Hello");
    }

    @Test
    void chat_includesDebugKey_whenServiceReturnsDebugInfo() throws Exception {
        given(aiService.chat(eq(42L), eq(null), eq("Hi"))).willReturn(
                new AIService.ChatResult(9L, "Reply", Map.of("toolCalls", 2)));

        mockMvc.perform(post("/api/ai/chat").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debug.toolCalls").value(2));
    }

    @Test
    void chat_returnsBadRequest_whenMessageBlank() throws Exception {
        mockMvc.perform(post("/api/ai/chat").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message cannot be empty"));

        verify(aiService, never()).chat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void chat_returnsBadRequest_whenBodyNull() throws Exception {
        // A literal JSON "null" body never reaches the controller's own body == null check: Spring
        // rejects a null-deserialized non-optional @RequestBody with HttpMessageNotReadableException
        // before the handler method runs, so the response is a framework-generated 400 with no body
        // (not the controller's "Message cannot be empty" JSON) and the service is never invoked.
        mockMvc.perform(post("/api/ai/chat").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verify(aiService, never()).chat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void chat_returnsServiceUnavailable_whenAiDisabled() throws Exception {
        given(aiService.chat(eq(42L), eq(null), eq("Hi"))).willThrow(OpenAIException.disabled());

        mockMvc.perform(post("/api/ai/chat").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI Assistant is currently disabled. Enable it in Settings to start chatting."))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void chat_returnsTooManyRequests_whenUserRateLimited() throws Exception {
        given(aiService.chat(eq(42L), eq(null), eq("Hi"))).willThrow(OpenAIException.tooManyRequestsFromUser());

        mockMvc.perform(post("/api/ai/chat").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void chat_returnsBadGateway_forOtherOpenAIErrors() throws Exception {
        given(aiService.chat(eq(42L), eq(null), eq("Hi"))).willThrow(OpenAIException.upstreamError());

        mockMvc.perform(post("/api/ai/chat").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(status().isBadGateway());
    }

    // ── GET/POST /api/ai/conversations ────────────────────────────────────

    @Test
    void listConversations_returnsMappedConversations_andDefaultsToUserOne_whenUnauthenticated() throws Exception {
        given(conversationService.listForUser(1L)).willReturn(List.of(conversation(1L, "Chat 1")));

        mockMvc.perform(get("/api/ai/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Chat 1"));

        verify(conversationService).listForUser(1L);
    }

    @Test
    void listConversations_defaultsTitle_whenNull() throws Exception {
        given(conversationService.listForUser(42L)).willReturn(List.of(conversation(2L, null)));

        mockMvc.perform(get("/api/ai/conversations").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("New chat"));
    }

    @Test
    void createConversation_returnsCreatedWithMappedResponse() throws Exception {
        given(conversationService.create(42L)).willReturn(conversation(7L, null));

        mockMvc.perform(post("/api/ai/conversations").requestAttr("userId", 42L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("New chat"));
    }

    // ── PUT /api/ai/conversations/{id} ────────────────────────────────────

    @Test
    void renameConversation_returnsBadRequest_whenTitleBlank() throws Exception {
        mockMvc.perform(put("/api/ai/conversations/1").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Title cannot be empty"));
    }

    @Test
    void renameConversation_returnsNotFound_whenServiceReturnsNull() throws Exception {
        given(conversationService.rename(1L, 42L, "New Title")).willReturn(null);

        mockMvc.perform(put("/api/ai/conversations/1").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Title\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conversation not found"));
    }

    @Test
    void renameConversation_returnsRenamedConversation_onSuccess() throws Exception {
        given(conversationService.rename(1L, 42L, "New Title")).willReturn(conversation(1L, "New Title"));

        mockMvc.perform(put("/api/ai/conversations/1").requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  New Title  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"));

        verify(conversationService).rename(1L, 42L, "New Title");
    }

    // ── DELETE /api/ai/conversations/{id} ─────────────────────────────────

    @Test
    void deleteConversation_returnsNotFound_whenServiceReturnsFalse() throws Exception {
        given(conversationService.delete(1L, 42L)).willReturn(false);

        mockMvc.perform(delete("/api/ai/conversations/1").requestAttr("userId", 42L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conversation not found"));
    }

    @Test
    void deleteConversation_returnsNoContent_onSuccess() throws Exception {
        given(conversationService.delete(1L, 42L)).willReturn(true);

        mockMvc.perform(delete("/api/ai/conversations/1").requestAttr("userId", 42L))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/ai/conversations/{id}/messages ───────────────────────────

    @Test
    void getMessages_returnsNotFound_whenConversationNotOwned() throws Exception {
        given(conversationService.findOwned(1L, 42L)).willReturn(null);

        mockMvc.perform(get("/api/ai/conversations/1/messages").requestAttr("userId", 42L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conversation not found"));
    }

    @Test
    void getMessages_filtersOutToolMessages() throws Exception {
        given(conversationService.findOwned(1L, 42L)).willReturn(conversation(1L, "Chat"));
        given(conversationService.getAllMessages(1L, 42L)).willReturn(List.of(
                message("user", "Hi"),
                message("tool", "{\"result\":true}"),
                message("assistant", "Hello there")));

        mockMvc.perform(get("/api/ai/conversations/1/messages").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }
}
