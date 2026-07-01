package org.example.finzin.repository;

import org.example.finzin.entity.NoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<NoteEntity, Long> {
    List<NoteEntity> findByArchivedFalseOrderByPinnedDescUpdatedAtDesc(Boolean archived);

    @Query("SELECT n FROM NoteEntity n WHERE n.archived = false AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY n.pinned DESC, n.updatedAt DESC")
    List<NoteEntity> searchNotes(@Param("search") String search);

    @Query("SELECT n FROM NoteEntity n WHERE n.archived = false AND n.pinned = true ORDER BY n.updatedAt DESC")
    List<NoteEntity> findPinnedNotes();
}
