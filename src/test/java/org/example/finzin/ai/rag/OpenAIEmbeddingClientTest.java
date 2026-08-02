package org.example.finzin.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No Spring context and no Mockito: like {@code OpenAIClientTest}, this points {@link
 * OpenAIEmbeddingClient} at a real local {@code com.sun.net.httpserver.HttpServer} (JDK built-in)
 * instead of api.openai.com, since the class builds its own {@code RestClient} internally with no
 * injectable HTTP-client seam. Covers every mapped failure path documented on {@link
 * OpenAIEmbeddingClient#embed}: unconfigured API key, timeout, non-2xx status, empty body, and a
 * malformed/missing "data"/"embedding" shape.
 */
class OpenAIEmbeddingClientTest {

    private HttpServer server;
    private ExecutorService executor;
    private int port;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    private interface BodyHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private void startServer(BodyHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/embeddings", exchange -> {
            exchange.getRequestBody().readAllBytes();
            handler.handle(exchange);
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        port = server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private OpenAIEmbeddingClient client(String apiKey, int timeoutSeconds) {
        return new OpenAIEmbeddingClient(new ObjectMapper(), apiKey, "http://localhost:" + port, timeoutSeconds, "text-embedding-3-small");
    }

    @Test
    void throwsImmediatelyWithoutAnyNetworkCallWhenApiKeyIsBlank() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> { calls.incrementAndGet(); respond(exchange, 200, "{}"); });
        OpenAIEmbeddingClient unconfigured = client("", 5);

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> unconfigured.embed("hello"));

        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
        assertEquals(0, calls.get(), "must never reach the network when unconfigured");
    }

    @Test
    void successfulResponseIsParsedIntoAFloatArray() throws Exception {
        startServer(exchange -> respond(exchange, 200,
                "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}"));

        float[] vector = client("test-key", 5).embed("hello world");

        assertEquals(3, vector.length);
        assertEquals(0.1f, vector[0], 0.0001f);
        assertEquals(0.3f, vector[2], 0.0001f);
    }

    @Test
    void nonTwoHundredStatusMapsToAnEmbeddingExceptionWithTheStatusCode() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{\"error\":\"boom\"}"));

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> client("test-key", 5).embed("hello"));

        assertTrue(ex.getMessage().contains("500"), "message was: " + ex.getMessage());
    }

    @Test
    void emptyResponseBodyMapsToAnEmbeddingException() throws Exception {
        startServer(exchange -> respond(exchange, 200, ""));

        assertThrows(EmbeddingException.class, () -> client("test-key", 5).embed("hello"));
    }

    @Test
    void missingDataArrayMapsToAMalformedResponseException() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"nothing\":\"here\"}"));

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> client("test-key", 5).embed("hello"));

        assertTrue(ex.getMessage().toLowerCase().contains("malformed"));
    }

    @Test
    void emptyDataArrayMapsToAMalformedResponseException() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"data\":[]}"));

        assertThrows(EmbeddingException.class, () -> client("test-key", 5).embed("hello"));
    }

    @Test
    void missingEmbeddingFieldInsideDataMapsToAMalformedResponseException() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"data\":[{\"notEmbedding\":[1,2,3]}]}"));

        assertThrows(EmbeddingException.class, () -> client("test-key", 5).embed("hello"));
    }

    @Test
    void slowServerBeyondReadTimeoutMapsToATimeoutEmbeddingException() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"data\":[{\"embedding\":[0.1]}]}");
        });

        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> client("test-key", 1).embed("hello"));

        assertTrue(ex.getMessage().toLowerCase().contains("timed out"));
    }
}
