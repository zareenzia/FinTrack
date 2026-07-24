package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.GoldPriceEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.gamification.GamificationEventType;
import org.example.finzin.repository.GoldPriceRepository;
import org.example.finzin.service.gold.GoldAssetService;
import org.example.finzin.service.gold.GoldPriceSyncService;
import org.example.finzin.service.gold.GoldWeightConverter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/gold")
public class GoldAssetApiController {

    private final GoldAssetService assetService;
    private final GoldPriceSyncService syncService;
    private final GoldPriceRepository priceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GoldAssetApiController(GoldAssetService assetService,
                                  GoldPriceSyncService syncService,
                                  GoldPriceRepository priceRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.assetService    = assetService;
        this.syncService     = syncService;
        this.priceRepository = priceRepository;
        this.eventPublisher  = eventPublisher;
    }

    private Long getUserId(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        return id != null ? (Long) id : 1L;
    }

    // ── ASSETS ────────────────────────────────────────────────────────────────

    @GetMapping("/assets")
    public List<Map<String, Object>> getAssets(HttpServletRequest request) {
        Long userId = getUserId(request);
        return assetService.getAssetsForUser(userId).stream()
                .map(a -> toAssetResponse(a, userId))
                .collect(Collectors.toList());
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<?> getAsset(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        Optional<GoldAssetEntity> opt = assetService.findById(id);
        if (opt.isEmpty() || !opt.get().getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toAssetResponse(opt.get(), userId));
    }

    @PostMapping("/assets")
    public ResponseEntity<?> createAsset(HttpServletRequest request, @RequestBody GoldAssetRequest body) {
        Long userId = getUserId(request);
        ResponseEntity<?> validation = validateAssetRequest(body);
        if (validation != null) return validation;

        GoldAssetEntity entity = fromRequest(body, userId);
        GoldAssetEntity saved = assetService.createAsset(entity);
        eventPublisher.publishEvent(new GamificationEvent(userId, GamificationEventType.GOLD_ASSET_CREATED,
                Map.of("assetId", saved.getId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toAssetResponse(saved, userId));
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<?> updateAsset(HttpServletRequest request, @PathVariable Long id, @RequestBody GoldAssetRequest body) {
        Long userId = getUserId(request);
        Optional<GoldAssetEntity> opt = assetService.findById(id);
        if (opt.isEmpty() || !opt.get().getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<?> validation = validateAssetRequest(body);
        if (validation != null) return validation;

        GoldAssetEntity entity = opt.get();
        applyRequest(body, entity);
        GoldAssetEntity updated = assetService.updateAsset(entity);
        return ResponseEntity.ok(toAssetResponse(updated, userId));
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<?> deleteAsset(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        Optional<GoldAssetEntity> opt = assetService.findById(id);
        if (opt.isEmpty() || !opt.get().getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        assetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    // ── PRICES ────────────────────────────────────────────────────────────────

    @GetMapping("/prices/current")
    public ResponseEntity<?> getCurrentPrices(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<GoldPriceEntity> latest = priceRepository.findLatestBatch();
        String mode = assetService.getUserPriceMode(userId);
        Optional<java.time.LocalDateTime> lastSync = syncService.getLastSuccessfulSyncTime();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode", mode);
        response.put("lastSyncTime", lastSync.map(Object::toString).orElse(null));
        response.put("lastSyncError", syncService.getLastSyncError());
        response.put("syncInProgress", syncService.isSyncInProgress());
        response.put("sourceUrl", "https://www.goldr.org/gold-calculator/");

        List<Map<String, Object>> priceList = latest.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("purity", p.getPurity());
            m.put("unit", p.getUnit());
            m.put("marketPrice", p.getMarketPrice());
            m.put("oldSellingPrice", p.getOldSellingPrice());
            m.put("retrievedAt", p.getRetrievedAt().toString());
            return m;
        }).collect(Collectors.toList());
        response.put("prices", priceList);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/prices/sync")
    public ResponseEntity<?> triggerSync(HttpServletRequest request) {
        if (syncService.isSyncInProgress()) {
            return ResponseEntity.ok(Map.of("message", "Sync already in progress", "syncing", true));
        }
        new Thread(() -> syncService.syncPrices(), "gold-manual-sync").start();
        return ResponseEntity.ok(Map.of("message", "Sync started", "syncing", true));
    }

    @PostMapping("/prices/mode")
    public ResponseEntity<?> setPriceMode(HttpServletRequest request, @RequestBody PriceModeRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.mode() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "mode is required"));
        }
        String mode = body.mode().toUpperCase();
        if (!mode.equals("AUTOMATIC") && !mode.equals("MANUAL")) {
            return ResponseEntity.badRequest().body(Map.of("error", "mode must be AUTOMATIC or MANUAL"));
        }
        assetService.setUserPriceMode(userId, mode, body.manualPricesJson());
        return ResponseEntity.ok(Map.of("mode", mode));
    }

    // ── SUMMARY ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    public Map<String, Object> getSummary(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<GoldAssetEntity> assets = assetService.getAssetsForUser(userId);
        double totalValue = assetService.getTotalGoldValueForUser(userId);
        double totalWeightGrams = assetService.getTotalGoldWeightInGrams(userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalGoldValue", totalValue);
        response.put("numberOfAssets", assets.size());
        response.put("totalWeightGrams", totalWeightGrams);
        response.put("totalWeightVori", GoldWeightConverter.fromGrams(totalWeightGrams, "VORI"));
        response.put("priceMode", assetService.getUserPriceMode(userId));
        response.put("lastSyncTime", syncService.getLastSuccessfulSyncTime().map(Object::toString).orElse(null));

        String mode = assetService.getUserPriceMode(userId);
        Map<String, Double> pricesPerGram = new LinkedHashMap<>();
        for (String purity : new String[]{"22K", "21K", "18K", "24K", "TRADITIONAL"}) {
            pricesPerGram.put(purity, assetService.getPricePerGram(purity, mode, userId));
        }
        response.put("pricesPerGram", pricesPerGram);
        return response;
    }

    // ── WEIGHT CONVERSION UTILITY ──────────────────────────────────────────────

    @GetMapping("/convert-weight")
    public Map<String, Object> convertWeight(
            @RequestParam double value,
            @RequestParam String unit) {
        Map<String, Double> converted = GoldWeightConverter.convertAll(value, unit);
        return Map.of("input", Map.of("value", value, "unit", unit), "conversions", converted);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private Map<String, Object> toAssetResponse(GoldAssetEntity a, Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("userId", a.getUserId());
        m.put("assetName", a.getAssetName());
        m.put("description", a.getDescription());
        m.put("goldType", a.getGoldType());
        m.put("purity", a.getPurity());
        m.put("weight", a.getWeight());
        m.put("weightUnit", a.getWeightUnit());
        m.put("weightConversions", GoldWeightConverter.convertAll(a.getWeight(), a.getWeightUnit()));
        m.put("purchaseDate", a.getPurchaseDate() != null ? a.getPurchaseDate().toString() : null);
        m.put("purchasePrice", a.getPurchasePrice());
        m.put("currentValue", a.getCurrentValue());
        double purchasePrice = a.getPurchasePrice() != null ? a.getPurchasePrice() : 0;
        double currentValue  = a.getCurrentValue()  != null ? a.getCurrentValue()  : 0;
        m.put("gainLoss", currentValue - purchasePrice);
        m.put("gainLossPct", purchasePrice > 0 ? ((currentValue - purchasePrice) / purchasePrice) * 100 : 0);
        m.put("notes", a.getNotes());
        m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        m.put("updatedAt", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        return m;
    }

    private GoldAssetEntity fromRequest(GoldAssetRequest body, Long userId) {
        GoldAssetEntity e = new GoldAssetEntity();
        e.setUserId(userId);
        applyRequest(body, e);
        return e;
    }

    private void applyRequest(GoldAssetRequest body, GoldAssetEntity e) {
        e.setAssetName(body.assetName().trim());
        e.setDescription(body.description());
        e.setGoldType(body.goldType() != null ? body.goldType() : "ORNAMENT");
        e.setPurity(body.purity() != null ? body.purity() : "22K");
        e.setWeight(body.weight());
        e.setWeightUnit(body.weightUnit() != null ? body.weightUnit() : "GRAM");
        if (body.purchaseDate() != null && !body.purchaseDate().isBlank()) {
            try { e.setPurchaseDate(LocalDate.parse(body.purchaseDate())); } catch (Exception ignored) {}
        }
        e.setPurchasePrice(body.purchasePrice());
        e.setNotes(body.notes());
    }

    private ResponseEntity<?> validateAssetRequest(GoldAssetRequest body) {
        if (body == null || body.assetName() == null || body.assetName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asset name is required"));
        }
        if (body.weight() == null || body.weight() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Weight must be a positive number"));
        }
        return null;
    }

    // ── INNER RECORDS ─────────────────────────────────────────────────────────

    private record GoldAssetRequest(
            String assetName,
            String description,
            String goldType,
            String purity,
            Double weight,
            String weightUnit,
            String purchaseDate,
            Double purchasePrice,
            String notes
    ) {}

    private record PriceModeRequest(
            String mode,
            String manualPricesJson
    ) {}
}
