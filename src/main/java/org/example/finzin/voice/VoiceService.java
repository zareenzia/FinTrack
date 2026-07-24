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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates transcript -> classified intent -> resolved draft. Deliberately does NOT create
 * the transaction/note/todo itself — the frontend calls the existing
 * {@code POST /api/transactions|notes|todos} directly with the confirmed fields, reusing all
 * existing balance math, credit-card validation, and RAG indexing with zero duplicated logic.
 */
@Service
public class VoiceService {
    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private final IntentParser intentParser;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final VoiceCommandHistoryRepository historyRepository;
    private final VoiceSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    public VoiceService(IntentParser intentParser,
                         CategoryRepository categoryRepository,
                         AccountRepository accountRepository,
                         VoiceCommandHistoryRepository historyRepository,
                         VoiceSettingsRepository settingsRepository,
                         ObjectMapper objectMapper) {
        this.intentParser = intentParser;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.historyRepository = historyRepository;
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
    }

    public VoiceResultDTO parseCommand(Long userId, String transcript, VoicePriorState priorStateIn) {
        VoicePriorState priorState = priorStateIn != null ? priorStateIn : VoicePriorState.initial();

        ParsedVoiceCommand parsed = intentParser.parse(userId, transcript, priorState);

        VoiceIntent intent = priorState.isFollowUp() ? VoiceIntent.valueOf(priorState.lockedIntent()) : parsed.intent();

        Map<String, Object> mergedFields = new LinkedHashMap<>();
        if (priorState.isFollowUp()) mergedFields.putAll(priorState.draftFields());
        mergedFields.putAll(parsed.fields());

        if (intent == VoiceIntent.QUERY) {
            Long historyId = saveHistory(userId, transcript, intent, mergedFields, parsed.source(), "routed_to_chat", null, null);
            return new VoiceResultDTO(historyId, intent.name(), mergedFields, List.of(), null, parsed.confidence(), parsed.source(), true, false, null);
        }
        if (intent == VoiceIntent.UNKNOWN) {
            Long historyId = saveHistory(userId, transcript, intent, mergedFields, parsed.source(), "unresolved", null, null);
            boolean needsDisambiguation = parsed.followUpQuestion() != null;
            return new VoiceResultDTO(historyId, intent.name(), mergedFields, List.of(), parsed.followUpQuestion(),
                    parsed.confidence(), parsed.source(), !needsDisambiguation, !needsDisambiguation, null);
        }

        resolveReferences(userId, intent, mergedFields);

        List<String> stillMissing = new ArrayList<>();
        for (String required : VoiceFieldRules.requiredFieldsFor(intent)) {
            if (!VoiceFieldRules.isFieldPresent(required, mergedFields)) stillMissing.add(required);
        }

        int turnCount = priorState.isFollowUp() ? priorState.turnCount() + 1 : (stillMissing.isEmpty() ? 0 : 1);
        boolean isComplete = stillMissing.isEmpty();
        boolean giveUp = false;
        String followUpQuestion = isComplete ? null
                : (parsed.followUpQuestion() != null ? parsed.followUpQuestion()
                    : VoiceFieldRules.followUpQuestionFor(intent, stillMissing.get(0)));

        if (!isComplete && turnCount >= VoicePriorState.MAX_FOLLOW_UP_TURNS) {
            giveUp = true;
            isComplete = true;
            followUpQuestion = null;
        }

        Long historyId = saveHistory(userId, transcript, intent, mergedFields, parsed.source(),
                isComplete ? "pending" : "awaiting_followup", null, null);

        VoicePriorState nextState = isComplete ? null
                : new VoicePriorState(intent.name(), mergedFields, stillMissing, followUpQuestion, turnCount);

        return new VoiceResultDTO(historyId, intent.name(), mergedFields, stillMissing, followUpQuestion,
                parsed.confidence(), parsed.source(), isComplete, giveUp, nextState);
    }

