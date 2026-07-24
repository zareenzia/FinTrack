package org.example.finzin.voice;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure regex/keyword fallback used when the AI tier is unconfigured, disabled, or fails for any
 * reason — always succeeds (never throws), tagged {@code source="HEURISTIC"}, same spirit as
 * {@code HeuristicReceiptFieldExtractor}. English only in V1; keyword lists are keyed by
 * language code so more languages can be added without restructuring.
 *
 * Intent classification here is scored per-candidate rather than first-match-wins: if the top
 * two candidates tie, this returns UNKNOWN with a disambiguation question instead of silently
 * guessing (e.g. "remind me to pay rent" hits both TODO and EXPENSE keyword lists).
 */
@Component
public class HeuristicIntentParser implements IntentParser {

    private static final Map<VoiceIntent, Map<String, List<String>>> KEYWORDS = buildKeywords();

    /**
     * Every list is audited so no entry is a substring of another entry in the SAME list (e.g.
     * "saved" is a substring of nothing here, and "save" is kept instead of also listing "saved"/
     * "saving"/"savings" as separate entries) — otherwise one spoken word could satisfy multiple
     * list entries at once and silently inflate that intent's score relative to the others,
     * undermining the tie-based ambiguity check below. Cross-list overlaps (e.g. "pay" appearing
     * to relate to both EXPENSE and a TODO reminder) are fine and expected — that's exactly the
     * ambiguity this scorer is designed to catch.
     */
    private static Map<VoiceIntent, Map<String, List<String>>> buildKeywords() {
        Map<VoiceIntent, Map<String, List<String>>> m = new EnumMap<>(VoiceIntent.class);
        // "expense" itself is included so a bare reply to a disambiguation question ("did you mean
        // to log an expense, or log savings?" -> "expense") is recognized — every other intent's
        // own name already doubles as a keyword (income/saving/transfer/note/todo), this was the
        // one gap.
        m.put(VoiceIntent.EXPENSE, Map.of("en", List.of("expense", "spent", "spend", "paid", "pay", "bought", "buy", "purchase", "cost")));
        m.put(VoiceIntent.INCOME, Map.of("en", List.of("salary", "receive", "earn", "deposited", "income", "freelance", "got paid", "paid me")));
        m.put(VoiceIntent.SAVINGS, Map.of("en", List.of("save", "saving", "dps", "set aside", "put aside")));
        m.put(VoiceIntent.TRANSFER, Map.of("en", List.of("transfer", "send", "sent", "move money", "moved")));
        m.put(VoiceIntent.NOTE, Map.of("en", List.of("note", "remember", "jot down", "keep in mind")));
        m.put(VoiceIntent.TODO, Map.of("en", List.of("todo", "to-do", "to do", "task", "remind me", "reminder")));
        return m;
    }

    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "^(how much|how many|what|when|why|did i|have i|do i|is my|are my|what's|whats)\\b|\\?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)");
    private static final Pattern BOUGHT_PATTERN = Pattern.compile(
            "(?:bought|purchased)\\s+([a-zA-Z][a-zA-Z\\s]*?)\\s+(?:from|for|using|with|via)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_PATTERN = Pattern.compile(
            "\\bon\\s+([a-zA-Z][a-zA-Z\\s]*?)(?:\\s+(?:using|with|via)\\b|[.,]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOR_PATTERN = Pattern.compile(
            "\\bfor\\s+([a-zA-Z][a-zA-Z\\s]*?)(?:\\s+(?:using|with|via)\\b|[.,]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_MERCHANT_PATTERN = Pattern.compile(
            "\\bfrom\\s+([a-zA-Z][a-zA-Z\\s]*?)\\s+for\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern USING_WITH_VIA_PATTERN = Pattern.compile(
            "(?:using|with|via)\\s+([a-zA-Z][a-zA-Z0-9\\s]*?)(?:[.,]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFER_FROM_TO_PATTERN = Pattern.compile(
            "\\bfrom\\s+([a-zA-Z][a-zA-Z0-9\\s]*?)\\s+to\\s+([a-zA-Z][a-zA-Z0-9\\s]*?)(?:[.,]|$)", Pattern.CASE_INSENSITIVE);
    // Single-sided fallbacks for a transfer that only names one party (e.g. "send 500 to bKash") —
    // per the real endpoint's own validation, only ONE of source/destination needs to be named.
    private static final Pattern TRANSFER_TO_ONLY_PATTERN = Pattern.compile(
            "\\bto\\s+([a-zA-Z][a-zA-Z0-9\\s]*?)(?:[.,]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFER_FROM_ONLY_PATTERN = Pattern.compile(
            "\\bfrom\\s+([a-zA-Z][a-zA-Z0-9\\s]*?)(?:[.,]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTE_TRIGGER_PATTERN = Pattern.compile(
            "^(create a note[,:]?\\s*|note that\\s+|remember (to|that)\\s+|jot down\\s+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TODO_TRIGGER_PATTERN = Pattern.compile(
            "^(add a to-?do[,:]?\\s*|add a task[,:]?\\s*(to\\s+)?|remind me to\\s+|remind me\\s+(to\\s+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIGH_PRIORITY_PATTERN = Pattern.compile("\\b(urgent|important|asap|high priority)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOW_PRIORITY_PATTERN = Pattern.compile("\\b(whenever|low priority|not urgent)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public ParsedVoiceCommand parse(Long userId, String transcript, VoicePriorState priorState) {
        String text = transcript == null ? "" : transcript.trim();
        String lower = text.toLowerCase();

        if (priorState != null && priorState.isSingleFieldFollowUp()) {
            return parseFollowUpAnswer(text, priorState);
        }

        if (QUESTION_PATTERN.matcher(lower).find()) {
            return new ParsedVoiceCommand(VoiceIntent.QUERY, 0.6, "HEURISTIC", Map.of(), List.of(), null);
        }

        // An explicit trigger phrase ("remind me to...", "add a task...", "create a note...")
        // is a deliberate, unambiguous invocation of that feature — it must win even when the
        // verb inside it also happens to appear in another intent's keyword list (e.g. "remind me
        // to pay the bill" would otherwise tie TODO against EXPENSE on the word "pay").
        if (TODO_TRIGGER_PATTERN.matcher(text).find()) {
            return parseTodo(text, 0.65);
        }
        if (NOTE_TRIGGER_PATTERN.matcher(text).find()) {
            return parseNote(text, 0.65);
        }

        Map<VoiceIntent, Integer> scores = scoreIntents(lower);
        VoiceIntent top = null, second = null;
        int topScore = 0, secondScore = 0;
        for (Map.Entry<VoiceIntent, Integer> e : scores.entrySet()) {
            int s = e.getValue();
            if (s > topScore) { second = top; secondScore = topScore; top = e.getKey(); topScore = s; }
            else if (s > secondScore) { second = e.getKey(); secondScore = s; }
        }

        if (top == null || topScore == 0) {
            return new ParsedVoiceCommand(VoiceIntent.UNKNOWN, 0.0, "HEURISTIC", Map.of(), List.of(), null);
        }
        if (second != null && secondScore == topScore) {
            String question = "Did you mean to " + describe(top) + ", or " + describe(second) + "?";
            return new ParsedVoiceCommand(VoiceIntent.UNKNOWN, 0.0, "HEURISTIC", Map.of(), List.of(), question);
        }

        double confidence = Math.min(1.0, 0.5 + 0.15 * topScore);
        return switch (top) {
            case EXPENSE, INCOME, SAVINGS -> parseTransactionLike(top, text, confidence);
            case TRANSFER -> parseTransfer(text, confidence);
            case NOTE -> parseNote(text, confidence);
            case TODO -> parseTodo(text, confidence);
            default -> new ParsedVoiceCommand(VoiceIntent.UNKNOWN, 0.0, "HEURISTIC", Map.of(), List.of(), null);
        };
    }

    private String describe(VoiceIntent intent) {
        return switch (intent) {
            case EXPENSE -> "log an expense";
            case INCOME -> "log income";
            case SAVINGS -> "log savings";
            case TRANSFER -> "transfer money";
            case NOTE -> "create a note";
            case TODO -> "add a to-do";
            default -> "do something";
        };
    }

    private Map<VoiceIntent, Integer> scoreIntents(String lower) {
        Map<VoiceIntent, Integer> scores = new EnumMap<>(VoiceIntent.class);
        for (Map.Entry<VoiceIntent, Map<String, List<String>>> e : KEYWORDS.entrySet()) {
            List<String> words = e.getValue().getOrDefault("en", List.of());
            int score = 0;
            for (String kw : words) {
                if (lower.contains(kw)) score++;
            }
            scores.put(e.getKey(), score);
        }
        return scores;
    }

    private ParsedVoiceCommand parseFollowUpAnswer(String text, VoicePriorState priorState) {
        VoiceIntent intent = VoiceIntent.valueOf(priorState.lockedIntent());
        String label = priorState.missingRequiredFields().get(0);
        Map<String, Object> fields = new LinkedHashMap<>(priorState.draftFields());
        boolean answered = applyFollowUpAnswer(intent, label, text.trim(), fields);
        List<String> stillMissing = new ArrayList<>(priorState.missingRequiredFields());
        if (answered) stillMissing.remove(label);
        String followUp = stillMissing.isEmpty() ? null : VoiceFieldRules.followUpQuestionFor(intent, stillMissing.get(0));
        return new ParsedVoiceCommand(intent, 0.6, "HEURISTIC", fields, stillMissing, followUp);
    }

    /**
     * Maps a missing-field LABEL (the vocabulary in {@link VoiceFieldRules}) onto the actual
     * fields-map key(s) {@link VoiceFieldRules#isFieldPresent} checks — the two must never drift
     * apart, since a label written under the wrong key would never register as filled.
     */
    private boolean applyFollowUpAnswer(VoiceIntent intent, String label, String text, Map<String, Object> fields) {
        if (text.isBlank()) return false;
        switch (label) {
            case "amount" -> {
                Double amount = extractAmount(text);
                if (amount == null) return false;
                fields.put("amount", amount);
                return true;
            }
            case "category" -> {
                fields.put("categoryPhrase", text);
                return true;
            }
            case "account" -> {
                // Transfer's "account" label covers both sides at once; a single follow-up answer
                // can only supply one — default to the destination (the more common single-sided
                // phrasing, e.g. "send it to X") rather than guessing which side was meant.
                if (intent == VoiceIntent.TRANSFER) {
                    fields.put("destinationAccountPhrase", text);
                } else {
                    fields.put("accountPhrase", text);
                }
                return true;
            }
            case "noteContent" -> {
                fields.put("noteContent", text);
                fields.put("noteTitle", autoTitle(text));
                return true;
            }
            case "todoTitle" -> {
                fields.put("todoTitle", text);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private ParsedVoiceCommand parseTransactionLike(VoiceIntent intent, String text, double confidence) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Double amount = extractAmount(text);
        if (amount != null) fields.put("amount", amount);
        String merchant = firstNonBlank(matchGroup(FROM_MERCHANT_PATTERN, text, 1), matchGroup(BOUGHT_PATTERN, text, 1));
        String categoryPhrase = firstNonBlank(merchant, matchGroup(ON_PATTERN, text, 1), matchGroup(FOR_PATTERN, text, 1));
        if (merchant != null) fields.put("description", merchant);
        if (categoryPhrase != null) fields.put("categoryPhrase", categoryPhrase);
        String accountPhrase = matchGroup(USING_WITH_VIA_PATTERN, text, 1);
        if (accountPhrase != null) fields.put("accountPhrase", accountPhrase);
        LocalDate date = extractRelativeDate(text);
        if (date != null) fields.put("date", date.toString());

        List<String> missing = new ArrayList<>();
        if (amount == null) missing.add("amount");
        if (categoryPhrase == null) missing.add("category");
        String followUp = missing.isEmpty() ? null : VoiceFieldRules.followUpQuestionFor(intent, missing.get(0));
        return new ParsedVoiceCommand(intent, confidence, "HEURISTIC", fields, missing, followUp);
    }

    private ParsedVoiceCommand parseTransfer(String text, double confidence) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Double amount = extractAmount(text);
        if (amount != null) fields.put("amount", amount);
        Matcher m = TRANSFER_FROM_TO_PATTERN.matcher(text);
        String sourcePhrase = null, destinationPhrase = null;
        if (m.find()) {
            sourcePhrase = clean(m.group(1));
            destinationPhrase = clean(m.group(2));
        } else {
            destinationPhrase = matchGroup(TRANSFER_TO_ONLY_PATTERN, text, 1);
            if (destinationPhrase == null) {
                sourcePhrase = matchGroup(TRANSFER_FROM_ONLY_PATTERN, text, 1);
            }
        }
        if (sourcePhrase != null) fields.put("sourceAccountPhrase", sourcePhrase);
        if (destinationPhrase != null) fields.put("destinationAccountPhrase", destinationPhrase);

        List<String> missing = new ArrayList<>();
        if (amount == null) missing.add("amount");
        if (sourcePhrase == null && destinationPhrase == null) missing.add("account");
        String followUp = missing.isEmpty() ? null : VoiceFieldRules.followUpQuestionFor(VoiceIntent.TRANSFER, missing.get(0));
        return new ParsedVoiceCommand(VoiceIntent.TRANSFER, confidence, "HEURISTIC", fields, missing, followUp);
    }

    private ParsedVoiceCommand parseNote(String text, double confidence) {
        Map<String, Object> fields = new LinkedHashMap<>();
        String content = clean(NOTE_TRIGGER_PATTERN.matcher(text).replaceFirst(""));
        List<String> missing = new ArrayList<>();
        if (content == null || content.isBlank()) {
            missing.add("noteContent");
        } else {
            fields.put("noteContent", content);
            fields.put("noteTitle", autoTitle(content));
        }
        String followUp = missing.isEmpty() ? null : "What should the note say?";
        return new ParsedVoiceCommand(VoiceIntent.NOTE, confidence, "HEURISTIC", fields, missing, followUp);
    }

    private ParsedVoiceCommand parseTodo(String text, double confidence) {
        Map<String, Object> fields = new LinkedHashMap<>();
        String title = clean(TODO_TRIGGER_PATTERN.matcher(text).replaceFirst(""));
        List<String> missing = new ArrayList<>();
        if (title == null || title.isBlank()) {
            missing.add("todoTitle");
        } else {
            fields.put("todoTitle", title);
        }
        LocalDate date = extractRelativeDate(text);
        if (date != null) fields.put("todoDueDate", date.toString());
        if (HIGH_PRIORITY_PATTERN.matcher(text).find()) fields.put("todoPriority", "high");
        else if (LOW_PRIORITY_PATTERN.matcher(text).find()) fields.put("todoPriority", "low");
        String followUp = missing.isEmpty() ? null : "What's the to-do?";
        return new ParsedVoiceCommand(VoiceIntent.TODO, confidence, "HEURISTIC", fields, missing, followUp);
    }

    private Double extractAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Deliberately limited to today/tomorrow/yesterday — no full relative-date NLP in the fallback tier. */
    private LocalDate extractRelativeDate(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("tomorrow")) return LocalDate.now().plusDays(1);
        if (lower.contains("yesterday")) return LocalDate.now().minusDays(1);
        if (lower.contains("today")) return LocalDate.now();
        return null;
    }

    private String autoTitle(String content) {
        String trimmed = content.trim();
        String[] words = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String w : words) {
            if (count >= 6 || sb.length() + w.length() > 40) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
            count++;
        }
        String title = sb.toString();
        return title.isBlank() ? "Voice Note" : title;
    }

    private String matchGroup(Pattern pattern, String text, int group) {
        Matcher m = pattern.matcher(text);
        return m.find() ? clean(m.group(group)) : null;
    }

    private String clean(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.isBlank() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
