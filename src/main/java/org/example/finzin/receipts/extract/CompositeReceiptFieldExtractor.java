package org.example.finzin.receipts.extract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The only {@link ReceiptFieldExtractor} bean {@code ReceiptService} depends on. Tries the AI
 * extractor first; falls back to the always-succeeding heuristic extractor if OpenAI is
 * unconfigured or the call fails for any reason (rate limit, timeout, malformed JSON, etc.).
 * Depends on the two concrete classes directly (not by interface) to avoid a circular lookup;
 * {@code @Primary} resolves the ambiguity for other consumers, since {@link AiReceiptFieldExtractor}
 * and {@link HeuristicReceiptFieldExtractor} are themselves also {@link ReceiptFieldExtractor} beans.
 */
@Component
@Primary
public class CompositeReceiptFieldExtractor implements ReceiptFieldExtractor {
    private static final Logger log = LoggerFactory.getLogger(CompositeReceiptFieldExtractor.class);

    private final AiReceiptFieldExtractor aiExtractor;
    private final HeuristicReceiptFieldExtractor heuristicExtractor;

    public CompositeReceiptFieldExtractor(AiReceiptFieldExtractor aiExtractor, HeuristicReceiptFieldExtractor heuristicExtractor) {
        this.aiExtractor = aiExtractor;
        this.heuristicExtractor = heuristicExtractor;
    }

    @Override
    public ReceiptFieldExtractionResult extract(String ocrText) {
        try {
            return aiExtractor.extract(ocrText);
        } catch (Exception e) {
            log.warn("AI receipt field extraction unavailable, falling back to heuristic extractor: {}", e.getMessage());
            return heuristicExtractor.extract(ocrText);
        }
    }
}
