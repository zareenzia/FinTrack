package org.example.finzin.service.gold;

import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.GoldPriceEntity;
import org.example.finzin.entity.GoldPriceSettingEntity;
import org.example.finzin.repository.GoldAssetRepository;
import org.example.finzin.repository.GoldPriceRepository;
import org.example.finzin.repository.GoldPriceSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GoldAssetService {

    private final GoldAssetRepository assetRepository;
    private final GoldPriceRepository priceRepository;
    private final GoldPriceSettingRepository settingRepository;

    public GoldAssetService(GoldAssetRepository assetRepository,
                            GoldPriceRepository priceRepository,
                            GoldPriceSettingRepository settingRepository) {
        this.assetRepository   = assetRepository;
        this.priceRepository   = priceRepository;
        this.settingRepository = settingRepository;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<GoldAssetEntity> getAssetsForUser(Long userId) {
        return assetRepository.findByUserId(userId);
    }

    @Transactional
    public GoldAssetEntity createAsset(GoldAssetEntity asset) {
        GoldAssetEntity saved = assetRepository.save(asset);
        saved.setCurrentValue(calculateValue(saved, getUserPriceMode(saved.getUserId()), saved.getUserId()));
        return assetRepository.save(saved);
    }

    @Transactional
    public GoldAssetEntity updateAsset(GoldAssetEntity asset) {
        asset.setCurrentValue(calculateValue(asset, getUserPriceMode(asset.getUserId()), asset.getUserId()));
        return assetRepository.save(asset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        assetRepository.deleteById(id);
    }

    public Optional<GoldAssetEntity> findById(Long id) {
        return assetRepository.findById(id);
    }

    // ── VALUATION ─────────────────────────────────────────────────────────────

    /**
     * Calculates the current value of a gold asset.
     * Uses price from DB; never calls external services.
     */
    public double calculateValue(GoldAssetEntity asset, String priceMode, Long userId) {
        double weightInGrams = GoldWeightConverter.toGrams(asset.getWeight(), asset.getWeightUnit());
        double pricePerGram = getPricePerGram(asset.getPurity(), priceMode, userId);
        if (pricePerGram <= 0) return 0;
        return weightInGrams * pricePerGram;
    }

    /**
     * Returns the price per gram for the given purity, taking into account the user's price mode.
     */
    public double getPricePerGram(String purity, String priceMode, Long userId) {
        if ("MANUAL".equalsIgnoreCase(priceMode)) {
            Double manual = getManualPricePerGram(purity, userId);
            if (manual != null && manual > 0) return manual;
        }
        // Automatic: get from DB, unit=GRAM
        return getPriceFromDb(purity, "GRAM");
    }

    private double getPriceFromDb(String purity, String unit) {
        String dbPurity = normalizePurityForDb(purity);
        Optional<GoldPriceEntity> opt = priceRepository.findLatestByPurityAndUnit(dbPurity, unit);
        if (opt.isPresent() && opt.get().getMarketPrice() != null) {
            return opt.get().getMarketPrice();
        }
        // Fallback: try VORI price and convert
        if (!"VORI".equals(unit)) {
            Optional<GoldPriceEntity> voriOpt = priceRepository.findLatestByPurityAndUnit(dbPurity, "VORI");
            if (voriOpt.isPresent() && voriOpt.get().getMarketPrice() != null) {
                return voriOpt.get().getMarketPrice() / GoldWeightConverter.GRAMS_PER_VORI;
            }
        }
        return 0;
    }

    private String normalizePurityForDb(String purity) {
        if (purity == null) return "22K";
        return switch (purity.toUpperCase()) {
            case "K22", "22K" -> "22K";
            case "K21", "21K" -> "21K";
            case "K18", "18K" -> "18K";
            case "K24", "24K" -> "24K";
            case "TRADITIONAL" -> "TRADITIONAL";
            default -> "22K";
        };
    }

    @Transactional
    public void recalculateAllAssets() {
        List<GoldAssetEntity> all = assetRepository.findAll();
        for (GoldAssetEntity asset : all) {
            try {
                String mode = getUserPriceMode(asset.getUserId());
                asset.setCurrentValue(calculateValue(asset, mode, asset.getUserId()));
                assetRepository.save(asset);
            } catch (Exception ignored) {}
        }
    }

    // ── PRICE MODE ────────────────────────────────────────────────────────────

    public String getUserPriceMode(Long userId) {
        return settingRepository.findByUserId(userId)
                .map(GoldPriceSettingEntity::getMode)
                .orElse("AUTOMATIC");
    }

    @Transactional
    public void setUserPriceMode(Long userId, String mode, String manualPricesJson) {
        GoldPriceSettingEntity setting = settingRepository.findByUserId(userId)
                .orElseGet(() -> {
                    GoldPriceSettingEntity s = new GoldPriceSettingEntity();
                    s.setUserId(userId);
                    return s;
                });
        setting.setMode(mode.toUpperCase());
        if (manualPricesJson != null) {
            setting.setManualPricesJson(manualPricesJson);
        }
        settingRepository.save(setting);
    }

    private Double getManualPricePerGram(String purity, Long userId) {
        Optional<GoldPriceSettingEntity> opt = settingRepository.findByUserId(userId);
        if (opt.isEmpty() || opt.get().getManualPricesJson() == null) return null;
        try {
            return parseSimpleJsonMap(opt.get().getManualPricesJson())
                    .get(normalizePurityForDb(purity));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Parses a simple flat JSON object: {"22K":12345.0,"21K":11900.0,...}
     * No external library required.
     */
    private static Map<String, Double> parseSimpleJsonMap(String json) {
        Map<String, Double> result = new HashMap<>();
        if (json == null || json.isBlank()) return result;
        // Strip outer braces and split on commas
        String stripped = json.trim().replaceAll("^\\{|\\}$", "");
        for (String pair : stripped.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().replaceAll("\"", "");
            String val = kv[1].trim().replaceAll("\"", "");
            try { result.put(key, Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // ── SUMMARY ───────────────────────────────────────────────────────────────

    public double getTotalGoldValueForUser(Long userId) {
        Double sum = assetRepository.sumCurrentValueByUserId(userId);
        return sum == null ? 0 : sum;
    }

    public double getTotalGoldWeightInGrams(Long userId) {
        Double sum = assetRepository.sumWeightInGramsByUserId(userId);
        return sum == null ? 0 : sum;
    }
}
