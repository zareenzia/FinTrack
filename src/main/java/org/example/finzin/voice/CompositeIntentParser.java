package org.example.finzin.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The only {@link IntentParser} bean {@code VoiceService} depends on. Tries the AI parser first;
 * falls back to the always-succeeding heuristic parser if OpenAI is unconfigured, disabled, or
 * the call fails for any reason (rate limit, timeout, malformed JSON, etc.) — byte-for-byte the
 * same shape as {@code CompositeReceiptFieldExtractor}. Depends on the two concrete classes
 * directly (not by interface) to avoid a circular lookup, same reasoning as that class.
 */
@Component
@Primary
public class CompositeIntentParser implements IntentParser {
    private static final Logger log = LoggerFactory.getLogger(CompositeIntentParser.class);

    private final AiIntentParser aiParser;
    private final HeuristicIntentParser heuristicParser;

    public CompositeIntentParser(AiIntentParser aiParser, HeuristicIntentParser heuristicParser) {
        this.aiParser = aiParser;
        this.heuristicParser = heuristicParser;
    }

    @Override
    public ParsedVoiceCommand parse(Long userId, String transcript, VoicePriorState priorState) {
        try {
            return aiParser.parse(userId, transcript, priorState);
        } catch (Exception e) {
            log.warn("AI voice intent parsing unavailable, falling back to heuristic parser: {}", e.getMessage());
            return heuristicParser.parse(userId, transcript, priorState);
        }
    }
}
