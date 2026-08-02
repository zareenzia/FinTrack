package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.AccountEntity;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository repository;

    private AccountEntity account(Long userId, String nickname, String status) {
        AccountEntity a = new AccountEntity();
        a.setUserId(userId);
        a.setAccountType("BANK");
        a.setAccountNickname(nickname);
        a.setCreditLimitBehavior("WARN");
        a.setOpeningBalance(1000.0);
        a.setCurrentBalance(1000.0);
        a.setStatus(status);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return a;
    }

    @Test
    void findByUserIdReturnsOnlyAccountsForThatUser() {
        entityManager.persistAndFlush(account(1L, "Main Checking", "ACTIVE"));
        entityManager.persistAndFlush(account(1L, "Savings", "ACTIVE"));
        entityManager.persistAndFlush(account(2L, "Other User Account", "ACTIVE"));
        entityManager.clear();

        List<AccountEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(a -> a.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoAccountsForUser() {
        List<AccountEntity> result = repository.findByUserId(999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByUserIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(account(1L, "Active One", "ACTIVE"));
        entityManager.persistAndFlush(account(1L, "Closed One", "CLOSED"));
        entityManager.clear();

        List<AccountEntity> result = repository.findByUserIdAndStatus(1L, "ACTIVE");

        assertEquals(1, result.size());
        assertEquals("Active One", result.get(0).getAccountNickname());
    }

    @Test
    void findByUserIdAndStatusReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(account(1L, "Active One", "ACTIVE"));
        entityManager.clear();

        List<AccountEntity> result = repository.findByUserIdAndStatus(1L, "CLOSED");

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByUserIdAndAccountNicknameIgnoreCaseIsTrueRegardlessOfCase() {
        entityManager.persistAndFlush(account(1L, "My Wallet", "ACTIVE"));
        entityManager.clear();

        assertTrue(repository.existsByUserIdAndAccountNicknameIgnoreCase(1L, "my wallet"));
        assertTrue(repository.existsByUserIdAndAccountNicknameIgnoreCase(1L, "MY WALLET"));
    }

    @Test
    void existsByUserIdAndAccountNicknameIgnoreCaseIsFalseForDifferentUserOrNickname() {
        entityManager.persistAndFlush(account(1L, "My Wallet", "ACTIVE"));
        entityManager.clear();

        assertFalse(repository.existsByUserIdAndAccountNicknameIgnoreCase(2L, "my wallet"));
        assertFalse(repository.existsByUserIdAndAccountNicknameIgnoreCase(1L, "someone else's wallet"));
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersAccounts() {
        AccountEntity a1 = entityManager.persistAndFlush(account(1L, "Acc1", "ACTIVE"));
        AccountEntity a2 = entityManager.persistAndFlush(account(1L, "Acc2", "ACTIVE"));
        AccountEntity a3 = entityManager.persistAndFlush(account(2L, "Acc3", "ACTIVE"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(a1.getId()).isEmpty());
        assertTrue(repository.findById(a2.getId()).isEmpty());
        assertTrue(repository.findById(a3.getId()).isPresent());
    }

    @Test
    void findByIdForUpdateReturnsMatchingAccount() {
        AccountEntity saved = entityManager.persistAndFlush(account(1L, "Locked Account", "ACTIVE"));
        entityManager.clear();

        Optional<AccountEntity> result = repository.findByIdForUpdate(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("Locked Account", result.get().getAccountNickname());
    }

    @Test
    void findByIdForUpdateReturnsEmptyWhenNotFound() {
        Optional<AccountEntity> result = repository.findByIdForUpdate(123456L);
        assertTrue(result.isEmpty());
    }
}
