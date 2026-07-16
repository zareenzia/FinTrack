package org.example.finzin.ai.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Free, deterministic {@link EmbeddingClient} for development without an OpenAI billing account.
 * Same input always hashes to the same 1536-dim vector; different inputs hash to different
 * vectors. The vectors carry no semantic meaning — only real embeddings can find "similar"
 * content — but this is enough to exercise the whole indexing/storage/retrieval pipeline
 * end-to-end for free. Activated via {@code ai.embedding.provider=mock}.
 */
@Component
@ConditionalOnProperty(name = "ai.embedding.provider", havingValue = "mock")
public class MockEmbeddingClient implements EmbeddingClient {
    private static final int DIMENSIONS = 1536;

    @Override
    public float[] embed(String text) {
        String safeText = text == null ? "" : text;
        long seed = seedFrom(safeText);
        Random random = new Random(seed);

        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = random.nextFloat() * 2f - 1f;
        }
        return vector;
    }

    private static long seedFrom(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash, 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException e) {
            return text.hashCode();
        }
    }
}
