package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.GoldPriceEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GoldPriceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GoldPriceRepository repository;

    private GoldPriceEntity price(String purity, String unit, double marketPrice, LocalDateTime retrievedAt) {
        GoldPriceEntity p = new GoldPriceEntity();
        p.setPurity(purity);
        p.setUnit(unit);
        p.setMarketPrice(marketPrice);
        p.setSource("bajus");
        p.setRetrievedAt(retrievedAt);
        return p;
    }

    @Test
    void findLatestByPurityAndUnitReturnsMostRecentMatchingRow() {
        entityManager.persistAndFlush(price("22K", "GRAM", 10000.0, LocalDateTime.of(2026, 1, 1, 0, 0)));
        GoldPriceEntity latest = entityManager.persistAndFlush(
                price("22K", "GRAM", 10500.0, LocalDateTime.of(2026, 2, 1, 0, 0)));
        entityManager.persistAndFlush(price("21K", "GRAM", 9500.0, LocalDateTime.of(2026, 3, 1, 0, 0)));
        entityManager.clear();

        Optional<GoldPriceEntity> result = repository.findLatestByPurityAndUnit("22K", "GRAM");

        assertTrue(result.isPresent());
        assertEquals(latest.getId(), result.get().getId());
        assertEquals(10500.0, result.get().getMarketPrice());
    }

    @Test
    void findLatestByPurityAndUnitReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(price("22K", "GRAM", 10000.0, LocalDateTime.now()));
        entityManager.clear();

        assertTrue(repository.findLatestByPurityAndUnit("18K", "VORI").isEmpty());
    }

    @Test
    void findLatestBatchReturnsOnlyRowsFromTheMostRecentRetrievedAt() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 2, 1, 0, 0);
        entityManager.persistAndFlush(price("22K", "GRAM", 10000.0, older));
        entityManager.persistAndFlush(price("21K", "GRAM", 9500.0, older));
        GoldPriceEntity n1 = entityManager.persistAndFlush(price("22K", "GRAM", 11000.0, newer));
        GoldPriceEntity n2 = entityManager.persistAndFlush(price("21K", "GRAM", 10500.0, newer));
        entityManager.clear();

        List<GoldPriceEntity> result = repository.findLatestBatch();

        assertEquals(2, result.size());
        assertTrue(result.stream().map(GoldPriceEntity::getId)
                .allMatch(id -> id.equals(n1.getId()) || id.equals(n2.getId())));
    }

    @Test
    void findLatestBatchReturnsEmptyWhenNoRowsExist() {
        assertTrue(repository.findLatestBatch().isEmpty());
    }

    @Test
    void findLatestRetrievedAtReturnsMaxTimestampAcrossAllRows() {
        entityManager.persistAndFlush(price("22K", "GRAM", 10000.0, LocalDateTime.of(2026, 1, 1, 0, 0)));
        entityManager.persistAndFlush(price("22K", "GRAM", 11000.0, LocalDateTime.of(2026, 2, 1, 0, 0)));
        entityManager.clear();

        Optional<LocalDateTime> result = repository.findLatestRetrievedAt();

        assertTrue(result.isPresent());
        assertEquals(LocalDateTime.of(2026, 2, 1, 0, 0), result.get());
    }

    @Test
    void findLatestRetrievedAtReturnsEmptyWhenNoRowsExist() {
        assertTrue(repository.findLatestRetrievedAt().isEmpty());
    }
}
