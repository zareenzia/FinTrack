package org.example.finzin.ai;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Minimal per-key TTL cache for expensive-but-tolerably-stale computations (financial health,
 * dashboard summary) — same spirit as {@link QueryEmbeddingCache} but time-based rather than
 * conversation-scoped. Not a Spring bean: each owning service holds its own instance, since the
 * cached value type differs per service and there's no cross-service sharing need.
 */
public class TtlCache<K, V> {
    private final long ttlMillis;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TtlCache(Duration ttl) {
        this.ttlMillis = ttl.toMillis();
    }

    public V getOrCompute(K key, Supplier<V> compute) {
        long now = System.currentTimeMillis();
        Entry<V> existing = entries.get(key);
        if (existing != null && (now - existing.timestamp) < ttlMillis) {
            return existing.value;
        }
        V value = compute.get();
        entries.put(key, new Entry<>(value, now));
        return value;
    }

    private record Entry<V>(V value, long timestamp) {}
}
