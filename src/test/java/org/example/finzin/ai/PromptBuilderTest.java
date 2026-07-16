package org.example.finzin.ai;

import org.example.finzin.entity.AiMessageEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backs the Part 8 fallback guarantee: whenever retrieval finds nothing (or fails), the assembled
 * prompt must be identical to what it was before Phase 2B, so chat behavior never changes for
 * users with no indexed data or during a retrieval outage.
 */
class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void blankRetrievedContextProducesNoExtraItem() {
        List<AiMessageEntity> history = List.of();
        List<Map<String, Object>> withEmpty = promptBuilder.buildInitialInput(history, "How much did I spend?", "");
        List<Map<String, Object>> withNull = promptBuilder.buildInitialInput(history, "How much did I spend?", null);
        List<Map<String, Object>> withBlank = promptBuilder.buildInitialInput(history, "How much did I spend?", "   ");

        assertEquals(2, withEmpty.size(), "system prompt + user message only");
        assertEquals(2, withNull.size());
        assertEquals(2, withBlank.size());
        assertEquals(withEmpty, withNull);
        assertEquals("system", withEmpty.get(0).get("role"));
        assertEquals("user", withEmpty.get(1).get("role"));
        assertEquals("How much did I spend?", withEmpty.get(1).get("content"));
    }

    @Test
    void nonBlankRetrievedContextInsertsExtraSystemItemBeforeUserMessage() {
        List<AiMessageEntity> history = List.of();
        List<Map<String, Object>> input = promptBuilder.buildInitialInput(
                history, "How much did I spend?", "Entity Type: NOTE\nTitle: Groceries");

        assertEquals(3, input.size());
        assertEquals("system", input.get(0).get("role"));
        assertEquals("system", input.get(1).get("role"));
        assertTrue(((String) input.get(1).get("content")).contains("Groceries"));
        assertEquals("user", input.get(2).get("role"));
        assertEquals("How much did I spend?", input.get(2).get("content"));
    }

    @Test
    void historyIsReplayedBeforeRetrievalItemAndToolMessagesAreSkipped() {
        AiMessageEntity userMsg = new AiMessageEntity();
        userMsg.setRole("user");
        userMsg.setContent("previous question");
        AiMessageEntity assistantMsg = new AiMessageEntity();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("previous answer");
        AiMessageEntity toolMsg = new AiMessageEntity();
        toolMsg.setRole("tool");
        toolMsg.setContent("{}");

        List<AiMessageEntity> history = List.of(userMsg, assistantMsg, toolMsg);
        List<Map<String, Object>> input = promptBuilder.buildInitialInput(history, "new question", "some context");

        // system, user(prev), assistant(prev), retrieval-system, user(new) — tool role never replayed
        assertEquals(5, input.size());
        assertEquals("previous question", input.get(1).get("content"));
        assertEquals("previous answer", input.get(2).get("content"));
        assertEquals("system", input.get(3).get("role"));
        assertEquals("new question", input.get(4).get("content"));
    }
}
