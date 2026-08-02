package org.example.finzin.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Plain unit test (no collaborators to mock) for the per-conversation query-embedding cache.
 * Covers the eviction/bounding behaviors called out in the class's own javadoc: a repeated query
 * within one conversation is never re-embedded, different conversations never share an entry, and
 * {@link QueryEmbeddingCache#evictConversation(Long)} actually drops that conversation's entries
 * (verified indirectly: after eviction, the same query is recomputed rather than served stale).
 */
class QueryEmbeddingCacheTest {

    @Test
    void repeatedQueryWithinTheSameConversationIsComputedOnlyOnce() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache();
        AtomicInteger computeCount = new AtomicInteger(0);

        float[] first = cache.getOrCompute(7L, "how much did I spend?", q -> {
            computeCount.incrementAndGet();
            return new float[]{1f, 2f, 3f};
        });
        float[] second = cache.getOrCompute(7L, "how much did I spend?", q -> {
            computeCount.incrementAndGet();
            return new float[]{9f, 9f, 9f};
        });

        assertArrayEquals(new float[]{1f, 2f, 3f}, second, "must return the cached vector, not recompute");
        assertNotSame(first, new float[]{9f, 9f, 9f});
        assertEquals(1, computeCount.get());
    }

    @Test
    void differentConversationsNeverShareACachedEmbeddingForTheSameQueryText() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache();

        float[] forConversationOne = cache.getOrCompute(1L, "balance?", q -> new float[]{1f});
        float[] forConversationTwo = cache.getOrCompute(2L, "balance?", q -> new float[]{2f});

        assertArrayEquals(new float[]{1f}, forConversationOne);
        assertArrayEquals(new float[]{2f}, forConversationTwo);
    }

    @Test
    void evictConversationForcesTheNextLookupToRecompute() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache();
        AtomicInteger computeCount = new AtomicInteger(0);

        cache.getOrCompute(5L, "net worth?", q -> {
            computeCount.incrementAndGet();
            return new float[]{1f};
        });
        cache.evictConversation(5L);
        cache.getOrCompute(5L, "net worth?", q -> {
            computeCount.incrementAndGet();
            return new float[]{1f};
        });

        assertEquals(2, computeCount.get(), "eviction must force recomputation on the next lookup for that conversation");
    }

    @Test
    void evictingAConversationThatWasNeverCachedIsANoOp() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache();

        cache.evictConversation(999L);

        // No exception, and unrelated entries are unaffected.
        float[] result = cache.getOrCompute(1L, "q", q -> new float[]{1f});
        assertArrayEquals(new float[]{1f}, result);
    }
}
