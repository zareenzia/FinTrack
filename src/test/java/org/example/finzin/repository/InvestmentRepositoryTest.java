package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.InvestmentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvestmentRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InvestmentRepository repository;

    private InvestmentEntity investment(Long userId, String name, String type) {
        InvestmentEntity i = new InvestmentEntity();
        i.setUserId(userId);
        i.setName(name);
        i.setInvestmentType(type);
        i.setPurchaseDate(LocalDate.of(2026, 1, 1));
        i.setQuantity(10.0);
        i.setPurchasePrice(100.0);
        i.setCurrentPrice(110.0);
        return i;
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersInvestments() {
        entityManager.persistAndFlush(investment(1L, "AAPL", "STOCKS"));
        entityManager.persistAndFlush(investment(1L, "BTC", "CRYPTO"));
        entityManager.persistAndFlush(investment(2L, "GOOG", "STOCKS"));
        entityManager.clear();

        List<InvestmentEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdAndInvestmentTypeFiltersByType() {
        entityManager.persistAndFlush(investment(1L, "AAPL", "STOCKS"));
        entityManager.persistAndFlush(investment(1L, "BTC", "CRYPTO"));
        entityManager.clear();

        List<InvestmentEntity> result = repository.findByUserIdAndInvestmentType(1L, "CRYPTO");

        assertEquals(1, result.size());
        assertEquals("BTC", result.get(0).getName());
    }

    @Test
    void findByUserIdAndInvestmentTypeReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(investment(1L, "AAPL", "STOCKS"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndInvestmentType(1L, "BONDS").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersInvestments() {
        InvestmentEntity i1 = entityManager.persistAndFlush(investment(1L, "AAPL", "STOCKS"));
        InvestmentEntity i2 = entityManager.persistAndFlush(investment(2L, "GOOG", "STOCKS"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(i1.getId()).isEmpty());
        assertTrue(repository.findById(i2.getId()).isPresent());
    }
}
