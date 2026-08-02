package org.example.finzin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No Spring context and no Mockito: {@link OpenAIClient} builds its own {@code RestClient}
 * internally from constructor parameters (there is no injectable HTTP-client seam), so this test
 * points the client's {@code baseUrl} at a real, local {@code com.sun.net.httpserver.HttpServer}
 * (JDK built-in, no extra test dependency) that returns scripted status codes/bodies. This
 * exercises the real request-building and HTTP-status-to-{@link OpenAIException} mapping without
 * ever reaching api.openai.com.
 *
 * Covers every documented error-tag path (429 -&gt; RATE_LIMIT, 401/403 -&gt; AUTH_ERROR, other
 * 4xx/5xx -&gt; UPSTREAM_ERROR, empty body -&gt; EMPTY_RESPONSE, unparsable body -&gt; PARSE_ERROR,
 * a slow server -&gt; TIMEOUT) and the temperature-retry-on-error behavior described in
 * {@link OpenAIClient#createResponse}'s javadoc.
 */
class OpenAIClientTest {

    private HttpServer server;
    private ExecutorService executor;
    private int port;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    private record ScriptedResponse(int status, String body, long delayMs) {
        static ScriptedResponse of(int status, String body) { return new ScriptedResponse(status, body, 0); }
    }

    private static class ScriptedHandler implements HttpHandler {
        final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());
        final Deque<ScriptedResponse> script;

        ScriptedHandler(List<ScriptedResponse> responses) {
            this.script = new ArrayDeque<>(responses);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(body);
            ScriptedResponse response = script.isEmpty() ? ScriptedResponse.of(500, "{}") : script.poll();
            if (response.delayMs() > 0) {
                try {
                    Thread.sleep(response.delayMs());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.getResponseBody().close();
        }
    }

    private ScriptedHandler startServer(ScriptedResponse... responses) throws IOException {
        ScriptedHandler handler = new ScriptedHandler(new ArrayList<>(List.of(responses)));
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/responses", handler);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        port = server.getAddress().getPort();
        return handler;
    }

    private OpenAIClient client(int timeoutSeconds) {
        return new OpenAIClient(new ObjectMapper(), "test-api-key", "http://localhost:" + port, timeoutSeconds);
    }

    private static final String SUCCESS_BODY = """
            {"output":[{"type":"message","content":[{"type":"output_text","text":"Hello there."}]}],
             "usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15}}
            """;

    @Test
    void successfulCallReturnsTheParsedJsonResponse() throws Exception {
        startServer(ScriptedResponse.of(200, SUCCESS_BODY));

        JsonNode response = client(5).createResponse(List.of(Map.of("role", "user", "content", "hi")),
                List.of(), "gpt-5", 500, 0.3);

        assertEquals("Hello there.", response.get("output").get(0).get("content").get(0).get("text").asText());
    }

    @Test
    void rateLimitStatusMapsToRateLimitError() throws Exception {
        startServer(ScriptedResponse.of(429, "{}"));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("RATE_LIMIT", ex.getErrorTag());
        assertTrue(ex.isRetryable());
    }

    @Test
    void unauthorizedStatusMapsToAuthError() throws Exception {
        startServer(ScriptedResponse.of(401, "{}"));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("AUTH_ERROR", ex.getErrorTag());
        assertFalse(ex.isRetryable());
    }

    @Test
    void forbiddenStatusMapsToAuthError() throws Exception {
        startServer(ScriptedResponse.of(403, "{}"));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("AUTH_ERROR", ex.getErrorTag());
    }

    @Test
    void otherClientErrorStatusMapsToUpstreamError() throws Exception {
        startServer(ScriptedResponse.of(400, "{}"));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("UPSTREAM_ERROR", ex.getErrorTag());
        assertTrue(ex.isRetryable());
    }

    @Test
    void serverErrorStatusMapsToUpstreamError() throws Exception {
        startServer(ScriptedResponse.of(503, "{}"));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("UPSTREAM_ERROR", ex.getErrorTag());
    }

    @Test
    void emptyResponseBodyMapsToEmptyResponseError() throws Exception {
        startServer(ScriptedResponse.of(200, ""));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("EMPTY_RESPONSE", ex.getErrorTag());
    }

    @Test
    void malformedJsonBodyMapsToParseError() throws Exception {
        startServer(ScriptedResponse.of(200, "this is not json at all {"));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("PARSE_ERROR", ex.getErrorTag());
    }

    @Test
    void slowServerBeyondReadTimeoutMapsToTimeoutError() throws Exception {
        startServer(new ScriptedResponse(200, SUCCESS_BODY, 1500));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(1).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals("TIMEOUT", ex.getErrorTag());
        assertTrue(ex.isRetryable());
    }

    @Test
    void notConfiguredWhenApiKeyIsBlankNeverEvenReachesTheNetwork() throws Exception {
        startServer(ScriptedResponse.of(200, SUCCESS_BODY));
        OpenAIClient unconfigured = new OpenAIClient(new ObjectMapper(), "", "http://localhost:" + port, 5);

        assertFalse(unconfigured.isConfigured());
        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> unconfigured.createResponse(List.of(), List.of(), "gpt-5", 500, null));
        assertEquals("NOT_CONFIGURED", ex.getErrorTag());
    }

    @Test
    void isConfiguredIsTrueWhenApiKeyIsPresent() {
        assertTrue(new OpenAIClient(new ObjectMapper(), "sk-real-key", "https://api.openai.com/v1", 60).isConfigured());
    }

    // ── temperature-retry-on-error behavior ─────────────────────────────────────────────────

    @Test
    void retriesOnceWithoutTemperatureWhenTheModelRejectsTheRequestAndTemperatureWasSet() throws Exception {
        ScriptedHandler handler = startServer(ScriptedResponse.of(400, "{}"), ScriptedResponse.of(200, SUCCESS_BODY));

        JsonNode response = client(5).createResponse(List.of(), List.of(), "gpt-5", 500, 0.3);

        assertEquals("Hello there.", response.get("output").get(0).get("content").get(0).get("text").asText());
        assertEquals(2, handler.requestBodies.size(), "must retry exactly once after the first rejection");
        assertTrue(handler.requestBodies.get(0).contains("\"temperature\""), "the first attempt must include temperature");
        assertFalse(handler.requestBodies.get(1).contains("\"temperature\""), "the retry must omit temperature entirely");
    }

    @Test
    void doesNotRetryWhenTemperatureWasAlreadyNull() throws Exception {
        ScriptedHandler handler = startServer(ScriptedResponse.of(400, "{}"));

        assertThrows(OpenAIException.class, () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null));

        assertEquals(1, handler.requestBodies.size(), "no temperature was ever sent, so there is nothing to retry without");
    }

    @Test
    void doesNotRetryForNonUpstreamErrorsEvenWhenTemperatureWasSet() throws Exception {
        ScriptedHandler handler = startServer(ScriptedResponse.of(401, "{}"), ScriptedResponse.of(200, SUCCESS_BODY));

        OpenAIException ex = assertThrows(OpenAIException.class,
                () -> client(5).createResponse(List.of(), List.of(), "gpt-5", 500, 0.3));

        assertEquals("AUTH_ERROR", ex.getErrorTag());
        assertEquals(1, handler.requestBodies.size(), "auth errors are not retried regardless of temperature");
    }

    @Test
    void requestBodyOmitsToolsFieldEntirelyWhenNoToolsAreProvided() throws Exception {
        ScriptedHandler handler = startServer(ScriptedResponse.of(200, SUCCESS_BODY));

        client(5).createResponse(List.of(), List.of(), "gpt-5", 500, null);

        assertFalse(handler.requestBodies.get(0).contains("\"tools\""));
    }
}
