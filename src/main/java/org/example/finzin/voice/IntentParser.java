package org.example.finzin.voice;

/**
 * Abstraction over whatever turns a speech transcript into a classified intent + extracted
 * fields, so the implementation (AI prompt today, something else later) can be swapped without
 * touching {@link VoiceService} — mirrors {@code ReceiptFieldExtractor}'s role for receipt OCR.
 */
public interface IntentParser {
    ParsedVoiceCommand parse(Long userId, String transcript, VoicePriorState priorState);
}
