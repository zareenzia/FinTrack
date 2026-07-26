package org.example.finzin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.ai.rag.EmbeddingClient;
import org.example.finzin.ai.rag.SemanticSearchService;
import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiSettingsEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test — no Spring context. This is the only place the tool-call loop's
 * mechanics get regression coverage after Phase 2B added a retrieval step ahead of it: there is
 * no pre-existing test suite for AIService or FinancialToolExecutor to lean on.
 */
@ExtendWith(MockitoExtension.class)
class AIServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long CONVERSATION_ID = 7L;

    @Mock private PromptBuilder promptBuilder;
    @Mock private OpenAIClient openAIClient;
    @Mock private FinancialToolExecutor toolExecutor;
    @Mock private ConversationService conversationService;
    @Mock private AiSettingsService aiSettingsService;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private SemanticSearchService semanticSearchService;
    @Mock private RetrievalContextBuilder retrievalContextBuilder;
    @Mock private QueryEmbeddingCache queryEmbeddingCache;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AIService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AIService(promptBuilder, openAIClient, toolExecutor, conversationService, aiSettingsService,
                objectMapper, embeddingClient, semanticSearchService, retrievalContextBuilder, queryEmbeddingCache, eventPublisher);

        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        when(conversationService.create(USER_ID)).thenReturn(conversation);
        when(conversationService.getRecentMessages(CONVERSATION_ID, USER_ID)).thenReturn(List.of());
    }

    private AiSettingsEntity settings(boolean developerMode) {
        AiSettingsEntity settings = new AiSettingsEntity();
        settings.setUserId(USER_ID);
        settings.setModel("gpt-5");
        settings.setMaxTokens(800);
        settings.setTemperature(0.3);
        settings.setEnabled(true);
        settings.setDeveloperMode(developerMode);
        return settings;
    }

    private JsonNode finalAnswerResponse(String text) throws Exception {
        return objectMapper.readTree("""
                {"output":[{"type":"message","content":[{"type":"output_text","text":"%s"}]}],
                 "usage":{"input_tokens":150,"output_tokens":30,"total_tokens":180}}
                """.formatted(text));
    }

    private JsonNode functionCallResponse() throws Exception {
        return objectMapper.readTree("""
                {"output":[{"type":"function_call","name":"getAccountBalances","call_id":"call_1","arguments":"{}"}],
                 "usage":{"input_tokens":100,"output_tokens":20,"total_tokens":120}}
                """);
    }

    @Test
    void retrievalFailureIsSwallowedAndChatStillCompletes() throws Exception {
        when(aiSettingsService.getOrDefault(USER_ID)).thenReturn(settings(false));
        when(queryEmbeddingCache.getOrCompute(any(), anyString(), any())).thenThrow(new RuntimeException("embedding boom"));
        when(promptBuilder.buildInitialInput(any(), anyString(), any())).thenReturn(new java.util.ArrayList<>(List.of(Map.of("role", "system", "content", "sys"))));
        when(toolExecutor.getToolDefinitions()).thenReturn(List.of());
        when(openAIClient.createResponse(any(), any(), anyString(), any(), any())).thenReturn(finalAnswerResponse("All good."));

        AIService.ChatResult result = aiService.chat(USER_ID, null, "How much did I spend?");

        assertEquals("All good.", result.message);
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptBuilder).buildInitialInput(any(), anyString(), contextCaptor.capture());
        assertEquals("", contextCaptor.getValue(), "retrieval failure must leave retrievedContext blank, not propagate");
    }

    @Test
    void emptyRetrievalLeavesRetrievedContextBlank() throws Exception {
        when(aiSettingsService.getOrDefault(USER_ID)).thenReturn(settings(false));
        when(queryEmbeddingCache.getOrCompute(any(), anyString(), any())).thenReturn(new float[1536]);
        when(semanticSearchService.search(eq(USER_ID), any(float[].class), anyInt())).thenReturn(List.of());
        when(retrievalContextBuilder.build(List.of())).thenReturn("");
        when(promptBuilder.buildInitialInput(any(), anyString(), any())).thenReturn(new java.util.ArrayList<>(List.of(Map.of("role", "system", "content", "sys"))));
        when(toolExecutor.getToolDefinitions()).thenReturn(List.of());
        when(openAIClient.createResponse(any(), any(), anyString(), any(), any())).thenReturn(finalAnswerResponse("Nothing indexed yet."));

        AIService.ChatResult result = aiService.chat(USER_ID, null, "How much did I spend?");

        assertEquals("Nothing indexed yet.", result.message);
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptBuilder).buildInitialInput(any(), anyString(), contextCaptor.capture());
        assertEquals("", contextCaptor.getValue());
    }

    @Test
    void toolLoopStillIteratesWithRetrievedContextPresent() throws Exception {
        when(aiSettingsService.getOrDefault(USER_ID)).thenReturn(settings(false));
        when(queryEmbeddingCache.getOrCompute(any(), anyString(), any())).thenReturn(new float[1536]);
        var docResult = new SemanticSearchService.SearchResult("TRANSACTION", 1L, "Grocery run", "Bought food", null, 0.8);
        when(semanticSearchService.search(eq(USER_ID), any(float[].class), anyInt())).thenReturn(List.of(docResult));
        when(retrievalContextBuilder.build(List.of(docResult))).thenReturn("Entity Type: TRANSACTION\nTitle: Grocery run");
        when(promptBuilder.buildInitialInput(any(), anyString(), any())).thenReturn(new java.util.ArrayList<>(List.of(Map.of("role", "system", "content", "sys"))));
        when(toolExecutor.getToolDefinitions()).thenReturn(List.of());
        when(toolExecutor.execute(eq("getAccountBalances"), anyString(), eq(USER_ID))).thenReturn(Map.of("accounts", List.of()));
        when(openAIClient.createResponse(any(), any(), anyString(), any(), any()))
                .thenReturn(functionCallResponse())
                .thenReturn(finalAnswerResponse("Your balance is $100."));

        AIService.ChatResult result = aiService.chat(USER_ID, null, "What's my balance?");

        assertEquals("Your balance is $100.", result.message);
        assertEquals(CONVERSATION_ID, result.conversationId);
        verify(toolExecutor, times(1)).execute(eq("getAccountBalances"), anyString(), eq(USER_ID));
        verify(openAIClient, times(2)).createResponse(any(), any(), anyString(), any(), any());
    }

    @Test
    void debugIsNullWhenDeveloperModeDisabled() throws Exception {
        when(aiSettingsService.getOrDefault(USER_ID)).thenReturn(settings(false));
        when(queryEmbeddingCache.getOrCompute(any(), anyString(), any())).thenReturn(new float[1536]);
        when(semanticSearchService.search(eq(USER_ID), any(float[].class), anyInt())).thenReturn(List.of());
        when(retrievalContextBuilder.build(List.of())).thenReturn("");
        when(promptBuilder.buildInitialInput(any(), anyString(), any())).thenReturn(new java.util.ArrayList<>(List.of(Map.of("role", "system", "content", "sys"))));
        when(toolExecutor.getToolDefinitions()).thenReturn(List.of());
        when(openAIClient.createResponse(any(), any(), anyString(), any(), any())).thenReturn(finalAnswerResponse("ok"));

        AIService.ChatResult result = aiService.chat(USER_ID, null, "hi");

        assertNull(result.debug);
    }

    @Test
    void debugIsPopulatedWhenDeveloperModeEnabled() throws Exception {
        when(aiSettingsService.getOrDefault(USER_ID)).thenReturn(settings(true));
        when(queryEmbeddingCache.getOrCompute(any(), anyString(), any())).thenReturn(new float[1536]);
        var docResult = new SemanticSearchService.SearchResult("NOTE", 2L, "Reminder", "content", null, 0.42);
        when(semanticSearchService.search(eq(USER_ID), any(float[].class), anyInt())).thenReturn(List.of(docResult));
        when(retrievalContextBuilder.build(List.of(docResult))).thenReturn("Entity Type: NOTE\nTitle: Reminder");
        when(promptBuilder.buildInitialInput(any(), anyString(), any())).thenReturn(new java.util.ArrayList<>(List.of(Map.of("role", "system", "content", "sys"))));
        when(toolExecutor.getToolDefinitions()).thenReturn(List.of());
        when(openAIClient.createResponse(any(), any(), anyString(), any(), any())).thenReturn(finalAnswerResponse("ok"));

        AIService.ChatResult result = aiService.chat(USER_ID, null, "hi");

        assertNotNull(result.debug);
        assertNotNull(result.debug.get("embeddingProvider"));
        assertEquals(1, result.debug.get("documentCount"));
        assertEquals(0, result.debug.get("toolCalls"));
        assertNotNull(result.debug.get("executionTimeMs"));
        assertEquals(150, result.debug.get("promptTokens"));
        assertEquals(30, result.debug.get("completionTokens"));
        assertEquals(180, result.debug.get("totalTokens"));
    }
}
