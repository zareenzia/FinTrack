package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.TransactionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository repository;

    private CategoryEntity category(Long userId, String name) {
        CategoryEntity c = new CategoryEntity(userId, name, "desc", "#ff0000", "tag");
        return entityManager.persistAndFlush(c);
    }

    private TransactionEntity transaction(Long userId, Double amount, String type, CategoryEntity category, LocalDateTime date) {
        TransactionEntity t = new TransactionEntity(userId, amount, "desc", category, type, date, LocalDateTime.now());
        return t;
    }

    @Test
    void findByIdAndUserIdReturnsTransactionWhenOwnedByUser() {
        TransactionEntity t = entityManager.persistAndFlush(transaction(1L, 100.0, "expense", null, LocalDateTime.now()));
        entityManager.clear();

        Optional<TransactionEntity> result = repository.findByIdAndUserId(t.getId(), 1L);

        assertTrue(result.isPresent());
    }

    @Test
    void findByIdAndUserIdReturnsEmptyWhenOwnedByAnotherUser() {
        TransactionEntity t = entityManager.persistAndFlush(transaction(1L, 100.0, "expense", null, LocalDateTime.now()));
        entityManager.clear();

        Optional<TransactionEntity> result = repository.findByIdAndUserId(t.getId(), 2L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByCategoryIdOrderByDateDescOrdersNewestFirst() {
        CategoryEntity cat = category(1L, "Dining");
        entityManager.persistAndFlush(transaction(1L, 50.0, "expense", cat, LocalDateTime.of(2026, 1, 1, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 60.0, "expense", cat, LocalDateTime.of(2026, 3, 1, 0, 0)));
        entityManager.clear();

        List<TransactionEntity> result = repository.findByCategory_IdOrderByDateDesc(cat.getId());

        assertEquals(2, result.size());
        assertEquals(60.0, result.get(0).getAmount());
        assertEquals(50.0, result.get(1).getAmount());
    }

    @Test
    void findByCategoryIdOrderByDateDescReturnsEmptyWhenNoneForCategory() {
        List<TransactionEntity> result = repository.findByCategory_IdOrderByDateDesc(999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByTransactionTypeOrderByDateDescOrdersNewestFirstAndFiltersByType() {
        entityManager.persistAndFlush(transaction(1L, 10.0, "income", null, LocalDateTime.of(2026, 1, 1, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 20.0, "expense", null, LocalDateTime.of(2026, 2, 1, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 30.0, "expense", null, LocalDateTime.of(2026, 4, 1, 0, 0)));
        entityManager.clear();

        List<TransactionEntity> result = repository.findByTransactionTypeOrderByDateDesc("expense");

        assertEquals(2, result.size());
        assertEquals(30.0, result.get(0).getAmount());
        assertEquals(20.0, result.get(1).getAmount());
    }

    @Test
    void findByUserIdReturnsAllTransactionsForUser() {
        entityManager.persistAndFlush(transaction(1L, 10.0, "income", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(1L, 20.0, "expense", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(2L, 30.0, "expense", null, LocalDateTime.now()));
        entityManager.clear();

        List<TransactionEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdOrderByDateDescOrdersNewestFirst() {
        entityManager.persistAndFlush(transaction(1L, 10.0, "expense", null, LocalDateTime.of(2026, 1, 1, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 20.0, "expense", null, LocalDateTime.of(2026, 5, 1, 0, 0)));
        entityManager.clear();

        List<TransactionEntity> result = repository.findByUserIdOrderByDateDesc(1L);

        assertEquals(20.0, result.get(0).getAmount());
        assertEquals(10.0, result.get(1).getAmount());
    }

    @Test
    void countByUserIdCountsOnlyThatUsersTransactions() {
        entityManager.persistAndFlush(transaction(1L, 10.0, "expense", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(1L, 20.0, "expense", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(2L, 30.0, "expense", null, LocalDateTime.now()));
        entityManager.clear();

        assertEquals(2L, repository.countByUserId(1L));
        assertEquals(0L, repository.countByUserId(999L));
    }

    @Test
    void sumByTransactionTypeSumsAcrossAllUsersForThatType() {
        entityManager.persistAndFlush(transaction(1L, 100.0, "income", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(2L, 50.0, "income", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(1L, 999.0, "expense", null, LocalDateTime.now()));
        entityManager.clear();

        Double sum = repository.sumByTransactionType("income");

        assertEquals(150.0, sum);
    }

    @Test
    void sumByTransactionTypeReturnsNullWhenNoMatches() {
        assertNull(repository.sumByTransactionType("nonexistent-type"));
    }

    @Test
    void sumByUserIdAndTransactionTypeAndSumByUserIdAndTypeAgreeOnTheSameTotal() {
        entityManager.persistAndFlush(transaction(1L, 100.0, "expense", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(1L, 50.0, "expense", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(1L, 999.0, "income", null, LocalDateTime.now()));
        entityManager.persistAndFlush(transaction(2L, 999.0, "expense", null, LocalDateTime.now()));
        entityManager.clear();

        assertEquals(150.0, repository.sumByUserIdAndTransactionType(1L, "expense"));
        assertEquals(150.0, repository.sumByUserIdAndType(1L, "expense"));
    }

    @Test
    void sumByUserIdAndTransactionTypeAndFromSavingsTrueOnlyIncludesFromSavingsTransactions() {
        TransactionEntity fromSavings = transaction(1L, 100.0, "expense", null, LocalDateTime.now());
        fromSavings.setFromSavings(true);
        TransactionEntity notFromSavings = transaction(1L, 200.0, "expense", null, LocalDateTime.now());
        notFromSavings.setFromSavings(false);
        entityManager.persistAndFlush(fromSavings);
        entityManager.persistAndFlush(notFromSavings);
        entityManager.clear();

        Double sum = repository.sumByUserIdAndTransactionTypeAndFromSavingsTrue(1L, "expense");

        assertEquals(100.0, sum);
    }

    @Test
    void existsBySourceAccountIdOrDestinationAccountIdIsTrueWhenEitherMatches() {
        TransactionEntity t = transaction(1L, 100.0, "transfer", null, LocalDateTime.now());
        t.setSourceAccountId(10L);
        t.setDestinationAccountId(20L);
        entityManager.persistAndFlush(t);
        entityManager.clear();

        assertTrue(repository.existsBySourceAccountIdOrDestinationAccountId(10L, 999L));
        assertTrue(repository.existsBySourceAccountIdOrDestinationAccountId(999L, 20L));
    }

    @Test
    void existsBySourceAccountIdOrDestinationAccountIdIsFalseWhenNeitherMatches() {
        TransactionEntity t = transaction(1L, 100.0, "transfer", null, LocalDateTime.now());
        t.setSourceAccountId(10L);
        t.setDestinationAccountId(20L);
        entityManager.persistAndFlush(t);
        entityManager.clear();

        assertFalse(repository.existsBySourceAccountIdOrDestinationAccountId(111L, 222L));
    }

    @Test
    void sumByUserIdAndTypeAndCategoryAndDateRangeFiltersByAllThreeDimensions() {
        CategoryEntity cat = category(1L, "Dining");
        CategoryEntity otherCat = category(1L, "Groceries");
        entityManager.persistAndFlush(transaction(1L, 100.0, "expense", cat, LocalDateTime.of(2026, 7, 15, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 200.0, "expense", otherCat, LocalDateTime.of(2026, 7, 15, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 300.0, "expense", cat, LocalDateTime.of(2026, 8, 15, 0, 0)));
        entityManager.clear();

        Double sum = repository.sumByUserIdAndTypeAndCategoryAndDateRange(1L, "expense", cat.getId(),
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));

        assertEquals(100.0, sum);
    }

    @Test
    void sumByUserIdAndTypeAndDateRangeExcludesTransactionsOutsideRange() {
        entityManager.persistAndFlush(transaction(1L, 100.0, "expense", null, LocalDateTime.of(2026, 7, 15, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 300.0, "expense", null, LocalDateTime.of(2026, 8, 15, 0, 0)));
        entityManager.clear();

        Double sum = repository.sumByUserIdAndTypeAndDateRange(1L, "expense",
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));

        assertEquals(100.0, sum);
    }

    @Test
    void findByUserIdAndDateRangeIsEndExclusive() {
        entityManager.persistAndFlush(transaction(1L, 10.0, "expense", null, LocalDateTime.of(2026, 7, 1, 0, 0)));
        entityManager.persistAndFlush(transaction(1L, 20.0, "expense", null, LocalDateTime.of(2026, 7, 31, 23, 59)));
        entityManager.persistAndFlush(transaction(1L, 30.0, "expense", null, LocalDateTime.of(2026, 8, 1, 0, 0)));
        entityManager.clear();

        List<TransactionEntity> result = repository.findByUserIdAndDateRange(1L,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));

        assertEquals(2, result.size(), "the transaction exactly at the end boundary must be excluded");
    }

    @Test
    void findBySourceAccountIdOrDestinationAccountIdOrderByDateAscOrdersOldestFirst() {
        TransactionEntity older = transaction(1L, 10.0, "transfer", null, LocalDateTime.of(2026, 1, 1, 0, 0));
        older.setSourceAccountId(5L);
        TransactionEntity newer = transaction(1L, 20.0, "transfer", null, LocalDateTime.of(2026, 6, 1, 0, 0));
        newer.setDestinationAccountId(5L);
        entityManager.persistAndFlush(newer);
        entityManager.persistAndFlush(older);
        entityManager.clear();

        List<TransactionEntity> result = repository.findBySourceAccountIdOrDestinationAccountIdOrderByDateAsc(5L, 5L);

        assertEquals(2, result.size());
        assertEquals(10.0, result.get(0).getAmount());
        assertEquals(20.0, result.get(1).getAmount());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersTransactions() {
        TransactionEntity t1 = entityManager.persistAndFlush(transaction(1L, 10.0, "expense", null, LocalDateTime.now()));
        TransactionEntity t2 = entityManager.persistAndFlush(transaction(2L, 20.0, "expense", null, LocalDateTime.now()));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(t1.getId()).isEmpty());
        assertTrue(repository.findById(t2.getId()).isPresent());
    }
}
