package org.example.finzin.voice;

import java.util.List;
import java.util.Map;

/**
 * Raw output of an {@link IntentParser} tier, before {@link VoiceService} resolves
 * category/account phrases against the user's real data and wraps it for the API response.
 *
 * @param source "AI" or "HEURISTIC" — which tier produced this result (surfaced to the UI so a
 *               heuristic-only parse can show a "please double-check" banner, mirroring the
 *               receipt-review screen's existing low-confidence disclaimer).
 * @param fields intent-specific raw fields (e.g. amount, categoryPhrase, accountPhrase,
 *               sourceAccountPhrase, destinationAccountPhrase, description, date, noteTitle,
 *               noteContent, todoTitle, todoDueDate, todoPriority) — phrases are the raw text
 *               heard, never a resolved id; resolution happens only in {@link VoiceService}.
 * @param missingRequiredFields fields the user said nothing about at all (triggers a spoken
 *                              follow-up) — distinct from a field that was mentioned but could
 *                              not be confidently resolved (that's a {@link VoiceService} concern,
 *                              surfaced as a blank dropdown on the confirmation screen instead).
 * @param followUpQuestion a single question to ask next, or null if nothing is missing/ambiguous.
 */
public record ParsedVoiceCommand(
        VoiceIntent intent,
        double confidence,
        String source,
        Map<String, Object> fields,
        List<String> missingRequiredFields,
        String followUpQuestion
) {
}
