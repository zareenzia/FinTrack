package org.example.finzin.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain-Mockito-free unit test (no collaborators to mock) for the minimal per-key TTL cache used
 * by {@code FinancialHealthService} and {@code DashboardSummaryService}. Covers the two behaviors
 * those callers actually depend on: a fresh value is only computed once within the TTL window,
 * and a new value is recomputed once the TTL has elapsed — plus that different keys never share
 * an entry (a per-user cache accidentally leaking another user's cached value would be a
 * correctness bug, not just a performance one).
 */
class TtlCacheTest {

    @Test
    void secondCallWithinTtlReturnsTheCachedValueWithoutRecomputing() {
        TtlCache<Long, String> cache = new TtlCache<>(Duration.ofMinutes(10));
        AtomicInteger computeCount = new AtomicInteger(0);

        String first = cache.getOrCompute(1L, () -> "value-" + computeCount.incrementAndGet());
        String second = cache.getOrCompute(1L, () -> "value-" + computeCount.incrementAndGet());

        assertEquals("value-1", first);
        assertEquals("value-1", second, "must return the cached value, not recompute");
        assertEquals(1, computeCount.get());
    }

    @Test
    void entryExpiresAfterTtlElapsesAndIsRecomputed() throws InterruptedException {
        TtlCache<Long, String> cache = new TtlCache<>(Duration.ofMillis(20));
        AtomicInteger computeCount = new AtomicInteger(0);

        String first = cache.getOrCompute(1L, () -> "value-" + computeCount.incrementAndGet());
        Thread.sleep(60);
        String second = cache.getOrCompute(1L, () -> "value-" + computeCount.incrementAndGet());

        assertEquals("value-1", first);
        assertEquals("value-2", second, "must recompute once the TTL window has elapsed");
        assertEquals(2, computeCount.get());
    }

    @Test
    void differentKeysAreCachedIndependently() {
        TtlCache<Long, String> cache = new TtlCache<>(Duration.ofMinutes(10));

        String forUserOne = cache.getOrCompute(1L, () -> "user-1-data");
        String forUserTwo = cache.getOrCompute(2L, () -> "user-2-data");
        String forUserOneAgain = cache.getOrCompute(1L, () -> "SHOULD_NOT_BE_RETURNED");

        assertEquals("user-1-data", forUserOne);
        assertEquals("user-2-data", forUserTwo);
        assertEquals("user-1-data", forUserOneAgain, "user 1's cached entry must never be affected by user 2's lookups");
    }
}
