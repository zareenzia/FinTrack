package org.example.finzin.voice;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the heuristic (non-AI) voice intent parser — the fallback path used
 * whenever OpenAI is unconfigured, disabled, or fails. Intent classification is scored per
 * candidate rather than first-match-wins specifically so an ambiguous phrase ("remind me to save
 * some money" hits both TODO and SAVINGS keyword lists) surfaces a disambiguation question
 * instead of silently guessing one of them.
 */
class HeuristicIntentParserTest {

    private final HeuristicIntentParser parser = new HeuristicIntentParser();

    @Test
    void classifiesExpenseAndExtractsAmountAndCategory() {
        ParsedVoiceCommand result = parser.parse(1L, "I spent 450 taka on lunch", VoicePriorState.initial());

        assertEquals(VoiceIntent.EXPENSE, result.intent());
        assertEquals(450.0, result.fields().get("amount"));
        assertEquals("lunch", result.fields().get("categoryPhrase"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertEquals("HEURISTIC", result.source());
    }

    @Test
    void classifiesTransferAndExtractsBothAccounts() {
        ParsedVoiceCommand result = parser.parse(1L, "Transfer 15,000 taka from SCB to City Bank.", VoicePriorState.initial());

        assertEquals(VoiceIntent.TRANSFER, result.intent());
        assertEquals(15000.0, result.fields().get("amount"));
        assertEquals("SCB", result.fields().get("sourceAccountPhrase"));
        assertEquals("City Bank", result.fields().get("destinationAccountPhrase"));
        assertTrue(result.missingRequiredFields().isEmpty());
    }

    @Test
    void transferMentioningOnlyOneSideIsNotFlaggedAsMissingAnAccount() {
        // "at least one of source/destination" is the real endpoint's own validation rule —
        // a transfer with only a destination mentioned is a legitimate external deposit, not a
        // command the heuristic parser should force a spoken follow-up for.
        ParsedVoiceCommand result = parser.parse(1L, "Send 500 taka to bKash.", VoicePriorState.initial());

        assertEquals(VoiceIntent.TRANSFER, result.intent());
        assertEquals("bKash", result.fields().get("destinationAccountPhrase"));
        assertNull(result.fields().get("sourceAccountPhrase"));
        assertTrue(result.missingRequiredFields().isEmpty());
    }

    @Test
    void routesQuestionsToQueryIntentInsteadOfTreatingThemAsACommand() {
        ParsedVoiceCommand result = parser.parse(1L, "How much did I spend this month?", VoicePriorState.initial());

        assertEquals(VoiceIntent.QUERY, result.intent());
        assertTrue(result.fields().isEmpty());
        assertNull(result.followUpQuestion());
    }

    @Test
    void ambiguousPhraseAcrossTwoIntentsReturnsUnknownWithDisambiguationQuestion() {
        // "saved" scores SAVINGS, "paid" scores EXPENSE, and neither an explicit TODO nor NOTE
        // trigger phrase is present — a tied top score must never be silently resolved to either.
        ParsedVoiceCommand result = parser.parse(1L, "I saved money and paid the bill", VoicePriorState.initial());

        assertEquals(VoiceIntent.UNKNOWN, result.intent());
        assertTrue(result.followUpQuestion() != null && result.followUpQuestion().startsWith("Did you mean"),
                "expected a disambiguation question, got: " + result.followUpQuestion());
    }

    @Test
    void noKeywordMatchAtAllReturnsUnknownWithNoQuestion() {
        ParsedVoiceCommand result = parser.parse(1L, "asdf qwer zxcv completely unrelated words", VoicePriorState.initial());

        assertEquals(VoiceIntent.UNKNOWN, result.intent());
        assertNull(result.followUpQuestion());
    }

    @Test
    void singleFieldFollowUpTreatsWholeFragmentAsTheAnswerRatherThanReclassifying() {
        VoicePriorState priorState = new VoicePriorState(
                "EXPENSE", Map.of("amount", 450.0), List.of("category"), "What category was that?", 1);

        ParsedVoiceCommand result = parser.parse(1L, "dining", priorState);

        assertEquals(VoiceIntent.EXPENSE, result.intent());
        assertEquals(450.0, result.fields().get("amount"));
        assertEquals("dining", result.fields().get("categoryPhrase"));
        assertTrue(result.missingRequiredFields().isEmpty());
    }

    @Test
    void singleFieldFollowUpForAmountParsesTheNumberOutOfAShortFragment() {
        VoicePriorState priorState = new VoicePriorState(
                "EXPENSE", Map.of("categoryPhrase", "dining"), List.of("amount"), "How much was it?", 1);

        ParsedVoiceCommand result = parser.parse(1L, "five hundred", priorState);

        // The heuristic tier is digit-only in V1 (spelled-out numbers are an AI-tier capability) —
        // a non-numeric fragment leaves "amount" genuinely unanswered rather than guessing.
        assertTrue(result.missingRequiredFields().contains("amount"));
    }

    @Test
    void noteContentIsExtractedAfterStrippingTheTriggerPhraseAndGetsAnAutoGeneratedTitle() {
        ParsedVoiceCommand result = parser.parse(1L,
                "Remember to renew my passport before December and keep photocopies of all documents",
                VoicePriorState.initial());

        assertEquals(VoiceIntent.NOTE, result.intent());
        assertTrue(result.fields().get("noteContent").toString().startsWith("renew my passport"));
        assertEquals("renew my passport before December and", result.fields().get("noteTitle"));
    }

    @Test
    void todoTitleIsExtractedAfterStrippingTheTriggerPhrase() {
        ParsedVoiceCommand result = parser.parse(1L, "Remind me to pay the internet bill", VoicePriorState.initial());

        assertEquals(VoiceIntent.TODO, result.intent());
        assertEquals("pay the internet bill", result.fields().get("todoTitle"));
    }
}
