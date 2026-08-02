package org.example.finzin.service.gold;

import org.example.finzin.entity.GoldPriceEntity;
import org.example.finzin.repository.GoldPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for GoldPriceSyncService. {@link GoldPriceScraper} is mocked throughout
 * (as {@code AbstractApiIntegrationTest} does for the HTTP-level integration tests) so this never
 * makes a real network call to goldr.org — it only exercises the sync orchestration: persistence
 * of newly fetched prices, recalculation triggering, and the various error/empty-result paths.
 */
@ExtendWith(MockitoExtension.class)
class GoldPriceSyncServiceTest {

    @Mock private GoldPriceScraper scraper;
    @Mock private GoldPriceRepository priceRepository;
    @Mock private GoldAssetService goldAssetService;

    private GoldPriceSyncService service;

    @BeforeEach
    void setUp() {
        service = new GoldPriceSyncService(scraper, priceRepository, goldAssetService);
    }

    private GoldPriceEntity price(String purity, String unit, double marketPrice) {
        GoldPriceEntity p = new GoldPriceEntity();
        p.setPurity(purity);
        p.setUnit(unit);
        p.setMarketPrice(marketPrice);
        p.setSource("goldr.org");
        p.setRetrievedAt(LocalDateTime.now());
        return p;
    }

    private void setSyncInProgress(boolean value) throws Exception {
        Field f = GoldPriceSyncService.class.getDeclaredField("syncInProgress");
        f.setAccessible(true);
        f.set(service, value);
    }

    // ================================================================================
    // syncPrices — happy path
    // ================================================================================

    @Test
    void syncPricesSavesFetchedPricesClearsErrorAndRecalculatesAssets() throws Exception {
        List<GoldPriceEntity> fetched = List.of(price("22K", "GRAM", 1000.0), price("21K", "GRAM", 950.0));
        when(scraper.scrapeCurrentPrices()).thenReturn(fetched);

        service.syncPrices();

        verify(priceRepository).saveAll(fetched);
        verify(goldAssetService).recalculateAllAssets();
        assertNull(service.getLastSyncError());
        assertNotNull(service.getLastSyncAttempt());
        assertFalse(service.isSyncInProgress(), "syncInProgress must be reset once the sync finishes");
    }

    // ================================================================================
    // syncPrices — empty/null scraper result
    // ================================================================================

    @Test
    void syncPricesRecordsErrorAndSkipsPersistenceWhenScraperReturnsEmptyList() throws Exception {
        when(scraper.scrapeCurrentPrices()).thenReturn(List.of());

        service.syncPrices();

        verify(priceRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(goldAssetService, never()).recalculateAllAssets();
        assertNotNull(service.getLastSyncError());
        assertTrue(service.getLastSyncError().contains("no prices"));
        assertFalse(service.isSyncInProgress());
    }

    @Test
    void syncPricesRecordsErrorAndSkipsPersistenceWhenScraperReturnsNull() throws Exception {
        when(scraper.scrapeCurrentPrices()).thenReturn(null);

        service.syncPrices();

        verify(priceRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(goldAssetService, never()).recalculateAllAssets();
        assertNotNull(service.getLastSyncError());
        assertFalse(service.isSyncInProgress());
    }

    // ================================================================================
    // syncPrices — scraper throws
    // ================================================================================

    @Test
    void syncPricesRecordsErrorMessageAndResetsFlagWhenScraperThrows() throws Exception {
        when(scraper.scrapeCurrentPrices()).thenThrow(new RuntimeException("goldr.org unreachable"));

        service.syncPrices();

        verify(priceRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(goldAssetService, never()).recalculateAllAssets();
        assertNotNull(service.getLastSyncError());
        assertTrue(service.getLastSyncError().contains("Gold price sync failed"));
        assertTrue(service.getLastSyncError().contains("goldr.org unreachable"));
        assertFalse(service.isSyncInProgress(), "the finally block must reset syncInProgress even on failure");
    }

    @Test
    void syncPricesRecordsErrorWhenRecalculateAllAssetsThrows() throws Exception {
        when(scraper.scrapeCurrentPrices()).thenReturn(List.of(price("22K", "GRAM", 1000.0)));
        org.mockito.Mockito.doThrow(new RuntimeException("recalculation failed")).when(goldAssetService).recalculateAllAssets();

        service.syncPrices();

        assertNotNull(service.getLastSyncError());
        assertTrue(service.getLastSyncError().contains("recalculation failed"));
        assertFalse(service.isSyncInProgress());
    }

    // ================================================================================
    // syncPrices — reentrancy guard
    // ================================================================================

    @Test
    void syncPricesDoesNothingWhenAlreadyInProgress() throws Exception {
        setSyncInProgress(true);

        service.syncPrices();

        verify(scraper, never()).scrapeCurrentPrices();
        verify(priceRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    // ================================================================================
    // Delegating getters
    // ================================================================================

    @Test
    void getLastSuccessfulSyncTimeDelegatesToRepository() {
        LocalDateTime now = LocalDateTime.now();
        when(priceRepository.findLatestRetrievedAt()).thenReturn(Optional.of(now));

        assertEquals(Optional.of(now), service.getLastSuccessfulSyncTime());
    }

    @Test
    void isSyncInProgressReflectsInternalStateBeforeAnySync() {
        assertFalse(service.isSyncInProgress());
    }
}
