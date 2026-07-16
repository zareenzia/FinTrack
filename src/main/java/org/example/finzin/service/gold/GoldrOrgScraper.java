package org.example.finzin.service.gold;

import org.example.finzin.entity.GoldPriceEntity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes gold prices from https://www.goldr.org/
 *
 * Page structure:
 *   div#table-gram  → first table.metal-table → gold prices per gram
 *   div#table-vori  → first table.metal-table → gold prices per vori
 *   div#table-ana   → first table.metal-table → gold prices per ana
 *   div#table-rati  → first table.metal-table → gold prices per rati
 *
 * Each table row: td[0]=purity label, td[1]=market price <strong>, td[2]=old selling price <strong>
 * Prices may use Bangla digits (০-৯) and commas — both are handled.
 */
@Component
public class GoldrOrgScraper implements GoldPriceScraper {

    @Value("${gold.source.url:https://www.goldr.org/}")
    private String sourceUrl;

    @Value("${gold.sync.timeout-seconds:10}")
    private int timeoutSeconds;

    /** Maps div IDs to their unit names */
    private static final String[][] UNIT_DIV_MAP = {
        {"table-gram", "GRAM"},
        {"table-vori", "VORI"},
        {"table-ana",  "ANA"},
        {"table-rati", "RATI"}
    };

    @Override
    public List<GoldPriceEntity> scrapeCurrentPrices() throws Exception {
        List<GoldPriceEntity> prices = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        Document doc = Jsoup.connect(sourceUrl)
                .timeout(timeoutSeconds * 1000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,bn;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get();

        for (String[] entry : UNIT_DIV_MAP) {
            String divId = entry[0];
            String unit  = entry[1];

            Element container = doc.getElementById(divId);
            if (container == null) continue;

            // First metal-table inside the container is the gold table (second is silver)
            Element goldTable = container.selectFirst("table.metal-table");
            if (goldTable == null) continue;

            // Skip silver tables: caption contains "রুপা" or "silver"
            Element caption = goldTable.selectFirst("caption");
            if (caption != null) {
                String cap = caption.text().toLowerCase();
                if (cap.contains("silver") || cap.contains("রুপা") || cap.contains("চান্দি")) continue;
            }

            Elements rows = goldTable.select("tbody tr");
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 2) continue;

                String purityLabel = cells.get(0).text().trim();
                String purity = normalizePurity(purityLabel);
                if (purity == null) continue;

                Double marketPrice    = extractPriceFromCell(cells.get(1));
                Double oldSellingPrice = cells.size() >= 3 ? extractPriceFromCell(cells.get(2)) : null;

                if (marketPrice == null || marketPrice <= 0) continue;

                GoldPriceEntity entity = new GoldPriceEntity();
                entity.setPurity(purity);
                entity.setUnit(unit);
                entity.setMarketPrice(marketPrice);
                entity.setOldSellingPrice(oldSellingPrice);
                entity.setSource(sourceUrl);
                entity.setRetrievedAt(now);
                prices.add(entity);
            }
        }

        return prices;
    }

    /**
     * Extracts the price from a table cell.
     * Looks for text inside <strong> first, falls back to full cell text.
     * Converts Bangla digits and strips non-numeric characters.
     */
    private static Double extractPriceFromCell(Element cell) {
        Element strong = cell.selectFirst("strong");
        String raw = strong != null ? strong.text() : cell.text();
        return parsePrice(raw);
    }

    private static Double parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String cleaned = convertBanglaDigits(raw)
                    .replaceAll("[^0-9.]", "")
                    .trim();
            if (cleaned.isEmpty()) return null;
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Converts Bangla/Bengali digits (০-৯) to ASCII digits (0-9). */
    private static String convertBanglaDigits(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= '\u09E6' && c <= '\u09EF') {
                sb.append((char) ('0' + (c - '\u09E6')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalizePurity(String text) {
        if (text == null) return null;
        String t = text.toLowerCase().trim();
        if (t.contains("22")) return "22K";
        if (t.contains("21")) return "21K";
        if (t.contains("18")) return "18K";
        if (t.contains("24")) return "24K";
        if (t.contains("traditional") || t.contains("sona") || t.contains("sonaton")) return "TRADITIONAL";
        return null;
    }
}

