package org.example.finzin.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily backstop for recurring transactions; per-login catch-up in AuthController covers the common case. */
@Component
public class RecurringTransactionScheduler {

    private final RecurringTransactionExecutionService executionService;

    public RecurringTransactionScheduler(RecurringTransactionExecutionService executionService) {
        this.executionService = executionService;
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void runDailyProcessing() {
        executionService.processAllDue();
        executionService.sendUpcomingReminders();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        new Thread(() -> {
            executionService.processAllDue();
            executionService.sendUpcomingReminders();
        }, "recurring-tx-init-sync").start();
    }
}
