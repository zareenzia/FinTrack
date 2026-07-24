package org.example.finzin.voice;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for which fields are required per {@link VoiceIntent}, whether a field
 * counts as "present" in a fields map, and what to ask when it's missing — shared by every
 * {@link IntentParser} tier and {@link VoiceService} so this vocabulary never drifts between them.
 *
 * "Required" here means "the user must have said something about it at all" (triggers a spoken
 * follow-up) — it is deliberately unrelated to whether a category/account phrase could be
 * confidently resolved against the user's real data (that's a {@link VoiceService} concern,
 * surfaced as a blank dropdown on the confirmation screen instead of another spoken question).
 */
final class VoiceFieldRules {
    private VoiceFieldRules() {
    }

    static List<String> requiredFieldsFor(VoiceIntent intent) {
        return switch (intent) {
            case EXPENSE, INCOME, SAVINGS -> List.of("amount", "category");
            case TRANSFER -> List.of("amount", "account");
            case NOTE -> List.of("noteContent");
            case TODO -> List.of("todoTitle");
            default -> List.of();
        };
    }

    static boolean isFieldPresent(String label, Map<String, Object> fields) {
        return switch (label) {
            case "amount" -> fields.get("amount") != null;
            case "category" -> fields.get("categoryPhrase") != null || fields.get("categoryId") != null;
            case "account" -> fields.get("accountPhrase") != null || fields.get("accountId") != null
                    || fields.get("sourceAccountPhrase") != null || fields.get("destinationAccountPhrase") != null
                    || fields.get("sourceAccountId") != null || fields.get("destinationAccountId") != null;
            case "noteContent" -> fields.get("noteContent") != null;
            case "todoTitle" -> fields.get("todoTitle") != null;
            default -> true;
        };
    }

    static String followUpQuestionFor(VoiceIntent intent, String missingField) {
        return switch (missingField) {
            case "amount" -> "How much was it?";
            case "category" -> "What category was that?";
            case "account" -> intent == VoiceIntent.TRANSFER ? "Which accounts — from and to?" : "Which account did you use?";
            case "noteContent" -> "What should the note say?";
            case "todoTitle" -> "What's the to-do?";
            default -> "Can you tell me more?";
        };
    }
}
