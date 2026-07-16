package org.example.finzin.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.ai.rag.EmbeddingClient;
import org.example.finzin.ai.rag.SemanticSearchService;
import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiMessageEntity;
import org.example.finzin.entity.AiSettingsEntity;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Orchestrates one chat turn: loads settings/history, builds the prompt, runs the OpenAI
 * tool-call loop, persists messages, and logs metrics only (never prompt/tool content).
 */
@Service
public class AIService {
    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final Duration MAX_LOOP_DURATION = Duration.ofSeconds(90);
    private static final int USER_RATE_LIMIT_PER_MINUTE = 15;

    private static final int RETRIEVAL_LIMIT = 8;

    private final PromptBuilder promptBuilder;
    private final OpenAIClient openAIClient;
    private final FinancialToolExecutor toolExecutor;
    private final ConversationService conversationService;
    private final AiSettingsService aiSettingsService;
    private final ObjectMapper objectMapper;
    private final EmbeddingClient embeddingClient;
    private final SemanticSearchService semanticSearchService;
    private final RetrievalContextBuilder retrievalContextBuilder;
    private final QueryEmbeddingCache queryEmbeddingCache;

    private final Map<Long, Deque<Instant>> userRequestTimestamps = new ConcurrentHashMap<>();

    public AIService(PromptBuilder promptBuilder, OpenAIClient openAIClient, FinancialToolExecutor toolExecutor,
                      ConversationService conversationService, AiSettingsService aiSettingsService, ObjectMapper objectMapper,
                      EmbeddingClient embeddingClient, SemanticSearchService semanticSearchService,
                      RetrievalContextBuilder retrievalContextBuilder, QueryEmbeddingCache queryEmbeddingCache) {
        this.promptBuilder = promptBuilder;
        this.openAIClient = openAIClient;
        this.toolExecutor = toolExecutor;
        this.conversationService = conversationService;
        this.aiSettingsService = aiSettingsService;
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.semanticSearchService = semanticSearchService;
        this.retrievalContextBuilder = retrievalContextBuilder;
        this.queryEmbeddingCache = queryEmbeddingCache;
    }

    public static class ChatResult {
        public final Long conversationId;
        public final String message;
        public final Map<String, Object> debug;
        public ChatResult(Long conversationId, String message, Map<String, Object> debug) {
            this.conversationId = conversationId;
            this.message = message;
            this.debug = debug;
        }
    }

    public ChatResult chat(Long userId, Long conversationId, String rawMessage) {
        Instant startedAt = Instant.now();
        AiSettingsEntity settings = aiSettingsService.getOrDefault(userId);
        String model = settings.getModel();

        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            throw OpenAIException.disabled();
        }
        enforceUserRateLimit(userId);

        String message = sanitize(rawMessage);

        AiConversationEntity conversation = (conversationId != null)
                ? conversationService.findOwned(conversationId, userId)
                : null;
        if (conversation == null) {
            conversation = conversationService.create(userId);
        }
        Long convId = conversation.getId();

        List<AiMessageEntity> history = conversationService.getRecentMessages(convId, userId);
        conversationService.appendMessage(convId, userId, "user", message, null);

        List<SemanticSearchService.SearchResult> retrieved = List.of();
        String retrievedContext = "";
        try {
            float[] queryEmbedding = queryEmbeddingCache.getOrCompute(convId, message, embeddingClient::embed);
            retrieved = semanticSearchService.search(userId, queryEmbedding, RETRIEVAL_LIMIT);
            retrievedContext = retrievalContextBuilder.build(retrieved);
        } catch (Exception e) {
            log.warn("Semantic retrieval failed, continuing without retrieved context userId={} conversationId={} errorType={}",
                    userId, convId, e.getClass().getSimpleName());
        }

        List<Map<String, Object>> input = promptBuilder.buildInitialInput(history, message, retrievedContext);
        List<Map<String, Object>> tools = toolExecutor.getToolDefinitions();

