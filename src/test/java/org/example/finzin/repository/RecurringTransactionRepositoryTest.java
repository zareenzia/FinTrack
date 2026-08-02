package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.RecurringTransactionEntity;
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
class RecurringTransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RecurringTransactionRepository repository;

    private RecurringTransactionEntity recurring(Long userId, String status, LocalDate nextExecutionDate) {
        RecurringTransactionEntity r = new RecurringTransactionEntity();
        r.setUserId(userId);
        r.setTransactionName("Netflix");
        r.setTransactionType("expense");
        r.setAmount(15.0);
        r.setFrequency("MONTHLY");
        r.setIntervalValue(1);
        r.setStartDate(LocalDate.of(2026, 1, 1));
        r.setNextExecutionDate(nextExecutionDate);
        r.setStatus(status);
        return r;
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersRecurringTransactions() {
        entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 9, 1)));
        entityManager.persistAndFlush(recurring(2L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.clear();

        List<RecurringTransactionEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.persistAndFlush(recurring(1L, "PAUSED", LocalDate.of(2026, 8, 1)));
        entityManager.clear();

        List<RecurringTransactionEntity> result = repository.findByUserIdAndStatus(1L, "PAUSED");

        assertEquals(1, result.size());
        assertEquals("PAUSED", result.get(0).getStatus());
    }

    @Test
    void findByStatusAndNextExecutionDateLessThanEqualReturnsDueTransactionsAcrossAllUsers() {
        entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.persistAndFlush(recurring(2L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 12, 1)));
        entityManager.clear();

        List<RecurringTransactionEntity> result =
                repository.findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", LocalDate.of(2026, 8, 1));

        assertEquals(2, result.size());
    }

    @Test
    void findByStatusAndNextExecutionDateLessThanEqualExcludesFutureAndWrongStatus() {
        entityManager.persistAndFlush(recurring(1L, "PAUSED", LocalDate.of(2026, 8, 1)));
        entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 12, 1)));
        entityManager.clear();

        List<RecurringTransactionEntity> result =
                repository.findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", LocalDate.of(2026, 8, 1));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByUserIdAndStatusAndNextExecutionDateLessThanEqualCombinesAllThreeFilters() {
        entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.persistAndFlush(recurring(2L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.persistAndFlush(recurring(1L, "PAUSED", LocalDate.of(2026, 8, 1)));
        entityManager.clear();

        List<RecurringTransactionEntity> result =
                repository.findByUserIdAndStatusAndNextExecutionDateLessThanEqual(1L, "ACTIVE", LocalDate.of(2026, 8, 1));

        assertEquals(1, result.size());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersRecurringTransactions() {
        RecurringTransactionEntity r1 = entityManager.persistAndFlush(recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        RecurringTransactionEntity r2 = entityManager.persistAndFlush(recurring(2L, "ACTIVE", LocalDate.of(2026, 8, 1)));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(r1.getId()).isEmpty());
        assertTrue(repository.findById(r2.getId()).isPresent());
    }
}
