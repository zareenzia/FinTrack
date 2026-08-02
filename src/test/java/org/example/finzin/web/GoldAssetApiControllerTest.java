package org.example.finzin.web;

import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.GoldPriceEntity;
import org.example.finzin.repository.GoldPriceRepository;
import org.example.finzin.service.JwtTokenProvider;
import org.example.finzin.service.gold.GoldAssetService;
import org.example.finzin.service.gold.GoldPriceSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GoldAssetApiController.class)
class GoldAssetApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private GoldAssetService assetService;

    @MockitoBean
    private GoldPriceSyncService syncService;

    @MockitoBean
    private GoldPriceRepository priceRepository;

    private GoldAssetEntity asset(Long id) {
        GoldAssetEntity e = new GoldAssetEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setAssetName("Wedding Bangle");
        e.setDescription("Gift");
        e.setGoldType("ORNAMENT");
        e.setPurity("22K");
        e.setWeight(10.0);
        e.setWeightUnit("GRAM");
        e.setPurchaseDate(LocalDate.of(2020, 1, 1));
        e.setPurchasePrice(50000.0);
        e.setCurrentValue(70000.0);
        e.setNotes("note");
        e.setCreatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        e.setUpdatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        return e;
    }

    // ── GET /api/gold/assets ──────────────────────────────────────────────────

    @Test
    void getAssetsReturnsMappedListWithGainLoss() throws Exception {
        when(assetService.getAssetsForUser(USER_ID)).thenReturn(List.of(asset(1L)));

        mockMvc.perform(get("/api/gold/assets").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].assetName").value("Wedding Bangle"))
                .andExpect(jsonPath("$[0].currentValue").value(70000.0))
                .andExpect(jsonPath("$[0].gainLoss").value(20000.0))
                .andExpect(jsonPath("$[0].gainLossPct").value(40.0));
    }

    // ── GET /api/gold/assets/{id} ─────────────────────────────────────────────

    @Test
    void getAssetReturns404WhenNotFound() throws Exception {
        when(assetService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/gold/assets/99").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAssetReturns404WhenOwnedByAnotherUser() throws Exception {
        GoldAssetEntity other = asset(1L);
        other.setUserId(999L);
        when(assetService.findById(1L)).thenReturn(Optional.of(other));

        mockMvc.perform(get("/api/gold/assets/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAssetReturnsAssetWhenOwned() throws Exception {
        when(assetService.findById(1L)).thenReturn(Optional.of(asset(1L)));

        mockMvc.perform(get("/api/gold/assets/1").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.assetName").value("Wedding Bangle"));
    }

    // ── POST /api/gold/assets ─────────────────────────────────────────────────

    @Test
    void createAssetReturnsBadRequestWhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/gold/assets")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weight\":10.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Asset name is required"));
    }

    @Test
    void createAssetReturnsBadRequestWhenWeightNotPositive() throws Exception {
        mockMvc.perform(post("/api/gold/assets")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"Ring\",\"weight\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Weight must be a positive number"));
    }

    @Test
    void createAssetReturnsCreatedOnSuccess() throws Exception {
        when(assetService.createAsset(any(GoldAssetEntity.class))).thenAnswer(inv -> {
            GoldAssetEntity e = inv.getArgument(0);
            e.setId(5L);
            e.setCurrentValue(70000.0);
            e.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            e.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            return e;
        });

        mockMvc.perform(post("/api/gold/assets")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"Ring\",\"weight\":10.0,\"purchasePrice\":50000.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.assetName").value("Ring"))
                .andExpect(jsonPath("$.goldType").value("ORNAMENT"))
                .andExpect(jsonPath("$.purity").value("22K"))
                .andExpect(jsonPath("$.weightUnit").value("GRAM"));
    }

    // ── PUT /api/gold/assets/{id} ─────────────────────────────────────────────

    @Test
    void updateAssetReturns404WhenNotOwned() throws Exception {
        when(assetService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/gold/assets/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"Ring\",\"weight\":10.0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAssetReturnsBadRequestWhenValidationFails() throws Exception {
        when(assetService.findById(1L)).thenReturn(Optional.of(asset(1L)));

        mockMvc.perform(put("/api/gold/assets/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"\",\"weight\":10.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Asset name is required"));
    }

    @Test
    void updateAssetReturnsUpdatedAssetOnSuccess() throws Exception {
        when(assetService.findById(1L)).thenReturn(Optional.of(asset(1L)));
        when(assetService.updateAsset(any(GoldAssetEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/gold/assets/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"Updated Ring\",\"weight\":15.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetName").value("Updated Ring"))
                .andExpect(jsonPath("$.weight").value(15.0));
    }

    // ── DELETE /api/gold/assets/{id} ──────────────────────────────────────────

    @Test
    void deleteAssetReturns404WhenNotOwned() throws Exception {
        when(assetService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/gold/assets/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());

        verify(assetService, never()).deleteAsset(any());
    }

    @Test
    void deleteAssetReturnsNoContentOnSuccess() throws Exception {
        when(assetService.findById(1L)).thenReturn(Optional.of(asset(1L)));

        mockMvc.perform(delete("/api/gold/assets/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(assetService).deleteAsset(1L);
    }

    // ── GET /api/gold/prices/current ──────────────────────────────────────────

    @Test
    void getCurrentPricesReturnsAggregatedResponse() throws Exception {
        GoldPriceEntity price = new GoldPriceEntity();
        price.setUnit("GRAM");
        price.setPurity("22K");
        price.setMarketPrice(12345.0);
        price.setOldSellingPrice(12000.0);
        price.setRetrievedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        when(priceRepository.findLatestBatch()).thenReturn(List.of(price));
        when(assetService.getUserPriceMode(USER_ID)).thenReturn("AUTOMATIC");
        when(syncService.getLastSuccessfulSyncTime()).thenReturn(Optional.of(LocalDateTime.of(2026, 7, 1, 9, 0)));
        when(syncService.getLastSyncError()).thenReturn(null);
        when(syncService.isSyncInProgress()).thenReturn(false);

        mockMvc.perform(get("/api/gold/prices/current").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("AUTOMATIC"))
                .andExpect(jsonPath("$.syncInProgress").value(false))
                .andExpect(jsonPath("$.prices[0].purity").value("22K"))
                .andExpect(jsonPath("$.prices[0].marketPrice").value(12345.0));
    }

    // ── POST /api/gold/prices/sync ────────────────────────────────────────────

    @Test
    void triggerSyncReturnsAlreadyInProgressMessageWithoutStartingAnotherSync() throws Exception {
        when(syncService.isSyncInProgress()).thenReturn(true);

        mockMvc.perform(post("/api/gold/prices/sync").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sync already in progress"))
                .andExpect(jsonPath("$.syncing").value(true));

        verify(syncService, never()).syncPrices();
    }

    @Test
    void triggerSyncStartsSyncWhenNotAlreadyInProgress() throws Exception {
        when(syncService.isSyncInProgress()).thenReturn(false);

        mockMvc.perform(post("/api/gold/prices/sync").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sync started"))
                .andExpect(jsonPath("$.syncing").value(true));

        // syncPrices() runs on a spawned background thread — allow it a moment to execute.
        verify(syncService, timeout(1000)).syncPrices();
    }

    // ── POST /api/gold/prices/mode ────────────────────────────────────────────

    @Test
    void setPriceModeReturnsBadRequestWhenModeMissing() throws Exception {
        mockMvc.perform(post("/api/gold/prices/mode")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mode is required"));
    }

    @Test
    void setPriceModeReturnsBadRequestWhenModeInvalid() throws Exception {
        mockMvc.perform(post("/api/gold/prices/mode")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mode must be AUTOMATIC or MANUAL"));
    }

    @Test
    void setPriceModeSucceedsWithManualMode() throws Exception {
        mockMvc.perform(post("/api/gold/prices/mode")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"manual\",\"manualPricesJson\":\"{\\\"22K\\\":12000}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MANUAL"));

        verify(assetService).setUserPriceMode(eq(USER_ID), eq("MANUAL"), eq("{\"22K\":12000}"));
    }

    // ── GET /api/gold/summary ─────────────────────────────────────────────────

    @Test
    void getSummaryReturnsAggregatedTotals() throws Exception {
        when(assetService.getAssetsForUser(USER_ID)).thenReturn(List.of(asset(1L), asset(2L)));
        when(assetService.getTotalGoldValueForUser(USER_ID)).thenReturn(140000.0);
        when(assetService.getTotalGoldWeightInGrams(USER_ID)).thenReturn(20.0);
        when(assetService.getUserPriceMode(USER_ID)).thenReturn("AUTOMATIC");
        when(syncService.getLastSuccessfulSyncTime()).thenReturn(Optional.empty());
        when(assetService.getPricePerGram(any(), eq("AUTOMATIC"), eq(USER_ID))).thenReturn(7000.0);

        mockMvc.perform(get("/api/gold/summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGoldValue").value(140000.0))
                .andExpect(jsonPath("$.numberOfAssets").value(2))
                .andExpect(jsonPath("$.totalWeightGrams").value(20.0))
                .andExpect(jsonPath("$.priceMode").value("AUTOMATIC"))
                .andExpect(jsonPath("$.pricesPerGram.22K").value(7000.0));
    }

    // ── GET /api/gold/convert-weight ──────────────────────────────────────────

    @Test
    void convertWeightUsesTheRealConverterAndReturnsAllUnits() throws Exception {
        mockMvc.perform(get("/api/gold/convert-weight")
                        .requestAttr("userId", USER_ID)
                        .param("value", "1")
                        .param("unit", "VORI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.value").value(1.0))
                .andExpect(jsonPath("$.input.unit").value("VORI"))
                .andExpect(jsonPath("$.conversions.GRAM").value(11.664));
    }
}
