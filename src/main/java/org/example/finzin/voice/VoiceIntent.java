package org.example.finzin.voice;

/**
 * What a voice command is asking the app to do. QUERY means a financial question
 * ("how much did I spend this month") — never handled here; the frontend forwards the
 * transcript verbatim to the existing {@code POST /api/ai/chat} instead of duplicating a
 * second chatbot. UNKNOWN means neither parser tier was confident enough to pick one intent.
 */
public enum VoiceIntent {
    EXPENSE,
    INCOME,
    SAVINGS,
    TRANSFER,
    NOTE,
    TODO,
    QUERY,
    UNKNOWN
}
