package org.example.finzin.service.gold;

import org.example.finzin.entity.GoldPriceEntity;
import org.example.finzin.repository.GoldPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GoldPriceSyncService {

    private final GoldPriceScraper scraper;
    private final GoldPriceRepository priceRepository;
    private final GoldAssetService goldAssetService;

    private final AtomicReference<String> lastSyncError = new AtomicReference<>(null);
    private volatile LocalDateTime lastSyncAttempt;
    private volatile boolean syncInProgress = false;

    public GoldPriceSyncService(GoldPriceScraper scraper,
                                GoldPriceRepository priceRepository,
                                GoldAssetService goldAssetService) {
        this.scraper          = scraper;
        this.priceRepository  = priceRepository;
        this.goldAssetService = goldAssetService;
    }

    @Transactional
    public void syncPrices() {
        if (syncInProgress) {
            return;
        }
        syncInProgress = true;
        lastSyncAttempt = LocalDateTime.now();

        try {
            List<GoldPriceEntity> fetched = scraper.scrapeCurrentPrices();

            if (fetched == null || fetched.isEmpty()) {
                lastSyncError.set("Scraper returned no prices at " + lastSyncAttempt);
                return;
            }

            priceRepository.saveAll(fetched);
            lastSyncError.set(null);

            // Recalculate all gold asset values with new prices
            goldAssetService.recalculateAllAssets();

        } catch (Exception e) {
            lastSyncError.set("Gold price sync failed: " + e.getMessage());
        } finally {
            syncInProgress = false;
        }
    }

    public Optional<LocalDateTime> getLastSuccessfulSyncTime() {
        return priceRepository.findLatestRetrievedAt();
    }

    public String getLastSyncError() {
        return lastSyncError.get();
    }

    public boolean isSyncInProgress() {
        return syncInProgress;
    }

    public LocalDateTime getLastSyncAttempt() {
        return lastSyncAttempt;
    }
}
