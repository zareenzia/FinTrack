package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.SharedTransactionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SharedTransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SharedTransactionRepository repository;

    private SharedTransactionEntity sharedTransaction(Long householdId, Long transactionId, LocalDate expenseDate) {
        SharedTransactionEntity t = new SharedTransactionEntity();
        t.setHouseholdId(householdId);
        t.setTransactionId(transactionId);
        t.setPayerUserId(10L);
        t.setTotalAmount(100.0);
        t.setSplitMethod("EQUAL");
        t.setDescription("Groceries run");
        t.setExpenseDate(expenseDate);
        return t;
    }

    @Test
    void findByHouseholdIdOrderByExpenseDateDescOrdersNewestFirstAndFiltersByHousehold() {
        SharedTransactionEntity t1 = entityManager.persistAndFlush(sharedTransaction(1L, 100L, LocalDate.of(2026, 1, 1)));
        SharedTransactionEntity t2 = entityManager.persistAndFlush(sharedTransaction(1L, 101L, LocalDate.of(2026, 3, 1)));
        entityManager.persistAndFlush(sharedTransaction(2L, 102L, LocalDate.of(2026, 2, 1)));
        entityManager.clear();

        List<SharedTransactionEntity> result = repository.findByHouseholdIdOrderByExpenseDateDesc(1L);

        assertEquals(2, result.size());
        assertEquals(t2.getId(), result.get(0).getId());
        assertEquals(t1.getId(), result.get(1).getId());
    }

    @Test
    void findByHouseholdIdOrderByExpenseDateDescReturnsEmptyWhenNoneForHousehold() {
        assertTrue(repository.findByHouseholdIdOrderByExpenseDateDesc(999L).isEmpty());
    }

    @Test
    void findByHouseholdIdAndExpenseDateBetweenFiltersByDateRange() {
        entityManager.persistAndFlush(sharedTransaction(1L, 100L, LocalDate.of(2026, 1, 15)));
        entityManager.persistAndFlush(sharedTransaction(1L, 101L, LocalDate.of(2026, 2, 15)));
        entityManager.persistAndFlush(sharedTransaction(1L, 102L, LocalDate.of(2026, 3, 15)));
        entityManager.clear();

        List<SharedTransactionEntity> result = repository.findByHouseholdIdAndExpenseDateBetween(
                1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getTransactionId());
    }

    @Test
    void findByHouseholdIdAndExpenseDateBetweenReturnsEmptyWhenNoneInRange() {
        entityManager.persistAndFlush(sharedTransaction(1L, 100L, LocalDate.of(2026, 1, 15)));
        entityManager.clear();

        assertTrue(repository.findByHouseholdIdAndExpenseDateBetween(
                1L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)).isEmpty());
    }

    @Test
    void findByTransactionIdReturnsMatchingSharedTransaction() {
        entityManager.persistAndFlush(sharedTransaction(1L, 100L, LocalDate.of(2026, 1, 15)));
        entityManager.clear();

        Optional<SharedTransactionEntity> result = repository.findByTransactionId(100L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getHouseholdId());
    }

    @Test
    void findByTransactionIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByTransactionId(999L).isEmpty());
    }

    @Test
    void deleteByHouseholdIdRemovesOnlyThatHouseholdsSharedTransactions() {
        SharedTransactionEntity t1 = entityManager.persistAndFlush(sharedTransaction(1L, 100L, LocalDate.of(2026, 1, 15)));
        SharedTransactionEntity t2 = entityManager.persistAndFlush(sharedTransaction(2L, 200L, LocalDate.of(2026, 1, 15)));
        entityManager.clear();

        repository.deleteByHouseholdId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(t1.getId()).isEmpty());
        assertTrue(repository.findById(t2.getId()).isPresent());
    }
}