    /** Mutates fields in place: replaces *Phrase keys with resolved *Id/*Name keys, branching by intent. */
    private void resolveReferences(Long userId, VoiceIntent intent, Map<String, Object> fields) {
        switch (intent) {
            case EXPENSE, INCOME, SAVINGS -> {
                String categoryPhrase = stringOf(fields.get("categoryPhrase"));
                if (categoryPhrase != null) {
                    resolveCategory(userId, intent.name().toLowerCase(Locale.ROOT), categoryPhrase)
                            .ifPresentOrElse(
                                    c -> { fields.put("categoryId", c.getId()); fields.put("categoryName", c.getName()); },
                                    () -> fields.put("categoryName", categoryPhrase));
                }
                String accountPhrase = stringOf(fields.get("accountPhrase"));
                if (accountPhrase != null) {
                    resolveAccount(userId, accountPhrase)
                            .ifPresentOrElse(
                                    a -> { fields.put("accountId", a.getId()); fields.put("accountName", a.getAccountNickname()); },
                                    () -> fields.put("accountName", accountPhrase));
                }
            }
            case TRANSFER -> {
                String sourcePhrase = stringOf(fields.get("sourceAccountPhrase"));
                if (sourcePhrase != null) {
                    resolveAccount(userId, sourcePhrase)
                            .ifPresentOrElse(
                                    a -> { fields.put("sourceAccountId", a.getId()); fields.put("sourceAccountName", a.getAccountNickname()); },
                                    () -> fields.put("sourceAccountName", sourcePhrase));
                }
                String destinationPhrase = stringOf(fields.get("destinationAccountPhrase"));
                if (destinationPhrase != null) {
                    resolveAccount(userId, destinationPhrase)
                            .ifPresentOrElse(
                                    a -> { fields.put("destinationAccountId", a.getId()); fields.put("destinationAccountName", a.getAccountNickname()); },
                                    () -> fields.put("destinationAccountName", destinationPhrase));
                }
            }
            case TODO -> {
                // TodoRequest.category is a free-text string, not an FK — never routed through category resolution.
            }
            case NOTE -> {
                // Notes have no category concept at all.
                if (fields.get("noteTitle") == null && fields.get("noteContent") != null) {
                    fields.put("noteTitle", autoTitle(stringOf(fields.get("noteContent"))));
                }
            }
            default -> {
            }
        }
    }

