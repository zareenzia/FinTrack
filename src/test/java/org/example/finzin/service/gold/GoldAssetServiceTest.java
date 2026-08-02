package org.example.finzin.service.gold;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.GoldPriceEntity;
import org.example.finzin.entity.GoldPriceSettingEntity;
import org.example.finzin.repository.GoldAssetRepository;
import org.example.finzin.repository.GoldPriceRepository;
import org.example.finzin.repository.GoldPriceSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching BudgetPlanServiceTest's convention) for GoldAssetService:
 * CRUD + valuation (purity normalization, manual/automatic price mode, VORI->GRAM fallback),
 * per-user price mode settings, bulk recalculation error isolation, and the aggregate summaries.
 */
@ExtendWith(MockitoExtension.class)
class GoldAssetServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private GoldAssetRepository assetRepository;
    @Mock private GoldPriceRepository priceRepository;
    @Mock private GoldPriceSettingRepository settingRepository;
    @Mock private DocumentIndexer documentIndexer;

    private GoldAssetService service;

    @BeforeEach
    void setUp() {
        service = new GoldAssetService(assetRepository, priceRepository, settingRepository, documentIndexer);
    }

    private GoldAssetEntity asset(Long id, String purity, double weight, String unit) {
        GoldAssetEntity a = new GoldAssetEntity();
        a.setId(id);
        a.setUserId(USER_ID);
        a.setAssetName("Necklace");
        a.setGoldType("ORNAMENT");
        a.setPurity(purity);
        a.setWeight(weight);
        a.setWeightUnit(unit);
        return a;
    }

    private GoldPriceEntity price(Double marketPrice) {
        GoldPriceEntity p = new GoldPriceEntity();
        p.setMarketPrice(marketPrice);
        return p;
    }

    // ================================================================================
    // getAssetsForUser / findById
    // ================================================================================

    @Test
    void getAssetsForUserDelegatesToRepository() {
        List<GoldAssetEntity> assets = List.of(asset(1L, "22K", 10, "GRAM"));
        when(assetRepository.findByUserId(USER_ID)).thenReturn(assets);

        assertSame(assets, service.getAssetsForUser(USER_ID));
    }

    @Test
    void findByIdDelegatesToRepository() {
        GoldAssetEntity a = asset(1L, "22K", 10, "GRAM");
        when(assetRepository.findById(1L)).thenReturn(Optional.of(a));

        assertSame(a, service.findById(1L).get());
    }

    // ================================================================================
    // createAsset / updateAsset / deleteAsset
    // ================================================================================

    @Test
    void createAssetComputesValueSavesTwiceAndIndexes() {
        GoldAssetEntity a = asset(null, "22K", 10, "GRAM");
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.of(price(1000.0)));

        GoldAssetEntity result = service.createAsset(a);

        assertEquals(10000.0, result.getCurrentValue());
        verify(assetRepository, times(2)).save(any());
        verify(documentIndexer).indexGoldAsset(result);
    }

    @Test
    void updateAssetRecomputesValueSavesOnceAndIndexes() {
        GoldAssetEntity a = asset(1L, "22K", 5, "GRAM");
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.of(price(2000.0)));
        when(assetRepository.save(a)).thenReturn(a);

        GoldAssetEntity result = service.updateAsset(a);

        assertEquals(10000.0, result.getCurrentValue());
        verify(assetRepository, times(1)).save(a);
        verify(documentIndexer).indexGoldAsset(a);
    }

    @Test
    void deleteAssetDeindexesThenDeletesWhenFound() {
        GoldAssetEntity a = asset(5L, "22K", 1, "GRAM");
        when(assetRepository.findById(5L)).thenReturn(Optional.of(a));

        service.deleteAsset(5L);

        verify(documentIndexer).deleteGoldAsset(USER_ID, 5L);
        verify(assetRepository).deleteById(5L);
    }

    @Test
    void deleteAssetSkipsDeindexingWhenNotFound() {
        when(assetRepository.findById(5L)).thenReturn(Optional.empty());

        service.deleteAsset(5L);

        verifyNoInteractions(documentIndexer);
        verify(assetRepository).deleteById(5L);
    }

    // ================================================================================
    // calculateValue / getPricePerGram
    // ================================================================================

    @Test
    void calculateValueMultipliesWeightInGramsByPricePerGram() {
        GoldAssetEntity a = asset(1L, "22K", 2, "VORI");
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.of(price(1000.0)));

        double value = service.calculateValue(a, "AUTOMATIC", USER_ID);

        assertEquals(2 * 11.664 * 1000.0, value, 0.0001);
    }

    @Test
    void calculateValueReturnsZeroWhenPricePerGramIsZeroOrNegative() {
        GoldAssetEntity a = asset(1L, "22K", 5, "GRAM");
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.empty());
        when(priceRepository.findLatestByPurityAndUnit("22K", "VORI")).thenReturn(Optional.empty());

        assertEquals(0, service.calculateValue(a, "AUTOMATIC", USER_ID));
    }

    @Test
    void getPricePerGramUsesManualPriceWhenModeManualAndValueIsPositive() {
        GoldPriceSettingEntity setting = new GoldPriceSettingEntity();
        setting.setUserId(USER_ID);
        setting.setManualPricesJson("{\"22K\":12345.0,\"21K\":11900.0}");
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

        double result = service.getPricePerGram("22K", "MANUAL", USER_ID);

        assertEquals(12345.0, result);
        verify(priceRepository, never()).findLatestByPurityAndUnit(any(), any());
    }

    @Test
    void getPricePerGramFallsBackToAutomaticWhenManualModeHasNoManualPrice() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.of(price(999.0)));

        double result = service.getPricePerGram("22K", "MANUAL", USER_ID);

        assertEquals(999.0, result);
    }

    @Test
    void getPricePerGramFallsBackToAutomaticWhenManualPriceIsZeroOrNegative() {
        GoldPriceSettingEntity setting = new GoldPriceSettingEntity();
        setting.setUserId(USER_ID);
        setting.setManualPricesJson("{\"22K\":0.0}");
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.of(price(500.0)));

        double result = service.getPricePerGram("22K", "MANUAL", USER_ID);

        assertEquals(500.0, result);
    }

    @Test
    void getPricePerGramFallsBackToVoriConvertedToGramWhenGramPriceMissing() {
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.empty());
        when(priceRepository.findLatestByPurityAndUnit("22K", "VORI")).thenReturn(Optional.of(price(11664.0)));

        double result = service.getPricePerGram("22K", "AUTOMATIC", USER_ID);

        assertEquals(1000.0, result, 0.0001, "11664/vori must convert down to 1000/gram");
    }

    @Test
    void getPricePerGramNormalizesPurityAliasesToCanonicalDbForm() {
        when(priceRepository.findLatestByPurityAndUnit("21K", "GRAM")).thenReturn(Optional.of(price(800.0)));

        double result = service.getPricePerGram("K21", "AUTOMATIC", USER_ID);

        assertEquals(800.0, result);
        verify(priceRepository).findLatestByPurityAndUnit("21K", "GRAM");
    }

    @Test
    void getPricePerGramDefaultsUnknownOrNullPurityTo22K() {
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.of(price(700.0)));

        assertEquals(700.0, service.getPricePerGram(null, "AUTOMATIC", USER_ID));
        assertEquals(700.0, service.getPricePerGram("BOGUS", "AUTOMATIC", USER_ID));
    }

    // ================================================================================
    // recalculateAllAssets
    // ================================================================================

    @Test
    void recalculateAllAssetsContinuesPastPerAssetFailures() {
        GoldAssetEntity ok = asset(1L, "22K", 1, "GRAM");
        GoldAssetEntity failing = asset(2L, "22K", 1, "GRAM");
        failing.setUserId(999L);
        when(assetRepository.findAll()).thenReturn(List.of(ok, failing));
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(settingRepository.findByUserId(999L)).thenThrow(new RuntimeException("boom"));
        when(priceRepository.findLatestByPurityAndUnit("22K", "GRAM")).thenReturn(Optional.of(price(1000.0)));

        service.recalculateAllAssets();

        assertEquals(1000.0, ok.getCurrentValue());
        verify(assetRepository, times(1)).save(ok);
        verify(assetRepository, never()).save(failing);
    }

    // ================================================================================
    // getUserPriceMode / setUserPriceMode
    // ================================================================================

    @Test
    void getUserPriceModeDefaultsToAutomaticWhenNoSettingExists() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertEquals("AUTOMATIC", service.getUserPriceMode(USER_ID));
    }

    @Test
    void getUserPriceModeReturnsStoredModeWhenPresent() {
        GoldPriceSettingEntity setting = new GoldPriceSettingEntity();
        setting.setMode("MANUAL");
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

        assertEquals("MANUAL", service.getUserPriceMode(USER_ID));
    }

    @Test
    void setUserPriceModeCreatesNewSettingWhenNoneExists() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<GoldPriceSettingEntity> captor = ArgumentCaptor.forClass(GoldPriceSettingEntity.class);

        service.setUserPriceMode(USER_ID, "manual", "{\"22K\":100.0}");

        verify(settingRepository).save(captor.capture());
        GoldPriceSettingEntity saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals("MANUAL", saved.getMode(), "mode must be upper-cased");
        assertEquals("{\"22K\":100.0}", saved.getManualPricesJson());
    }

    @Test
    void setUserPriceModeKeepsExistingManualPricesJsonWhenArgumentIsNull() {
        GoldPriceSettingEntity existing = new GoldPriceSettingEntity();
        existing.setUserId(USER_ID);
        existing.setManualPricesJson("{\"22K\":100.0}");
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        service.setUserPriceMode(USER_ID, "AUTOMATIC", null);

        assertEquals("{\"22K\":100.0}", existing.getManualPricesJson(), "null manualPricesJson must not overwrite existing data");
        assertEquals("AUTOMATIC", existing.getMode());
        verify(settingRepository).save(existing);
    }

    // ================================================================================
    // getTotalGoldValueForUser / getTotalGoldWeightInGrams
    // ================================================================================

    @Test
    void getTotalGoldValueForUserDefaultsToZeroWhenSumIsNull() {
        when(assetRepository.sumCurrentValueByUserId(USER_ID)).thenReturn(null);
        assertEquals(0, service.getTotalGoldValueForUser(USER_ID));
    }

    @Test
    void getTotalGoldValueForUserReturnsSumWhenPresent() {
        when(assetRepository.sumCurrentValueByUserId(USER_ID)).thenReturn(5000.0);
        assertEquals(5000.0, service.getTotalGoldValueForUser(USER_ID));
    }

    @Test
    void getTotalGoldWeightInGramsDefaultsToZeroWhenSumIsNull() {
        when(assetRepository.sumWeightInGramsByUserId(USER_ID)).thenReturn(null);
        assertEquals(0, service.getTotalGoldWeightInGrams(USER_ID));
    }

    @Test
    void getTotalGoldWeightInGramsReturnsSumWhenPresent() {
        when(assetRepository.sumWeightInGramsByUserId(USER_ID)).thenReturn(123.45);
        assertEquals(123.45, service.getTotalGoldWeightInGrams(USER_ID));
    }
}
