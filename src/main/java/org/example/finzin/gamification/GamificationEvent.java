package org.example.finzin.gamification;

import java.util.Map;

/**
 * Published by existing modules (via {@code ApplicationEventPublisher}) after they've already
 * finished their own real work — this event carries no ability to influence the caller, it's
 * purely a "this already happened" notice for {@link GamificationEventListener} to react to.
 *
 * @param metadata event-specific extras (e.g. {"transactionType": "expense", "amount": 450.0,
 *                 "sourceId": "1234"}) — kept as a loose map rather than one record per event
 *                 type, since the set of fields genuinely varies and this event has exactly one
 *                 internal consumer that already knows what to expect per {@link GamificationEventType}.
 */
public record GamificationEvent(Long userId, GamificationEventType type, Map<String, Object> metadata) {
}
