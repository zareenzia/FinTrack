package org.example.finzin.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Caches query embeddings per conversation so asking the same (or a repeated) question within one
 * conversation doesn't re-call the embedding provider. Bounded at two levels — entries per
 * conversation and number of tracked conversations — since most conversations are abandoned
 * rather than explicitly deleted, so {@link #evictConversation(Long)} alone wouldn't cap memory.
 */
@Component
public class QueryEmbeddingCache {
    private static final int MAX_ENTRIES_PER_CONVERSATION = 20;
    private static final int MAX_TRACKED_CONVERSATIONS = 300;

    private final LinkedHashMap<Long, LinkedHashMap<String, float[]>> byConversation =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, LinkedHashMap<String, float[]>> eldest) {
                    return size() > MAX_TRACKED_CONVERSATIONS;
                }
            };

    public float[] getOrCompute(Long conversationId, String query, Function<String, float[]> compute) {
        synchronized (byConversation) {
            LinkedHashMap<String, float[]> perConversation = byConversation.computeIfAbsent(conversationId, id -> newBoundedMap());
            float[] cached = perConversation.get(query);
            if (cached != null) {
                return cached;
            }
            float[] computed = compute.apply(query);
            perConversation.put(query, computed);
            return computed;
        }
    }

    public void evictConversation(Long conversationId) {
        synchronized (byConversation) {
            byConversation.remove(conversationId);
        }
    }

    private LinkedHashMap<String, float[]> newBoundedMap() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > MAX_ENTRIES_PER_CONVERSATION;
            }
        };
    }
}
