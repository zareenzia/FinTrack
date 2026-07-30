package org.example.finzin.voice;

import java.util.List;
import java.util.Map;

/**
 * Response body of {@code POST /api/voice/parse}. This never triggers a save by itself — the
 * frontend always shows a confirmation screen (or, for a single missing field, asks a spoken
 * follow-up) before calling the real {@code /api/transactions|notes|todos} endpoint itself.
 *
 * @param isComplete   true once every required field for {@code intent} is filled (resolved or not
 *                     — an unresolved category/account still counts as "complete" here; it just
 *                     shows blank on the confirmation screen for the user to pick manually).
 * @param giveUp       true once {@link VoicePriorState#MAX_FOLLOW_UP_TURNS} follow-up turns have
 *                     been used without completing — the frontend must stop asking and fall back
 *                     to a manual confirmation form for whatever remains missing.
 * @param priorState   opaque state to send back verbatim (with the next spoken answer appended)
 *                     on the next {@code /parse} call if this turn isn't complete yet.
 */
public record VoiceResultDTO(
        Long historyId,
        String intent,
        Map<String, Object> fields,
        List<String> missingRequiredFields,
        String followUpQuestion,
        double confidence,
        String source,
        boolean isComplete,
        boolean giveUp,
        VoicePriorState priorState
) {
}
