package org.example.finzin.receipts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Deletes every receipt (image file + row) older than the retention window, confirmed or not, so
 * scanned receipts never accumulate DB/disk storage indefinitely.
 */
@Component
public class ReceiptCleanupScheduler {

    @Value("${app.receipts.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${app.receipts.retention-hours:48}")
    private int retentionHours;

    private final ReceiptService receiptService;

    public ReceiptCleanupScheduler(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    /** Runs hourly so actual retention stays close to the configured window, not up to a day past it. */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledCleanup() {
        if (cleanupEnabled) {
            receiptService.cleanupExpiredReceipts(LocalDateTime.now().minusHours(retentionHours));
        }
    }

    /** Catches up on anything that piled up while the app was down, same as the other schedulers. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (cleanupEnabled) {
            new Thread(() -> receiptService.cleanupExpiredReceipts(LocalDateTime.now().minusHours(retentionHours)),
                    "receipt-cleanup-init-sync").start();
        }
    }
}
