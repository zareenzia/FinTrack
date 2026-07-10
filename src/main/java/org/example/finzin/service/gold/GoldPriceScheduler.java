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

    /** Runs at fixed rate (default 60 min). Spring converts the property to ms. */
    @Scheduled(fixedRateString = "#{${gold.sync.interval-minutes:60} * 60 * 1000}")
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