    /**
     * Exact case-insensitive name match first (never auto-creates), else a substring match only
     * if it resolves to exactly one candidate — an ambiguous substring hit is left unresolved
     * rather than guessed. Type-scoped so an expense category can never cross-match an income
     * transaction, matching the existing {@code GET /categories?type=} convention.
     */
    private Optional<CategoryEntity> resolveCategory(Long userId, String transactionType, String phrase) {
        List<CategoryEntity> candidates = categoryRepository.findByUserIdAndCategoryTypeOrGeneral(userId, transactionType);
        String needle = phrase.trim().toLowerCase(Locale.ROOT);

        Optional<CategoryEntity> exact = candidates.stream()
                .filter(c -> c.getName() != null && c.getName().trim().equalsIgnoreCase(phrase.trim()))
                .findFirst();
        if (exact.isPresent()) return exact;

        List<CategoryEntity> substringMatches = candidates.stream()
                .filter(c -> c.getName() != null)
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(needle) || needle.contains(c.getName().toLowerCase(Locale.ROOT)))
                .toList();
        return substringMatches.size() == 1 ? Optional.of(substringMatches.get(0)) : Optional.empty();
    }

    /**
     * Same never-guess-on-ambiguity rule as {@link #resolveCategory}: exact nickname match first,
     * else a substring match against nickname/bankName/provider only if exactly one active
     * account qualifies.
     */
    private Optional<AccountEntity> resolveAccount(Long userId, String phrase) {
        List<AccountEntity> active = accountRepository.findByUserIdAndStatus(userId, "ACTIVE");
        String needle = phrase.trim().toLowerCase(Locale.ROOT);

        Optional<AccountEntity> exact = active.stream()
                .filter(a -> a.getAccountNickname() != null && a.getAccountNickname().trim().equalsIgnoreCase(phrase.trim()))
                .findFirst();
        if (exact.isPresent()) return exact;

        List<AccountEntity> substringMatches = active.stream()
                .filter(a -> containsIgnoreCase(a.getAccountNickname(), needle)
                        || containsIgnoreCase(a.getBankName(), needle)
                        || containsIgnoreCase(a.getProvider(), needle))
                .toList();
        return substringMatches.size() == 1 ? Optional.of(substringMatches.get(0)) : Optional.empty();
    }

    private boolean containsIgnoreCase(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private String stringOf(Object value) {
        if (value == null) return null;
        String s = value.toString().trim();
        return s.isBlank() ? null : s;
    }

    /** Mirrors {@code ConversationService.autoTitle}'s truncate-for-a-title fallback convention. */
    private String autoTitle(String content) {
        if (content == null) return "Voice Note";
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return "Voice Note";
        String[] words = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String w : words) {
            if (count >= 6 || sb.length() + w.length() > 40) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
            count++;
        }
        return sb.isEmpty() ? "Voice Note" : sb.toString();
    }

    private Long saveHistory(Long userId, String transcript, VoiceIntent intent, Map<String, Object> fields,
                              String source, String status, String resolvedEntityType, Long resolvedEntityId) {
        VoiceCommandHistoryEntity entity = new VoiceCommandHistoryEntity();
        entity.setUserId(userId);
        entity.setOriginalTranscript(transcript);
        entity.setIntent(intent.name());
        entity.setSource(source);
        entity.setStatus(status);
        entity.setResolvedEntityType(resolvedEntityType);
        entity.setResolvedEntityId(resolvedEntityId);
        entity.setParsedJson(writeJsonSafely(fields));
        return historyRepository.save(entity).getId();
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize voice command fields: {}", e.getMessage());
            return null;
        }
    }

    // ---- History ----

    public List<Map<String, Object>> getHistory(Long userId) {
        return historyRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    /**
     * Called by a small follow-up PATCH after the frontend's own save call to the real endpoint
     * succeeds. If this never arrives (tab closed, etc.) the history row just stays "pending"
     * forever — an inert, cosmetic UI state ("not confirmed"), never an error: the real
     * transaction/note/todo already exists independently once the frontend's save call succeeded,
     * so this table is an audit log, not a source of truth.
     */
    public boolean updateHistoryStatus(Long userId, Long historyId, String status, String resolvedEntityType, Long resolvedEntityId) {
        Optional<VoiceCommandHistoryEntity> found = historyRepository.findByIdAndUserId(historyId, userId);
        if (found.isEmpty()) return false;
        VoiceCommandHistoryEntity entity = found.get();
        entity.setStatus(status);
        if (resolvedEntityType != null) entity.setResolvedEntityType(resolvedEntityType);
        if (resolvedEntityId != null) entity.setResolvedEntityId(resolvedEntityId);
        historyRepository.save(entity);
        return true;
    }

    @Transactional
    public boolean deleteHistoryItem(Long userId, Long id) {
        Optional<VoiceCommandHistoryEntity> found = historyRepository.findByIdAndUserId(id, userId);
        if (found.isEmpty()) return false;
        historyRepository.deleteByIdAndUserId(id, userId);
        return true;
    }

    @Transactional
    public void clearHistory(Long userId) {
        historyRepository.deleteByUserId(userId);
    }

    private Map<String, Object> toHistoryResponse(VoiceCommandHistoryEntity e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.getId());
        map.put("originalTranscript", e.getOriginalTranscript());
        map.put("correctedText", e.getCorrectedText());
        map.put("intent", e.getIntent());
        map.put("source", e.getSource());
        map.put("status", e.getStatus());
        map.put("resolvedEntityType", e.getResolvedEntityType());
        map.put("resolvedEntityId", e.getResolvedEntityId());
        map.put("createdAt", e.getCreatedAt());
        return map;
    }

    // ---- Settings ----

    public VoiceSettingsEntity getOrDefaultSettings(Long userId) {
        return settingsRepository.findByUserId(userId).orElseGet(() -> {
            VoiceSettingsEntity entity = new VoiceSettingsEntity();
            entity.setUserId(userId);
            return settingsRepository.save(entity);
        });
    }

    public VoiceSettingsEntity updateSettings(Long userId, Boolean enabled, String language, String speechProvider,
                                               Integer autoStopSilenceSeconds, Boolean noiseReduction,
                                               Boolean saveAudioRecordings, Integer maxRecordingLengthSeconds,
                                               Double speechSpeed) {
        VoiceSettingsEntity entity = getOrDefaultSettings(userId);
        if (enabled != null) entity.setEnabled(enabled);
        if (language != null && !language.isBlank()) entity.setLanguage(language.trim());
        if (speechProvider != null && !speechProvider.isBlank()) entity.setSpeechProvider(speechProvider.trim());
        if (autoStopSilenceSeconds != null) entity.setAutoStopSilenceSeconds(autoStopSilenceSeconds);
        if (noiseReduction != null) entity.setNoiseReduction(noiseReduction);
        if (saveAudioRecordings != null) entity.setSaveAudioRecordings(saveAudioRecordings);
        if (maxRecordingLengthSeconds != null) entity.setMaxRecordingLengthSeconds(maxRecordingLengthSeconds);
        if (speechSpeed != null) entity.setSpeechSpeed(speechSpeed);
        return settingsRepository.save(entity);
    }
}
