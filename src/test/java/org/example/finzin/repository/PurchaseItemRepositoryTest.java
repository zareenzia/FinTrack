package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.PurchaseItemEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PurchaseItemRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PurchaseItemRepository repository;

    private PurchaseItemEntity purchaseItem(Long userId, String itemName, String status) {
        PurchaseItemEntity p = new PurchaseItemEntity();
        p.setUserId(userId);
        p.setItemName(itemName);
        p.setEstimatedPrice(99.99);
        p.setNeedLevel("SHOULD_HAVE");
        p.setPriority("MEDIUM");
        p.setStatus(status);
        return p;
    }

    @Test
    void findByUserIdReturnsOnlyItemsForThatUser() {
        entityManager.persistAndFlush(purchaseItem(1L, "Headphones", "PLANNING"));
        entityManager.persistAndFlush(purchaseItem(1L, "Shoes", "READY"));
        entityManager.persistAndFlush(purchaseItem(2L, "Other", "PLANNING"));
        entityManager.clear();

        List<PurchaseItemEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(p -> p.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsItemOnlyForOwningUser() {
        PurchaseItemEntity saved = entityManager.persistAndFlush(purchaseItem(1L, "Headphones", "PLANNING"));
        entityManager.clear();

        Optional<PurchaseItemEntity> found = repository.findByIdAndUserId(saved.getId(), 1L);
        Optional<PurchaseItemEntity> wrongUser = repository.findByIdAndUserId(saved.getId(), 2L);

        assertTrue(found.isPresent());
        assertEquals("Headphones", found.get().getItemName());
        assertTrue(wrongUser.isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByIdAndUserId(999999L, 1L).isEmpty());
    }

    @Test
    void findByUserIdAndStatusInFiltersByStatusList() {
        entityManager.persistAndFlush(purchaseItem(1L, "Headphones", "PLANNING"));
        entityManager.persistAndFlush(purchaseItem(1L, "Shoes", "READY"));
        entityManager.persistAndFlush(purchaseItem(1L, "Jacket", "PURCHASED"));
        entityManager.clear();

        List<PurchaseItemEntity> result = repository.findByUserIdAndStatusIn(1L, List.of("PLANNING", "READY"));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(p -> List.of("PLANNING", "READY").contains(p.getStatus())));
    }

    @Test
    void findByUserIdAndStatusInReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(purchaseItem(1L, "Headphones", "PLANNING"));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndStatusIn(1L, List.of("PURCHASED", "CANCELLED")).isEmpty());
    }

    @Test
    void findByStatusInReturnsMatchingItemsAcrossUsers() {
        entityManager.persistAndFlush(purchaseItem(1L, "Headphones", "PLANNING"));
        entityManager.persistAndFlush(purchaseItem(2L, "Shoes", "PLANNING"));
        entityManager.persistAndFlush(purchaseItem(1L, "Jacket", "PURCHASED"));
        entityManager.clear();

        List<PurchaseItemEntity> result = repository.findByStatusIn(List.of("PLANNING"));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(p -> p.getStatus().equals("PLANNING")));
    }

    @Test
    void findByStatusInReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(purchaseItem(1L, "Headphones", "PLANNING"));
        entityManager.clear();

        assertTrue(repository.findByStatusIn(List.of("CANCELLED")).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersItems() {
        PurchaseItemEntity p1 = entityManager.persistAndFlush(purchaseItem(1L, "Headphones", "PLANNING"));
        PurchaseItemEntity p2 = entityManager.persistAndFlush(purchaseItem(2L, "Shoes", "PLANNING"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(p1.getId()).isEmpty());
        assertTrue(repository.findById(p2.getId()).isPresent());
    }
}
