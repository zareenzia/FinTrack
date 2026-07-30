package org.example.finzin.ai.rag;

/** Thrown by {@link EmbeddingClient} implementations. Always caught by callers — an embedding
 *  failure must never break the user-facing operation that triggered indexing. */
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }
}
