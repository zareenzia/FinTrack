package org.example.finzin.voice;

import java.util.List;
import java.util.Map;

/**
 * Opaque state the frontend round-trips on each follow-up turn of a multi-turn voice command —
 * there is no server-side conversation/session table for this (deliberately not reusing
 * {@code ai_conversations}/{@code ai_messages}: that's a chat-transcript model, this is
 * structured draft-field data, and conflating them would be a mistake, not a simplification).
 *
 * When {@code missingRequiredFields} has exactly one entry and {@code turnCount > 0}, both parser
 * tiers treat the entire follow-up transcript as the answer to that one field rather than
 * re-running full intent classification on a short fragment (e.g. "five hundred", "bKash") —
 * a 1-2 word utterance in isolation is too fragile to reclassify safely.
 */
public record VoicePriorState(
        String lockedIntent,
        Map<String, Object> draftFields,
        List<String> missingRequiredFields,
        String lastFollowUpQuestion,
        Integer turnCount
) {
    public static final int MAX_FOLLOW_UP_TURNS = 3;

    public static VoicePriorState initial() {
        return new VoicePriorState(null, Map.of(), List.of(), null, 0);
    }

    public boolean isFollowUp() {
        return lockedIntent != null && turnCount != null && turnCount > 0;
    }

    public boolean isSingleFieldFollowUp() {
        return isFollowUp() && missingRequiredFields != null && missingRequiredFields.size() == 1;
    }

    public boolean hasExhaustedFollowUps() {
        return turnCount != null && turnCount >= MAX_FOLLOW_UP_TURNS;
    }
}
