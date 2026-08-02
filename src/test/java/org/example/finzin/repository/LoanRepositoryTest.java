package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.LoanEntity;
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
class LoanRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LoanRepository repository;

    private LoanEntity loan(Long userId, String name, String status) {
        LoanEntity l = new LoanEntity();
        l.setUserId(userId);
        l.setLoanName(name);
        l.setLoanType("PERSONAL");
        l.setPrincipalAmount(5000.0);
        l.setLoanStartDate(LocalDate.of(2026, 1, 1));
        l.setRemainingBalance(4000.0);
        l.setStatus(status);
        return l;
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersLoans() {
        entityManager.persistAndFlush(loan(1L, "Car Loan", "ACTIVE"));
        entityManager.persistAndFlush(loan(1L, "Home Loan", "ACTIVE"));
        entityManager.persistAndFlush(loan(2L, "Other's Loan", "ACTIVE"));
        entityManager.clear();

        List<LoanEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByUserIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(loan(1L, "Car Loan", "ACTIVE"));
        entityManager.persistAndFlush(loan(1L, "Old Loan", "CLOSED"));
        entityManager.clear();

        List<LoanEntity> result = repository.findByUserIdAndStatus(1L, "CLOSED");

        assertEquals(1, result.size());
        assertEquals("Old Loan", result.get(0).getLoanName());
    }

    @Test
    void findByUserIdAndStatusReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(loan(1L, "Car Loan", "ACTIVE"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndStatus(1L, "OVERDUE").isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersLoans() {
        LoanEntity l1 = entityManager.persistAndFlush(loan(1L, "Car Loan", "ACTIVE"));
        LoanEntity l2 = entityManager.persistAndFlush(loan(2L, "Other's Loan", "ACTIVE"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(l1.getId()).isEmpty());
        assertTrue(repository.findById(l2.getId()).isPresent());
    }
}
