package org.example.finzin.ai;

import org.example.finzin.ai.rag.SemanticSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalContextBuilderTest {

    private final RetrievalContextBuilder builder = new RetrievalContextBuilder();

    @Test
    void emptyOrNullListProducesEmptyString() {
        assertEquals("", builder.build(List.of()));
        assertEquals("", builder.build(null));
    }

    @Test
    void formatsEachDocumentWithKeyFields() {
        var result = new SemanticSearchService.SearchResult(
                "TRANSACTION", 1L, "Grocery run", "Bought food", "{\"amount\":45.5}", 0.812345);
        String formatted = builder.build(List.of(result));

        assertTrue(formatted.contains("Entity Type: TRANSACTION"));
        assertTrue(formatted.contains("Title: Grocery run"));
        assertTrue(formatted.contains("Content: Bought food"));
        assertTrue(formatted.contains("Metadata: {\"amount\":45.5}"));
        assertTrue(formatted.contains("Similarity Score: 0.812"));
    }

    @Test
    void multipleDocumentsAppearInOrder() {
        var a = new SemanticSearchService.SearchResult("NOTE", 1L, "A", "content-a", null, 0.5);
        var b = new SemanticSearchService.SearchResult("TODO", 2L, "B", "content-b", null, 0.4);
        String formatted = builder.build(List.of(a, b));

        assertTrue(formatted.contains("content-a"));
        assertTrue(formatted.contains("content-b"));
        assertTrue(formatted.indexOf("content-a") < formatted.indexOf("content-b"));
    }

    @Test
    void nullTitleContentAndMetadataDoNotThrow() {
        var result = new SemanticSearchService.SearchResult("NOTE", 1L, null, null, null, 0.1);
        assertDoesNotThrow(() -> builder.build(List.of(result)));
    }
}
