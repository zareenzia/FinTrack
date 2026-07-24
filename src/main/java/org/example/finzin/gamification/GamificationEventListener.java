package org.example.finzin.gamification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.Map;

/**
 * The single consumer of every {@link GamificationEvent}. One rule governs which listener
 * annotation a given publisher should use, not a hook-by-hook judgment call: AFTER_COMMIT only
 * for events published from inside an already-{@code @Transactional} method that has other side
 * effects still open (today: only {@code AccountBalanceService}'s transaction paths — a plain
 * listener there would run synchronously inside that still-open transaction, so a bug here could
 * roll back the user's real balance update). Everywhere else (notes/todos/investments/loans/
 * goals/receipts/AI chat/budget plans/gold assets/daily-active) the publish happens right after a
 * plain repository {@code .save()} that has already committed on its own, so a plain
 * {@code @EventListener} is correct and AFTER_COMMIT would be pointless.
 *
 * Every handler method is wrapped so nothing can ever escape and propagate back to the publisher
 * — this codebase has no existing precedent for that discipline elsewhere (see
 * {@code FinancialHealthService}'s unguarded snapshot upsert), so it's enforced here explicitly
 * rather than assumed.
 *
 * Gamification only reacts to creation/first-completion events, never to edits or deletes —
 * reversing XP/counters/achievements precisely on an edit or delete would add a large amount of
 * undo-logic complexity for a purely cosmetic, non-authoritative layer; this is a deliberate,
 * one-directional simplification.
 *
 * {@code onTransactionCommitted} runs its work in a brand-new transaction ({@code REQUIRES_NEW}):
 * an AFTER_COMMIT callback fires while the just-completed transaction's synchronization is still
 * bound to the thread (cleanup happens slightly later), so a plain {@code @Transactional} write
 * here would silently try to join that already-finished transaction instead of starting a fresh
 * one — Hibernate happily returns the in-memory entity as "saved" but nothing is ever actually
 * committed. Confirmed live: without REQUIRES_NEW, {@code transactions.count} never persisted
 * despite every method call completing without error.
 */
@Component
public class GamificationEventListener {
    private static final Logger log = LoggerFactory.getLogger(GamificationEventListener.class);

    private final XPService xpService;
    private final ProgressService progressService;
    private final AchievementEngine achievementEngine;
    private final StreakService streakService;
    private final GamificationSettingsService settingsService;

    @Value("${gamification.xp.expense-logged:2}")
    private int xpExpenseLogged;
    @Value("${gamification.xp.income-logged:3}")
    private int xpIncomeLogged;
    @Value("${gamification.xp.savings-logged:10}")
    private int xpSavingsLogged;
    @Value("${gamification.xp.note-created:2}")
    private int xpNoteCreated;
    @Value("${gamification.xp.todo-completed:3}")
    private int xpTodoCompleted;
    @Value("${gamification.xp.receipt-scanned:10}")
    private int xpReceiptScanned;
    @Value("${gamification.xp.ai-assistant-used:5}")
    private int xpAiAssistantUsed;
    @Value("${gamification.xp.daily-login:5}")
    private int xpDailyLogin;
    @Value("${gamification.xp.budget-created:20}")
    private int xpBudgetCreated;
    @Value("${gamification.xp.goal-completed:150}")
    private int xpGoalCompleted;
    @Value("${gamification.xp.loan-emi-paid:25}")
    private int xpLoanEmiPaid;
    @Value("${gamification.xp.investment-logged:5}")
    private int xpInvestmentLogged;

