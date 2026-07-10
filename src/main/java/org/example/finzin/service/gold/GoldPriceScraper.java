package org.example.finzin.service.gold;

import org.example.finzin.entity.GoldPriceEntity;
import java.util.List;

/**
 * Strategy interface for gold price providers.
 * The default implementation scrapes goldr.org.
 * Swap this interface's implementation to change data source without touching business logic.
 */
public interface GoldPriceScraper {
    /**
     * Fetches the latest gold prices.
     * @return list of price records (not yet persisted)
     * @throws Exception if the data source is unreachable or parsing fails
     */
    List<GoldPriceEntity> scrapeCurrentPrices() throws Exception;
}
