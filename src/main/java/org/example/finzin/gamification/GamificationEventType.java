package org.example.finzin.gamification;

/**
 * What happened in another module that the gamification engine should react to. Purely
 * observational — nothing in this enum's consumers is ever allowed to affect the business logic
 * that raised the event.
 */
public enum GamificationEventType {
    TRANSACTION_LOGGED,
    NOTE_CREATED,
    TODO_COMPLETED,
    INVESTMENT_CREATED,
    LOAN_EMI_PAID,
    GOAL_COMPLETED,
    RECEIPT_SCANNED,
    AI_CONVERSATION_COMPLETED,
    DAILY_ACTIVE,
    BUDGET_PLAN_CREATED,
    GOLD_ASSET_CREATED
}
