package org.example.finzin.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.ai.OpenAIClient;
import org.example.finzin.ai.OpenAIException;
import org.example.finzin.entity.AiSettingsEntity;
import org.example.finzin.repository.AiSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds the speech transcript through the existing {@link OpenAIClient} in text-only mode and
 * asks for a strict JSON object back — same call shape as {@code AiReceiptFieldExtractor}.
 * {@code VoiceService} never calls this directly; see {@link CompositeIntentParser}.
 *
 * Respects the user's existing "AI Assistant enabled" toggle ({@link AiSettingsEntity#getEnabled()})
 * in addition to whether OpenAI is configured at all — a user who disables AI chat to conserve
 * quota also gets heuristic-only voice parsing, rather than a second, inconsistent on/off switch.
 */
@Component
public class AiIntentParser implements IntentParser {
    private static final Logger log = LoggerFactory.getLogger(AiIntentParser.class);

    private static final String MODEL = "gpt-5";
    private static final int MAX_OUTPUT_TOKENS = 500;
    private static final double TEMPERATURE = 0.2;

    private static final String PROMPT_TEMPLATE = """
            You are an intent classifier and field extractor for a personal finance voice assistant. \
            The user spoke the transcript below. Determine which ONE intent applies: EXPENSE, INCOME, \
            SAVINGS, TRANSFER, NOTE, TODO, QUERY, or UNKNOWN.

            QUERY means the user is asking a question about their finances (e.g. "how much did I spend \
            this month") rather than issuing a command — do not extract fields for QUERY.
            UNKNOWN means you genuinely cannot tell what the user wants.

            IMPORTANT: never invent or guess a specific category name or account name. Only echo back \
            the raw phrase the user actually said (categoryPhrase, accountPhrase, sourceAccountPhrase, \
            destinationAccountPhrase) — a separate system will match those phrases against the user's \
            real categories/accounts, or leave them blank for the user to pick if there's no confident match.

            Respond with ONLY a single raw JSON object (no markdown fences, no explanation) matching \
            exactly this shape:
            {
              "intent": "EXPENSE|INCOME|SAVINGS|TRANSFER|NOTE|TODO|QUERY|UNKNOWN",
              "confidence": number from 0 to 1,
              "fields": {
                "amount": number or null,
                "currency": string or null,
                "date": "YYYY-MM-DD" or null (resolve relative dates like \"tomorrow evening\" yourself),
                "description": string or null (merchant or short description),
                "categoryPhrase": string or null (raw phrase only, never resolved),
                "accountPhrase": string or null (raw phrase only, for expense/income/savings),
                "sourceAccountPhrase": string or null (for TRANSFER only),
                "destinationAccountPhrase": string or null (for TRANSFER only),
                "noteTitle": string or null (only for NOTE — a short 3-6 word title you generate from the content),
                "noteContent": string or null (only for NOTE — the full spoken content),
                "todoTitle": string or null (only for TODO),
                "todoDueDate": "YYYY-MM-DD" or null (only for TODO),
                "todoPriority": "low"|"medium"|"high" or null (only for TODO)
              },
              "missingRequiredFields": [string, ...] (field names the user said nothing about at all — \
              e.g. "amount", "category", "account", "noteContent", "todoTitle" — never include a field \
              here just because you couldn't match it to something real, only if it was never mentioned),
              "followUpQuestion": string or null (one short question to ask next if something required \
              is missing, else null)
            }

            Prior conversation state (may be empty if this is the first turn): %s

            Transcript:
            ---
            %s
            ---
            """;

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;
    private final AiSettingsRepository aiSettingsRepository;

    public AiIntentParser(OpenAIClient openAIClient, ObjectMapper objectMapper, AiSettingsRepository aiSettingsRepository) {
        this.openAIClient = openAIClient;
        this.objectMapper = objectMapper;
        this.aiSettingsRepository = aiSettingsRepository;
    }

    @Override
    public ParsedVoiceCommand parse(Long userId, String transcript, VoicePriorState priorState) {
        if (!openAIClient.isConfigured()) {
            throw OpenAIException.notConfigured();
        }
        boolean userEnabledAi = aiSettingsRepository.findByUserId(userId)
                .map(AiSettingsEntity::getEnabled)
                .orElse(true);
        if (!userEnabledAi) {
            throw OpenAIException.disabled();
        }

        String priorStateJson = describePriorState(priorState);
        List<Map<String, Object>> input = List.of(inputItem("user", PROMPT_TEMPLATE.formatted(priorStateJson, transcript)));
        JsonNode response = openAIClient.createResponse(input, null, MODEL, MAX_OUTPUT_TOKENS, TEMPERATURE);
        String text = extractOutputText(response);
        if (text == null || text.isBlank()) {
            throw OpenAIException.emptyResponse();
        }
        JsonNode json = parseJson(text);
        return toResult(json);
    }

    private String describePriorState(VoicePriorState priorState) {
        if (priorState == null || !priorState.isFollowUp()) return "(none — this is the first turn)";
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("lockedIntent", priorState.lockedIntent());
            summary.put("draftFields", priorState.draftFields());
            summary.put("stillMissing", priorState.missingRequiredFields());
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "(none)";
        }
    }

    private Map<String, Object> inputItem(String role, String content) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private String extractOutputText(JsonNode response) {
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
                    String t = textOf(part, "text");
                    if (t != null) sb.append(t);
                }
            }
        }
        return sb.toString();
    }

    private String textOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText(null);
    }

    private JsonNode parseJson(String rawText) {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("Failed to parse AI voice intent response as JSON: {}", e.getMessage());
            throw OpenAIException.malformedResponse();
        }
    }

    private ParsedVoiceCommand toResult(JsonNode json) {
        VoiceIntent intent;
        try {
            intent = VoiceIntent.valueOf(textOf(json, "intent"));
        } catch (Exception e) {
            intent = VoiceIntent.UNKNOWN;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        JsonNode fieldsNode = json.get("fields");
        if (fieldsNode != null && fieldsNode.isObject()) {
            fieldsNode.fieldNames().forEachRemaining(key -> {
                JsonNode value = fieldsNode.get(key);
                if (value == null || value.isNull()) return;
                if (value.isNumber()) fields.put(key, value.asDouble());
                else fields.put(key, value.asText());
            });
        }

        List<String> missing = new ArrayList<>();
        JsonNode missingNode = json.get("missingRequiredFields");
        if (missingNode != null && missingNode.isArray()) {
            missingNode.forEach(n -> missing.add(n.asText()));
        }

        Double confidence = numberOf(json, "confidence");
        return new ParsedVoiceCommand(
                intent,
                confidence != null ? confidence : 0.7,
                "AI",
                fields,
                missing,
                textOf(json, "followUpQuestion")
        );
    }

    private Double numberOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || value.isNull() || !value.isNumber()) ? null : value.asDouble();
    }
}