    public GamificationEventListener(XPService xpService, ProgressService progressService,
                                      AchievementEngine achievementEngine, StreakService streakService,
                                      GamificationSettingsService settingsService) {
        this.xpService = xpService;
        this.progressService = progressService;
        this.achievementEngine = achievementEngine;
        this.streakService = streakService;
        this.settingsService = settingsService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTransactionCommitted(GamificationEvent event) {
        if (event.type() != GamificationEventType.TRANSACTION_LOGGED) return;
        safely(() -> handleTransactionLogged(event));
    }

    @EventListener
    public void onEvent(GamificationEvent event) {
        if (event.type() == GamificationEventType.TRANSACTION_LOGGED) return; // handled AFTER_COMMIT above
        safely(() -> dispatch(event));
    }

    private void dispatch(GamificationEvent event) {
        switch (event.type()) {
            case NOTE_CREATED -> handleNoteCreated(event);
            case TODO_COMPLETED -> handleTodoCompleted(event);
            case INVESTMENT_CREATED -> handleInvestmentCreated(event);
            case LOAN_EMI_PAID -> handleLoanEmiPaid(event);
            case GOAL_COMPLETED -> handleGoalCompleted(event);
            case RECEIPT_SCANNED -> handleReceiptScanned(event);
            case AI_CONVERSATION_COMPLETED -> handleAiConversationCompleted(event);
            case DAILY_ACTIVE -> handleDailyActive(event);
            case BUDGET_PLAN_CREATED -> handleBudgetPlanCreated(event);
            case GOLD_ASSET_CREATED -> handleGoldAssetCreated(event);
            default -> { /* TRANSACTION_LOGGED handled elsewhere */ }
        }
    }

    private void handleTransactionLogged(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        Map<String, Object> meta = event.metadata();
        String transactionType = String.valueOf(meta.get("transactionType"));
        String sourceId = String.valueOf(meta.get("transactionId"));
        double amount = numberOf(meta.get("amount"));

        progressService.incrementCounter(userId, "transactions.count", 1);
        achievementEngine.checkMetric(userId, "transactions.count");
        achievementEngine.checkMetric(userId, "creditcard.headroom_percent");

        switch (transactionType) {
            case "expense" -> xpService.awardXp(userId, xpExpenseLogged, "EXPENSE_LOGGED", "TRANSACTION", sourceId);
            case "income" -> xpService.awardXp(userId, xpIncomeLogged, "INCOME_LOGGED", "TRANSACTION", sourceId);
            case "savings" -> {
                xpService.awardXp(userId, xpSavingsLogged, "SAVINGS_LOGGED", "TRANSACTION", sourceId);
                progressService.incrementCounter(userId, "savings.total", amount);
                achievementEngine.checkMetric(userId, "savings.total");
            }
            default -> { /* transfer — no flat per-action XP defined */ }
        }
    }

    private void handleNoteCreated(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        String sourceId = String.valueOf(event.metadata().get("noteId"));
        progressService.incrementCounter(userId, "notes.count", 1);
        achievementEngine.checkMetric(userId, "notes.count");
        xpService.awardXp(userId, xpNoteCreated, "NOTE_CREATED", "NOTE", sourceId);
    }

    private void handleTodoCompleted(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        String sourceId = String.valueOf(event.metadata().get("todoId"));
        progressService.incrementCounter(userId, "todos.completed_count", 1);
        achievementEngine.checkMetric(userId, "todos.completed_count");
        xpService.awardXp(userId, xpTodoCompleted, "TODO_COMPLETED", "TODO", sourceId);
    }

    private void handleInvestmentCreated(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        String sourceId = String.valueOf(event.metadata().get("investmentId"));
        progressService.incrementCounter(userId, "investments.count", 1);
        achievementEngine.checkMetric(userId, "investments.count");
        achievementEngine.checkMetric(userId, "investments.portfolio_value");
        achievementEngine.checkMetric(userId, "investments.distinct_types");
        xpService.awardXp(userId, xpInvestmentLogged, "INVESTMENT_LOGGED", "INVESTMENT", sourceId);
    }

    private void handleLoanEmiPaid(GamificationEvent event) {
        Long userId = event.userId();
        Long loanId = (Long) event.metadata().get("loanId");
        double newBalance = numberOf(event.metadata().get("newBalance"));
        boolean paidOff = Boolean.TRUE.equals(event.metadata().get("paidOff"));
        // sourceId encodes loanId + the resulting balance: each distinct payment strictly lowers
        // the balance to a value never seen before, so this both awards XP per real payment and
        // still dedups a genuine duplicate/retried request (which would recompute the same value).
        xpService.awardXp(userId, xpLoanEmiPaid, "LOAN_EMI_PAID", "LOAN", loanId + ":" + newBalance);
        if (!isEnabled(userId)) return;
        if (paidOff) {
            progressService.incrementCounter(userId, "loans.paid_off_count", 1);
            achievementEngine.checkMetric(userId, "loans.paid_off_count");
        }
        achievementEngine.checkMetric(userId, "loans.debt_reduced_percent");
        achievementEngine.checkMetric(userId, "loans.debt_free");
    }

    private void handleGoalCompleted(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        String sourceId = String.valueOf(event.metadata().get("goalId"));
        progressService.incrementCounter(userId, "goals.completed_count", 1);
        achievementEngine.checkMetric(userId, "goals.completed_count");
        xpService.awardXp(userId, xpGoalCompleted, "GOAL_COMPLETED", "GOAL", sourceId);
    }

    private void handleReceiptScanned(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        String sourceId = String.valueOf(event.metadata().get("receiptId"));
        progressService.incrementCounter(userId, "receipts.scanned_count", 1);
        achievementEngine.checkMetric(userId, "receipts.scanned_count");
        xpService.awardXp(userId, xpReceiptScanned, "RECEIPT_SCANNED", "RECEIPT", sourceId);
    }

    private void handleAiConversationCompleted(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        progressService.incrementCounter(userId, "ai.conversations_count", 1);
        achievementEngine.checkMetric(userId, "ai.conversations_count");
        // Capped once per day (dedup'd on the date-derived sourceId) to avoid trivial farming via rapid chat spam.
        xpService.awardXp(userId, xpAiAssistantUsed, "AI_ASSISTANT_USED", "AI_CHAT", LocalDate.now().toString());
    }

    private void handleDailyActive(GamificationEvent event) {
        Long userId = event.userId();
        String today = LocalDate.now().toString();
        xpService.awardXp(userId, xpDailyLogin, "DAILY_LOGIN", "DAILY_LOGIN", today);
        if (!isEnabled(userId)) return;
        if (!Boolean.TRUE.equals(settingsService.getOrDefault(userId).getEnableStreakTracking())) return;
        streakService.recordActivity(userId, "DAILY_ACTIVE", LocalDate.now());
        achievementEngine.checkMetric(userId, "streak.daily_active");
    }

    private void handleBudgetPlanCreated(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        String sourceId = String.valueOf(event.metadata().get("planId"));
        progressService.incrementCounter(userId, "budget.plans_created", 1);
        achievementEngine.checkMetric(userId, "budget.plans_created");
        xpService.awardXp(userId, xpBudgetCreated, "BUDGET_CREATED", "BUDGET_PLAN", sourceId);
    }

    private void handleGoldAssetCreated(GamificationEvent event) {
        Long userId = event.userId();
        if (!isEnabled(userId)) return;
        progressService.incrementCounter(userId, "assets.count", 1);
        achievementEngine.checkMetric(userId, "assets.count");
        achievementEngine.checkMetric(userId, "assets.total_value");
    }

    private boolean isEnabled(Long userId) {
        return settingsService.isEnabled(userId);
    }

    private double numberOf(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Gamification event handling failed (ignored, purely observational): {}", e.getMessage(), e);
        }
    }
}
