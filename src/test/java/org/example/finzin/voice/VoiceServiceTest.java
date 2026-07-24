package org.example.finzin.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.VoiceCommandHistoryEntity;
import org.example.finzin.entity.VoiceSettingsEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.VoiceCommandHistoryRepository;
import org.example.finzin.repository.VoiceSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (no Spring context), matching {@code AIServiceTest}'s convention.
 * Covers the correctness-critical rules from the Voice Assistant plan: category resolution is
 * type-scoped and never auto-creates, account required-ness differs between transfer and every
 * other transaction-like intent, the follow-up-turn cap actually stops asking, and history status
 * transitions are ownership-checked.
 */
@ExtendWith(MockitoExtension.class)
class VoiceServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private IntentParser intentParser;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private VoiceCommandHistoryRepository historyRepository;
    @Mock private VoiceSettingsRepository settingsRepository;

    private VoiceService voiceService;

    @BeforeEach
    void setUp() {
        voiceService = new VoiceService(intentParser, categoryRepository, accountRepository,
                historyRepository, settingsRepository, new ObjectMapper());

        // Not every test triggers a save() (the settings-only test doesn't touch history at all).
        lenient().when(historyRepository.save(any(VoiceCommandHistoryEntity.class))).thenAnswer(inv -> {
            VoiceCommandHistoryEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(100L);
            return e;
        });
    }

    private CategoryEntity category(Long id, String name) {
        CategoryEntity c = new CategoryEntity();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private AccountEntity account(Long id, String nickname, String bankName) {
        AccountEntity a = new AccountEntity();
        a.setId(id);
        a.setAccountNickname(nickname);
        a.setBankName(bankName);
        return a;
    }

    @Test
    void resolvesCategoryByExactNameWithinTheCorrectTransactionType() {
        when(categoryRepository.findByUserIdAndCategoryTypeOrGeneral(USER_ID, "expense"))
                .thenReturn(List.of(category(9L, "Dining")));
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.EXPENSE, 0.9, "AI",
                Map.of("amount", 450.0, "categoryPhrase", "Dining"), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "I spent 450 on dining", null);

        assertEquals(9L, result.fields().get("categoryId"));
        assertEquals("Dining", result.fields().get("categoryName"));
        assertTrue(result.isComplete());
    }

    @Test
    void neverAutoCreatesACategoryWhenNothingMatchesEvenApproximately() {
        when(categoryRepository.findByUserIdAndCategoryTypeOrGeneral(USER_ID, "expense"))
                .thenReturn(List.of(category(9L, "Dining")));
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.EXPENSE, 0.9, "AI",
                Map.of("amount", 450.0, "categoryPhrase", "Spelunking Gear"), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "I spent 450 on spelunking gear", null);

        assertNull(result.fields().get("categoryId"));
        assertEquals("Spelunking Gear", result.fields().get("categoryName"));
    }

    @Test
    void ambiguousSubstringMatchAcrossMultipleCategoriesIsLeftUnresolvedRatherThanGuessed() {
        when(categoryRepository.findByUserIdAndCategoryTypeOrGeneral(USER_ID, "expense"))
                .thenReturn(List.of(category(1L, "Fun Money"), category(2L, "Refund Tracking")));
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.EXPENSE, 0.9, "AI",
                Map.of("amount", 100.0, "categoryPhrase", "fun"), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "I spent 100 on fun", null);

        // "fun" is a substring of both "Fun Money" and "reFUNd Tracking" — must not silently pick one.
        assertNull(result.fields().get("categoryId"));
    }

    @Test
    void accountIsOptionalForExpenseSoAnUnmentionedAccountIsNotFlaggedAsMissing() {
        when(categoryRepository.findByUserIdAndCategoryTypeOrGeneral(USER_ID, "expense")).thenReturn(List.of());
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.EXPENSE, 0.9, "AI",
                Map.of("amount", 450.0, "categoryPhrase", "Dining"), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "I spent 450 on dining", null);

        assertTrue(result.isComplete());
        assertFalse(result.missingRequiredFields().contains("account"));
    }

    @Test
    void transferRequiresAtLeastOneAccountButNotBoth() {
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.TRANSFER, 0.9, "AI",
                Map.of("amount", 500.0), List.of("account"), "Which accounts — from and to?"));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "Transfer 500", null);

        assertFalse(result.isComplete());
        assertTrue(result.missingRequiredFields().contains("account"));
        assertNotNull(result.followUpQuestion());
    }

    @Test
    void transferWithOnlyOneSideNamedIsNotFlaggedAsMissingAnAccount() {
        when(accountRepository.findByUserIdAndStatus(USER_ID, "ACTIVE"))
                .thenReturn(List.of(account(5L, "bKash", null)));
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.TRANSFER, 0.9, "AI",
                Map.of("amount", 500.0, "destinationAccountPhrase", "bKash"), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "Send 500 to bKash", null);

        assertTrue(result.isComplete());
        assertEquals(5L, result.fields().get("destinationAccountId"));
    }

    @Test
    void followUpCapStopsAskingAndSignalsGiveUpAfterMaxTurns() {
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.EXPENSE, 0.6, "HEURISTIC", Map.of("amount", 450.0), List.of("category"), "What category was that?"));

        VoicePriorState priorState = new VoicePriorState("EXPENSE", Map.of("amount", 450.0),
                List.of("category"), "What category was that?", VoicePriorState.MAX_FOLLOW_UP_TURNS);

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "still not sure", priorState);

        assertTrue(result.giveUp());
        assertTrue(result.isComplete());
        assertNull(result.followUpQuestion());
        assertNull(result.priorState());
    }

    @Test
    void unknownIntentWithoutDisambiguationQuestionGivesUpImmediately() {
        when(intentParser.parse(eq(USER_ID), any(), any()))
                .thenReturn(new ParsedVoiceCommand(VoiceIntent.UNKNOWN, 0.0, "HEURISTIC", Map.of(), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "asdf qwer", null);

        assertEquals("UNKNOWN", result.intent());
        assertTrue(result.giveUp());
    }

    @Test
    void queryIntentIsMarkedCompleteWithNoFollowUpSoTheFrontendForwardsToChat() {
        when(intentParser.parse(eq(USER_ID), any(), any()))
                .thenReturn(new ParsedVoiceCommand(VoiceIntent.QUERY, 0.6, "HEURISTIC", Map.of(), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "How much did I spend this month?", null);

        assertEquals("QUERY", result.intent());
        assertTrue(result.isComplete());
        assertFalse(result.giveUp());
    }

    @Test
    void noteWithContentButNoTitleGetsAnAutoGeneratedTitle() {
        when(intentParser.parse(eq(USER_ID), any(), any())).thenReturn(new ParsedVoiceCommand(
                VoiceIntent.NOTE, 0.9, "AI",
                Map.of("noteContent", "renew my passport before December"), List.of(), null));

        VoiceResultDTO result = voiceService.parseCommand(USER_ID, "create a note", null);

        assertEquals("renew my passport before December", result.fields().get("noteTitle"));
    }

    @Test
    void historyStatusCanOnlyBeUpdatedByItsOwningUser() {
        VoiceCommandHistoryEntity existing = new VoiceCommandHistoryEntity();
        existing.setId(7L);
        existing.setUserId(USER_ID);
        when(historyRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(existing));
        when(historyRepository.findByIdAndUserId(7L, 999L)).thenReturn(Optional.empty());

        assertTrue(voiceService.updateHistoryStatus(USER_ID, 7L, "completed", "transaction", 123L));
        assertEquals("completed", existing.getStatus());
        assertEquals(123L, existing.getResolvedEntityId());

        assertFalse(voiceService.updateHistoryStatus(999L, 7L, "completed", "transaction", 123L));
    }

    @Test
    void settingsRoundTripAppliesOnlyNonNullFields() {
        VoiceSettingsEntity existing = new VoiceSettingsEntity();
        existing.setUserId(USER_ID);
        existing.setEnabled(true);
        existing.setLanguage("en-US");
        when(settingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(VoiceSettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        VoiceSettingsEntity updated = voiceService.updateSettings(USER_ID, false, null, null, null, null, null, null, null);

        assertFalse(updated.getEnabled());
        assertEquals("en-US", updated.getLanguage()); // untouched fields keep their prior value
    }
}
