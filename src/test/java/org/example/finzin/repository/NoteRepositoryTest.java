package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.NoteEntity;
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
class NoteRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NoteRepository repository;

    private NoteEntity note(Long userId, String title, String content, boolean pinned, boolean archived) {
        NoteEntity n = new NoteEntity();
        n.setUserId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setPinned(pinned);
        n.setArchived(archived);
        return n;
    }

    @Test
    void findByArchivedFalseOrderByPinnedDescUpdatedAtDescReturnsOnlyUnarchivedPinnedFirst() {
        entityManager.persistAndFlush(note(1L, "Archived Note", "old", false, true));
        entityManager.persistAndFlush(note(1L, "Regular Note", "content", false, false));
        entityManager.persistAndFlush(note(1L, "Pinned Note", "content", true, false));
        entityManager.clear();

        List<NoteEntity> result = repository.findByArchivedFalseOrderByPinnedDescUpdatedAtDesc(false);

        assertEquals(2, result.size());
        assertEquals("Pinned Note", result.get(0).getTitle(), "pinned notes must sort before unpinned ones");
    }

    @Test
    void searchNotesMatchesTitleOrContentAmongUnarchivedNotesOnly() {
        entityManager.persistAndFlush(note(1L, "Budget Ideas", "save money", false, false));
        entityManager.persistAndFlush(note(1L, "Archived Budget", "old budget notes", false, true));
        entityManager.persistAndFlush(note(1L, "Groceries", "milk eggs bread", false, false));
        entityManager.clear();

        List<NoteEntity> result = repository.searchNotes("budget");

        assertEquals(1, result.size());
        assertEquals("Budget Ideas", result.get(0).getTitle());
    }

    @Test
    void findPinnedNotesReturnsOnlyPinnedUnarchivedNotes() {
        entityManager.persistAndFlush(note(1L, "Pinned", "content", true, false));
        entityManager.persistAndFlush(note(1L, "Not Pinned", "content", false, false));
        entityManager.persistAndFlush(note(1L, "Pinned Archived", "content", true, true));
        entityManager.clear();

        List<NoteEntity> result = repository.findPinnedNotes();

        assertEquals(1, result.size());
        assertEquals("Pinned", result.get(0).getTitle());
    }

    @Test
    void findByUserIdAndArchivedFiltersByBothFields() {
        entityManager.persistAndFlush(note(1L, "Active", "content", false, false));
        entityManager.persistAndFlush(note(1L, "Archived", "content", false, true));
        entityManager.persistAndFlush(note(2L, "Other User", "content", false, false));
        entityManager.clear();

        List<NoteEntity> result = repository.findByUserIdAndArchived(1L, false);

        assertEquals(1, result.size());
        assertEquals("Active", result.get(0).getTitle());
    }

    @Test
    void searchByUserIdAndContentScopesToUserAndMatchesTitleOrContent() {
        entityManager.persistAndFlush(note(1L, "Vacation Plan", "trip to Cox's Bazar", false, false));
        entityManager.persistAndFlush(note(2L, "Vacation Plan", "different user's note", false, false));
        entityManager.clear();

        List<NoteEntity> result = repository.searchByUserIdAndContent(1L, "vacation");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getUserId());
    }

    @Test
    void findPinnedByUserIdReturnsOnlyThatUsersPinnedUnarchivedNotes() {
        entityManager.persistAndFlush(note(1L, "My Pinned", "content", true, false));
        entityManager.persistAndFlush(note(2L, "Other Pinned", "content", true, false));
        entityManager.clear();

        List<NoteEntity> result = repository.findPinnedByUserId(1L);

        assertEquals(1, result.size());
        assertEquals("My Pinned", result.get(0).getTitle());
    }

    @Test
    void findByUserIdAndArchivedFalseOrderByPinnedDescUpdatedAtDescOrdersPinnedFirstAndExcludesArchived() {
        entityManager.persistAndFlush(note(1L, "Archived", "content", true, true));
        entityManager.persistAndFlush(note(1L, "Unpinned", "content", false, false));
        entityManager.persistAndFlush(note(1L, "Pinned", "content", true, false));
        entityManager.clear();

        List<NoteEntity> result = repository.findByUserIdAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(1L);

        assertEquals(2, result.size());
        assertEquals("Pinned", result.get(0).getTitle());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersNotes() {
        NoteEntity n1 = entityManager.persistAndFlush(note(1L, "Mine", "content", false, false));
        NoteEntity n2 = entityManager.persistAndFlush(note(2L, "Other's", "content", false, false));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(n1.getId()).isEmpty());
        assertTrue(repository.findById(n2.getId()).isPresent());
    }
}
