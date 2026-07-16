package org.example.finzin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around OpenAI's Responses API (https://api.openai.com/v1/responses).
 * Builds/parses requests, maps HTTP failures to {@link OpenAIException}. Contains no financial
 * or business logic — that lives in {@link org.example.finzin.ai.AIService}.
 */
@Component
public class OpenAIClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;

    public OpenAIClient(ObjectMapper objectMapper,
                         @Value("${openai.api.key:}") String apiKey,
                         @Value("${openai.api.base-url:https://api.openai.com/v1}") String baseUrl,
                         @Value("${openai.api.timeout-seconds:60}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory((ClientHttpRequestFactory) requestFactory)
                .build();
    }

    @PostConstruct
    void checkConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY is not set — AI Assistant requests will fail until it is configured.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Calls POST /responses. Retries once without "temperature" if the model rejects it
     * (some reasoning-tier models only accept a fixed sampling temperature).
     */
    public JsonNode createResponse(List<Map<String, Object>> input, List<Map<String, Object>> tools,
                                    String model, Integer maxOutputTokens, Double temperature) {
        if (!isConfigured()) {
            throw OpenAIException.notConfigured();
        }
        Map<String, Object> body = buildBody(input, tools, model, maxOutputTokens, temperature);
        try {
            return call(body);
        } catch (OpenAIException e) {
            if ("UPSTREAM_ERROR".equals(e.getErrorTag()) && temperature != null) {
                log.warn("OpenAI rejected a request parameter (likely temperature) for model={}, retrying without it", model);
                Map<String, Object> retryBody = buildBody(input, tools, model, maxOutputTokens, null);
                return call(retryBody);
            }
            throw e;
        }
    }

    private Map<String, Object> buildBody(List<Map<String, Object>> input, List<Map<String, Object>> tools,
                                           String model, Integer maxOutputTokens, Double temperature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        if (maxOutputTokens != null) {
            body.put("max_output_tokens", maxOutputTokens);
        }
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        body.put("store", false);
        return body;
    }

    private JsonNode call(Map<String, Object> body) {
        try {
            String responseJson = restClient.post()
                    .uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (req, res) -> { throw OpenAIException.rateLimit(); })
                    .onStatus(status -> status.value() == 401 || status.value() == 403, (req, res) -> { throw OpenAIException.authError(); })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { throw OpenAIException.upstreamError(); })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> { throw OpenAIException.upstreamError(); })
                    .body(String.class);
            if (responseJson == null || responseJson.isBlank()) {
                throw OpenAIException.emptyResponse();
            }
            return objectMapper.readTree(responseJson);
        } catch (OpenAIException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw OpenAIException.timeout();
        } catch (Exception e) {
            throw OpenAIException.malformedResponse();
        }
    }
}
