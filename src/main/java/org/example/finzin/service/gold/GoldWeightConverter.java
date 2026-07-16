package org.example.finzin.service.gold;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts gold weights between Bangladeshi measurement units.
 * <br>
 * Reference: 1 Vori = 11.664 grams (standard)
 * 1 Ana  = 1/16 Vori = 0.729 grams
 * 1 Rati = 1/96 Vori = 0.12150 grams
 * 1 Point = 1/480 Vori = 0.02430 grams
 */
public final class GoldWeightConverter {

    private GoldWeightConverter() {}

    // Grams per unit
    public static final double GRAMS_PER_VORI  = 11.664;
    public static final double GRAMS_PER_ANA   = GRAMS_PER_VORI / 16.0;   // 0.729
    public static final double GRAMS_PER_RATI  = GRAMS_PER_VORI / 96.0;   // 0.12150
    public static final double GRAMS_PER_POINT = GRAMS_PER_VORI / 480.0;  // 0.02430

    /**
     * Converts any supported unit to grams.
     * @param value amount in the given unit
     * @param unit  GRAM | VORI | ANA | RATI | POINT (case-insensitive)
     */
    public static double toGrams(double value, String unit) {
        if (unit == null) return value;
        return switch (unit.toUpperCase()) {
            case "GRAM"  -> value;
            case "VORI"  -> value * GRAMS_PER_VORI;
            case "ANA"   -> value * GRAMS_PER_ANA;
            case "RATI"  -> value * GRAMS_PER_RATI;
            case "POINT" -> value * GRAMS_PER_POINT;
            default      -> value; // treat unknown as grams
        };
    }

    /** Converts grams to the target unit. */
    public static double fromGrams(double grams, String targetUnit) {
        if (targetUnit == null) return grams;
        return switch (targetUnit.toUpperCase()) {
            case "GRAM"  -> grams;
            case "VORI"  -> grams / GRAMS_PER_VORI;
            case "ANA"   -> grams / GRAMS_PER_ANA;
            case "RATI"  -> grams / GRAMS_PER_RATI;
            case "POINT" -> grams / GRAMS_PER_POINT;
            default      -> grams;
        };
    }

    /**
     * Returns all conversions of the given weight as a map.
     * Keys: GRAM, VORI, ANA, RATI, POINT
     */
    public static Map<String, Double> convertAll(double value, String unit) {
        double grams = toGrams(value, unit);
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("GRAM",  round6(grams));
        result.put("VORI",  round6(grams / GRAMS_PER_VORI));
        result.put("ANA",   round6(grams / GRAMS_PER_ANA));
        result.put("RATI",  round6(grams / GRAMS_PER_RATI));
        result.put("POINT", round6(grams / GRAMS_PER_POINT));
        return result;
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
