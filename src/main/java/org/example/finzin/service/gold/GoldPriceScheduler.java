package org.example.finzin.service.gold;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GoldPriceScheduler {

    @Value("${gold.sync.enabled:true}")
    private boolean syncEnabled;

    private final GoldPriceSyncService syncService;

    public GoldPriceScheduler(GoldPriceSyncService syncService) {
        this.syncService = syncService;
    }

    /** Runs weekly, once every Sunday at 11:11 AM (configurable via gold.sync.cron). */
    @Scheduled(cron = "${gold.sync.cron:0 11 11 * * SUN}")
    public void scheduledSync() {
        if (syncEnabled) {
            syncService.syncPrices();
        }
    }

    /** Perform an initial sync after app starts so prices are available immediately. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (syncEnabled) {
            new Thread(() -> syncService.syncPrices(), "gold-price-init-sync").start();
        }
    }
}