        int toolCallCount = 0;
        JsonNode response;
        try {
            for (int iteration = 0; ; iteration++) {
                if (iteration >= MAX_TOOL_ITERATIONS || Duration.between(startedAt, Instant.now()).compareTo(MAX_LOOP_DURATION) > 0) {
                    log.warn("AI chat tool loop limit hit userId={} conversationId={} toolCalls={}", userId, convId, toolCallCount);
                    throw OpenAIException.toolLoopLimit();
                }

                response = openAIClient.createResponse(input, tools, model, settings.getMaxTokens(), settings.getTemperature());
                List<JsonNode> functionCalls = extractFunctionCalls(response);
                if (functionCalls.isEmpty()) {
                    break;
                }

                for (JsonNode call : functionCalls) {
                    toolCallCount++;
                    String toolName = textOf(call, "name");
                    String callId = textOf(call, "call_id");
                    String arguments = textOf(call, "arguments");

                    Map<String, Object> result = toolExecutor.execute(toolName, arguments, userId);
                    String resultJson = writeJson(result);
                    conversationService.appendMessage(convId, userId, "tool", resultJson, toolName);

                    input.add(objectMapper.convertValue(call, new TypeReference<Map<String, Object>>() {}));
                    input.add(functionCallOutputItem(callId, resultJson));
                }
            }

            String assistantText = extractAssistantText(response);
            if (assistantText == null || assistantText.isBlank()) {
                throw OpenAIException.emptyResponse();
            }

            conversationService.appendMessage(convId, userId, "assistant", assistantText, null);

            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            Map<String, Integer> usage = extractUsage(response);
            log.info("AI chat completed userId={} conversationId={} durationMs={} toolCalls={} promptTokens={} completionTokens={} totalTokens={} model={} retrievedDocs={}",
                    userId, convId, durationMs, toolCallCount,
                    usage.get("input"), usage.get("output"), usage.get("total"), model, retrieved.size());

            Map<String, Object> debug = Boolean.TRUE.equals(settings.getDeveloperMode())
                    ? buildDebugInfo(retrieved, toolCallCount, durationMs, usage)
                    : null;
            return new ChatResult(convId, assistantText, debug);
        } catch (OpenAIException e) {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.warn("AI chat failed userId={} conversationId={} durationMs={} errorType={} model={}",
                    userId, convId, durationMs, e.getErrorTag(), model);
            throw e;
        }
    }

    /** Only called when the user has Developer Mode enabled — never persisted, only returned on this response. */
    private Map<String, Object> buildDebugInfo(List<SemanticSearchService.SearchResult> retrieved, int toolCallCount,
                                                long durationMs, Map<String, Integer> usage) {
        List<Map<String, Object>> retrievedDocuments = retrieved.stream().map(r -> {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("entityType", r.entityType());
            doc.put("title", r.title() != null ? r.title() : "");
            doc.put("score", r.score());
            return doc;
        }).collect(Collectors.toList());

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("embeddingProvider", embeddingClient.getClass().getSimpleName());
        debug.put("retrievedDocuments", retrievedDocuments);
        debug.put("documentCount", retrieved.size());
        debug.put("toolCalls", toolCallCount);
        debug.put("executionTimeMs", durationMs);
        debug.put("promptTokens", usage.get("input"));
        debug.put("completionTokens", usage.get("output"));
        debug.put("totalTokens", usage.get("total"));
        return debug;
    }

    private void enforceUserRateLimit(Long userId) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = userRequestTimestamps.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && Duration.between(timestamps.peekFirst(), now).toSeconds() > 60) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= USER_RATE_LIMIT_PER_MINUTE) {
                throw OpenAIException.tooManyRequestsFromUser();
            }
            timestamps.addLast(now);
        }
    }

    private String sanitize(String rawMessage) {
        if (rawMessage == null) return "";
        String stripped = Jsoup.parse(rawMessage).text().trim();
        return stripped.length() > MAX_MESSAGE_LENGTH ? stripped.substring(0, MAX_MESSAGE_LENGTH) : stripped;
    }

    private Map<String, Object> functionCallOutputItem(String callId, String outputJson) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call_output");
        item.put("call_id", callId);
        item.put("output", outputJson);
        return item;
    }

    private List<JsonNode> extractFunctionCalls(JsonNode response) {
        List<JsonNode> calls = new ArrayList<>();
        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                if ("function_call".equals(textOf(item, "type"))) {
                    calls.add(item);
                }
            }
        }
        return calls;
    }

    private String extractAssistantText(JsonNode response) {
        JsonNode output = response.get("output");
        if (output == null || !output.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : output) {
            if (!"message".equals(textOf(item, "type"))) continue;
            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) continue;
            for (JsonNode part : content) {
                String partType = textOf(part, "type");
                if ("output_text".equals(partType) || "text".equals(partType)) {
                    String text = textOf(part, "text");
                    if (text != null) sb.append(text);
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private Map<String, Integer> extractUsage(JsonNode response) {
        Map<String, Integer> usage = new LinkedHashMap<>();
        JsonNode usageNode = response.get("usage");
        usage.put("input", intOrNull(usageNode, "input_tokens"));
        usage.put("output", intOrNull(usageNode, "output_tokens"));
        usage.put("total", intOrNull(usageNode, "total_tokens"));
        return usage;
    }

    private Integer intOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || !value.canConvertToInt()) ? null : value.asInt();
    }

    private String textOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText(null);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
