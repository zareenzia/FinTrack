package org.example.finzin.ai.rag;

/** Provider-agnostic seam for turning text into an embedding vector. */
public interface EmbeddingClient {
    float[] embed(String text);
}
