package org.example.finzin.ai;

import org.example.finzin.ai.rag.SemanticSearchService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Formats semantic-search results into a plain-text block for injection into the chat prompt.
 * Pure formatting only — the top-N limit is enforced by the caller's {@code search(..., limit)}
 * call, not here, so there is exactly one place that decision lives.
 */
@Component
public class RetrievalContextBuilder {

    public String build(List<SemanticSearchService.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        return results.stream().map(this::formatOne).collect(Collectors.joining("\n\n"));
    }

    private String formatOne(SemanticSearchService.SearchResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Entity Type: ").append(r.entityType()).append('\n');
        sb.append("Title: ").append(r.title() != null ? r.title() : "").append('\n');
        sb.append("Content: ").append(r.content() != null ? r.content() : "").append('\n');
        if (r.metadata() != null && !r.metadata().isBlank()) {
            sb.append("Metadata: ").append(r.metadata()).append('\n');
        }
        sb.append("Similarity Score: ").append(String.format("%.3f", r.score()));
        return sb.toString();
    }
}
