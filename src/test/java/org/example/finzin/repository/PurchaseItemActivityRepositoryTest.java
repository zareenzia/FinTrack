package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.PurchaseItemActivityEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PurchaseItemActivityRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PurchaseItemActivityRepository repository;

    private PurchaseItemActivityEntity activity(Long purchaseItemId, Long userId, String activityType) {
        PurchaseItemActivityEntity a = new PurchaseItemActivityEntity();
        a.setPurchaseItemId(purchaseItemId);
        a.setUserId(userId);
        a.setActivityType(activityType);
        return a;
    }

    @Test
    void findByPurchaseItemIdOrderByCreatedAtDescOrdersNewestFirst() throws InterruptedException {
        PurchaseItemActivityEntity first = entityManager.persistFlushFind(activity(1L, 1L, "CREATED"));
        Thread.sleep(5);
        PurchaseItemActivityEntity second = entityManager.persistFlushFind(activity(1L, 1L, "STATUS_CHANGE"));
        Thread.sleep(5);
        entityManager.persistFlushFind(activity(2L, 1L, "CREATED"));
        entityManager.clear();

        List<PurchaseItemActivityEntity> result = repository.findByPurchaseItemIdOrderByCreatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId(), "most recently created activity must come first");
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void findByPurchaseItemIdOrderByCreatedAtDescReturnsEmptyWhenNoneForItem() {
        assertTrue(repository.findByPurchaseItemIdOrderByCreatedAtDesc(999L).isEmpty());
    }

    @Test
    void findByPurchaseItemIdAndActivityTypeOrderByCreatedAtAscOrdersOldestFirstAndFiltersType() throws InterruptedException {
        PurchaseItemActivityEntity firstPriceChange = entityManager.persistFlushFind(activity(1L, 1L, "PRICE_CHANGE"));
        Thread.sleep(5);
        entityManager.persistFlushFind(activity(1L, 1L, "STATUS_CHANGE"));
        Thread.sleep(5);
        PurchaseItemActivityEntity secondPriceChange = entityManager.persistFlushFind(activity(1L, 1L, "PRICE_CHANGE"));
        entityManager.clear();

        List<PurchaseItemActivityEntity> result =
                repository.findByPurchaseItemIdAndActivityTypeOrderByCreatedAtAsc(1L, "PRICE_CHANGE");

        assertEquals(2, result.size());
        assertEquals(firstPriceChange.getId(), result.get(0).getId(), "oldest PRICE_CHANGE must come first");
        assertEquals(secondPriceChange.getId(), result.get(1).getId());
    }

    @Test
    void findByPurchaseItemIdAndActivityTypeOrderByCreatedAtAscReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(activity(1L, 1L, "CREATED"));
        entityManager.clear();

        assertTrue(repository.findByPurchaseItemIdAndActivityTypeOrderByCreatedAtAsc(1L, "PRICE_CHANGE").isEmpty());
    }

    @Test
    void deleteByPurchaseItemIdRemovesOnlyThatItemsActivity() {
        PurchaseItemActivityEntity a1 = entityManager.persistAndFlush(activity(1L, 1L, "CREATED"));
        PurchaseItemActivityEntity a2 = entityManager.persistAndFlush(activity(2L, 1L, "CREATED"));
        entityManager.clear();

        repository.deleteByPurchaseItemId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(a1.getId()).isEmpty());
        assertTrue(repository.findById(a2.getId()).isPresent());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersActivity() {
        PurchaseItemActivityEntity a1 = entityManager.persistAndFlush(activity(1L, 1L, "CREATED"));
        PurchaseItemActivityEntity a2 = entityManager.persistAndFlush(activity(2L, 2L, "CREATED"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(a1.getId()).isEmpty());
        assertTrue(repository.findById(a2.getId()).isPresent());
    }
}
