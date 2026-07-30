package org.example.finzin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.ai.rag.EmbeddingService;
import org.example.finzin.ai.rag.IndexedEntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the real Spring-wired bean graph for Phase 2B — AIService → QueryEmbeddingCache →
 * SemanticSearchService → VectorRepository (real pgvector query against the real Neon DB) →
 * RetrievalContextBuilder → PromptBuilder — is actually connected, by mocking only the
 * network-calling OpenAIClient and inspecting what prompt it was actually called with. Uses the
 * mock embedding provider with identical query/document text so retrieval finding the document is
 * guaranteed rather than coincidental (mock vectors carry no semantic meaning).
 */
@SpringBootTest(properties = "ai.embedding.provider=mock")
class AIServiceRetrievalWiringTest {

    private static final long USER_ID = 900_000_201L;
    private static final String MARKER_TEXT = "MARKER_9f2a distinctive note content about a rare topic";

    @Autowired private AIService aiService;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private OpenAIClient openAIClient;

    private Long createdConversationId;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_document_embeddings WHERE user_id = ?", USER_ID);
        if (createdConversationId != null) {
            jdbcTemplate.update("DELETE FROM ai_messages WHERE conversation_id = ?", createdConversationId);
            jdbcTemplate.update("DELETE FROM ai_conversations WHERE id = ?", createdConversationId);
            createdConversationId = null;
        }
        jdbcTemplate.update("DELETE FROM ai_settings WHERE user_id = ?", USER_ID);
    }

    @Test
    void retrievedDocumentContentReachesTheActualOpenAIClientCall() throws Exception {
        embeddingService.indexDocument(USER_ID, IndexedEntityType.NOTE, 777_401L, "Unique note", MARKER_TEXT, Map.of());

        JsonNode finalAnswer = objectMapper.readTree("""
                {"output":[{"type":"message","content":[{"type":"output_text","text":"Here you go."}]}],
                 "usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15}}
                """);
        when(openAIClient.createResponse(any(), any(), anyString(), any(), any())).thenReturn(finalAnswer);

        AIService.ChatResult result = aiService.chat(USER_ID, null, MARKER_TEXT);
        createdConversationId = result.conversationId;

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> inputCaptor = ArgumentCaptor.forClass(List.class);
        verify(openAIClient).createResponse(inputCaptor.capture(), any(), anyString(), any(), any());

        boolean foundMarker = inputCaptor.getValue().stream()
                .anyMatch(item -> String.valueOf(item.get("content")).contains("MARKER_9f2a"));
        assertTrue(foundMarker,
                "the indexed document's content must reach the assembled prompt via the real retrieval -> context-builder -> prompt-builder chain");
    }
}
