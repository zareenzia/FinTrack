package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.VoiceCommandHistoryEntity;
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
class VoiceCommandHistoryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private VoiceCommandHistoryRepository repository;

    private VoiceCommandHistoryEntity command(Long userId, String intent) {
        VoiceCommandHistoryEntity c = new VoiceCommandHistoryEntity();
        c.setUserId(userId);
        c.setOriginalTranscript("add a todo for tomorrow");
        c.setIntent(intent);
        c.setSource("AI");
        c.setStatus("pending");
        return c;
    }

    @Test
    void findTop50ByUserIdOrderByCreatedAtDescOrdersNewestFirst() throws InterruptedException {
        VoiceCommandHistoryEntity first = entityManager.persistFlushFind(command(1L, "CREATE_TODO"));
        Thread.sleep(5);
        VoiceCommandHistoryEntity second = entityManager.persistFlushFind(command(1L, "CREATE_TRANSACTION"));
        Thread.sleep(5);
        entityManager.persistFlushFind(command(2L, "CREATE_TODO"));
        entityManager.clear();

        List<VoiceCommandHistoryEntity> result = repository.findTop50ByUserIdOrderByCreatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId(), "most recently created command must come first");
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void findTop50ByUserIdOrderByCreatedAtDescReturnsEmptyWhenNoneForUser() {
        assertTrue(repository.findTop50ByUserIdOrderByCreatedAtDesc(999L).isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsCommandOnlyForOwningUser() {
        VoiceCommandHistoryEntity saved = entityManager.persistAndFlush(command(1L, "CREATE_TODO"));
        entityManager.clear();

        Optional<VoiceCommandHistoryEntity> found = repository.findByIdAndUserId(saved.getId(), 1L);
        Optional<VoiceCommandHistoryEntity> wrongUser = repository.findByIdAndUserId(saved.getId(), 2L);

        assertTrue(found.isPresent());
        assertTrue(wrongUser.isEmpty());
    }

    @Test
    void findByIdAndUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByIdAndUserId(999999L, 1L).isEmpty());
    }

    @Test
    void deleteByIdAndUserIdRemovesOnlyWhenUserMatches() {
        VoiceCommandHistoryEntity saved = entityManager.persistAndFlush(command(1L, "CREATE_TODO"));
        entityManager.clear();

        repository.deleteByIdAndUserId(saved.getId(), 2L);
        entityManager.flush();
        entityManager.clear();
        assertTrue(repository.findById(saved.getId()).isPresent(), "delete for wrong user must not remove the row");

        repository.deleteByIdAndUserId(saved.getId(), 1L);
        entityManager.flush();
        entityManager.clear();
        assertTrue(repository.findById(saved.getId()).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersCommands() {
        VoiceCommandHistoryEntity c1 = entityManager.persistAndFlush(command(1L, "CREATE_TODO"));
        VoiceCommandHistoryEntity c2 = entityManager.persistAndFlush(command(2L, "CREATE_TODO"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(c1.getId()).isEmpty());
        assertTrue(repository.findById(c2.getId()).isPresent());
    }
}
