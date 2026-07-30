package org.example.finzin.receipts.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.ai.OpenAIClient;
import org.example.finzin.ai.OpenAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds raw OCR text (not the image) through the existing {@link OpenAIClient} in text-only mode
 * and asks for a strict JSON object back. This is the primary extractor; {@code ReceiptService}
 * never calls it directly — see {@link CompositeReceiptFieldExtractor}.
 */
@Component
public class AiReceiptFieldExtractor implements ReceiptFieldExtractor {
    private static final Logger log = LoggerFactory.getLogger(AiReceiptFieldExtractor.class);

    private static final String MODEL = "gpt-5";
    private static final int MAX_OUTPUT_TOKENS = 700;
    private static final double TEMPERATURE = 0.2;

    private static final String PROMPT_TEMPLATE = """
            You are a receipt-parsing assistant. Below is raw OCR text extracted from a photographed \
            receipt — it may contain misread characters, missing spaces, or garbled lines.

            Extract the fields you can confidently find and respond with ONLY a single raw JSON object \
            (no markdown code fences, no explanation) matching exactly this shape:
            {
              "merchantName": string or null,
              "receiptNumber": string or null,
              "invoiceNumber": string or null,
              "receiptDate": "YYYY-MM-DD" or null,
              "receiptTime": string or null,
              "currency": string or null,
              "totalAmount": number or null,
              "taxAmount": number or null,
              "discountAmount": number or null,
              "subtotalAmount": number or null,
              "paymentMethod": string or null,
              "items": [{"name": string, "quantity": number or null, "unitPrice": number or null, "totalPrice": number or null}],
              "predictedCategoryLabel": string or null (a short everyday spending category guess, e.g. "Groceries", "Dining Out", "Transportation"),
              "suggestedNotes": string or null (a short one-line note about the purchase),
              "confidenceScore": number from 0 to 1 (your overall confidence in this extraction),
              "fieldConfidence": {"merchantName": number 0-1, "totalAmount": number 0-1, "receiptDate": number 0-1, "predictedCategoryLabel": number 0-1}
            }

            If a field cannot be determined, use null rather than guessing wildly, and give it a low fieldConfidence.

            OCR text:
            ---
            %s
            ---
            """;

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    public AiReceiptFieldExtractor(OpenAIClient openAIClient, ObjectMapper objectMapper) {
        this.openAIClient = openAIClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReceiptFieldExtractionResult extract(String ocrText) {
        if (!openAIClient.isConfigured()) {
            throw OpenAIException.notConfigured();
        }
        List<Map<String, Object>> input = List.of(inputItem("user", PROMPT_TEMPLATE.formatted(ocrText)));
        JsonNode response = openAIClient.createResponse(input, null, MODEL, MAX_OUTPUT_TOKENS, TEMPERATURE);
        String text = extractOutputText(response);
        if (text == null || text.isBlank()) {
            throw OpenAIException.emptyResponse();
        }
        JsonNode json = parseJson(text);
        return toResult(json);
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
            log.warn("Failed to parse AI receipt extraction response as JSON: {}", e.getMessage());
            throw OpenAIException.malformedResponse();
        }
    }

    private ReceiptFieldExtractionResult toResult(JsonNode json) {
        List<ReceiptLineItem> items = new ArrayList<>();
        JsonNode itemsNode = json.get("items");
        if (itemsNode != null && itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                items.add(new ReceiptLineItem(
                        textOf(item, "name"),
                        numberOf(item, "quantity"),
                        numberOf(item, "unitPrice"),
                        numberOf(item, "totalPrice")
                ));
            }
        }

        Map<String, Double> fieldConfidence = new LinkedHashMap<>();
        JsonNode fc = json.get("fieldConfidence");
        if (fc != null && fc.isObject()) {
            fc.fieldNames().forEachRemaining(key -> {
                Double value = numberOf(fc, key);
                if (value != null) fieldConfidence.put(key, value);
            });
        }

        return new ReceiptFieldExtractionResult(
                textOf(json, "merchantName"),
                textOf(json, "receiptNumber"),
                textOf(json, "invoiceNumber"),
                parseDate(textOf(json, "receiptDate")),
                textOf(json, "receiptTime"),
                textOf(json, "currency"),
                numberOf(json, "totalAmount"),
                numberOf(json, "taxAmount"),
                numberOf(json, "discountAmount"),
                numberOf(json, "subtotalAmount"),
                textOf(json, "paymentMethod"),
                items,
                textOf(json, "predictedCategoryLabel"),
                textOf(json, "suggestedNotes"),
                numberOf(json, "confidenceScore"),
                fieldConfidence,
                "AI",
                null
        );
    }

    private Double numberOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || value.isNull() || !value.isNumber()) ? null : value.asDouble();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
