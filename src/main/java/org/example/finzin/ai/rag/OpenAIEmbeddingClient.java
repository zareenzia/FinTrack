package org.example.finzin.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Thin wrapper around OpenAI's Embeddings API (https://api.openai.com/v1/embeddings). Active
 * whenever {@code ai.embedding.provider} is "openai" or unset — see {@link MockEmbeddingClient}
 * for the free, deterministic alternative used during development.
 */
@Component
@ConditionalOnProperty(name = "ai.embedding.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAIEmbeddingClient implements EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIEmbeddingClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAIEmbeddingClient(ObjectMapper objectMapper,
                                  @Value("${openai.api.key:}") String apiKey,
                                  @Value("${openai.api.base-url:https://api.openai.com/v1}") String baseUrl,
                                  @Value("${openai.api.timeout-seconds:60}") int timeoutSeconds,
                                  @Value("${openai.embedding.model:text-embedding-3-small}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory((ClientHttpRequestFactory) requestFactory)
                .build();
    }

    @Override
    public float[] embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmbeddingException("OPENAI_API_KEY is not configured");
        }
        try {
            String responseJson = restClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "input", text))
                    .retrieve()
                    .body(String.class);
            if (responseJson == null || responseJson.isBlank()) {
                throw new EmbeddingException("Empty embeddings response");
            }
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new EmbeddingException("Malformed embeddings response");
            }
            JsonNode vectorNode = data.get(0).get("embedding");
            if (vectorNode == null || !vectorNode.isArray()) {
                throw new EmbeddingException("Malformed embeddings response");
            }
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }
            return vector;
        } catch (EmbeddingException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new EmbeddingException("Embeddings request timed out");
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.warn("Embeddings call failed status={} body={}", e.getStatusCode().value(),
                    body.length() > 300 ? body.substring(0, 300) : body);
            throw new EmbeddingException("Embeddings call failed: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Embeddings call failed errorType={}", e.getClass().getSimpleName());
            throw new EmbeddingException("Embeddings call failed");
        }
    }
}
