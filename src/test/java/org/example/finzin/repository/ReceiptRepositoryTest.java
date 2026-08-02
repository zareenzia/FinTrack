package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.ReceiptEntity;
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
class ReceiptRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReceiptRepository repository;

    private ReceiptEntity receipt(Long userId, Long transactionId) {
        ReceiptEntity r = new ReceiptEntity();
        r.setUserId(userId);
        r.setTransactionId(transactionId);
        r.setImagePath("/receipts/img.jpg");
        r.setMimeType("image/jpeg");
        r.setFileSizeBytes(1024L);
        r.setExtractionSource("AI");
        return r;
    }

    @Test
    void findByIdAndUserIdReturnsReceiptOnlyForOwningUser() {
        ReceiptEntity saved = entityManager.persistAndFlush(receipt(1L, null));
        entityManager.clear();

        Optional<ReceiptEntity> found = repository.findByIdAndUserId(saved.getId(), 1L);
        Optional<ReceiptEntity> wrongUser = repository.findByIdAndUserId(saved.getId(), 2L);

        assertTrue(found.isPresent());
        assertTrue(wrongUser.isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByIdAndUserId(999999L, 1L).isEmpty());
    }

    @Test
    void findByUserIdAndTransactionIdInReturnsMatchingReceipts() {
        entityManager.persistAndFlush(receipt(1L, 100L));
        entityManager.persistAndFlush(receipt(1L, 101L));
        entityManager.persistAndFlush(receipt(1L, 102L));
        entityManager.persistAndFlush(receipt(2L, 100L));
        entityManager.clear();

        List<ReceiptEntity> result = repository.findByUserIdAndTransactionIdIn(1L, List.of(100L, 101L));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> List.of(100L, 101L).contains(r.getTransactionId())));
    }

    @Test
    void findByUserIdAndTransactionIdInReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(receipt(1L, 100L));
        entityManager.clear();

        assertTrue(repository.findByUserIdAndTransactionIdIn(1L, List.of(999L)).isEmpty());
    }

    @Test
    void findByCreatedAtBeforeReturnsOnlyReceiptsOlderThanCutoff() {
        ReceiptEntity saved = entityManager.persistFlushFind(receipt(1L, null));
        entityManager.clear();

        List<ReceiptEntity> before = repository.findByCreatedAtBefore(saved.getCreatedAt().plusSeconds(1));
        List<ReceiptEntity> tooEarly = repository.findByCreatedAtBefore(saved.getCreatedAt().minusSeconds(1));

        assertTrue(before.stream().anyMatch(r -> r.getId().equals(saved.getId())));
        assertTrue(tooEarly.isEmpty());
    }

    @Test
    void findByUserIdReturnsOnlyReceiptsForThatUser() {
        entityManager.persistAndFlush(receipt(1L, 100L));
        entityManager.persistAndFlush(receipt(1L, 101L));
        entityManager.persistAndFlush(receipt(2L, 102L));
        entityManager.clear();

        List<ReceiptEntity> result = repository.findByUserId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getUserId().equals(1L)));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoneForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersReceipts() {
        ReceiptEntity r1 = entityManager.persistAndFlush(receipt(1L, 100L));
        ReceiptEntity r2 = entityManager.persistAndFlush(receipt(2L, 101L));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(r1.getId()).isEmpty());
        assertTrue(repository.findById(r2.getId()).isPresent());
    }
}
