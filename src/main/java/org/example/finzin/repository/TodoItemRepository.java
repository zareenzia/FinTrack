package org.example.finzin.repository;

import org.example.finzin.entity.TodoItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TodoItemRepository extends JpaRepository<TodoItemEntity, Long> {
    List<TodoItemEntity> findByUserIdAndListIdAndParentItemIdIsNull(Long userId, Long listId);
    List<TodoItemEntity> findByListId(Long listId);
    List<TodoItemEntity> findByParentItemId(Long parentItemId);
    Optional<TodoItemEntity> findByIdAndUserId(Long id, Long userId);
    List<TodoItemEntity> findByUserIdAndImportantTrueAndCompletedFalseOrderByCreatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
    void deleteByListId(Long listId);
    void deleteByParentItemId(Long parentItemId);

    /** Per-list top-level item counts for the sidebar's badges — avoids an N+1 findByListId per list. */
    @Query("SELECT i.listId as listId, COUNT(i) as total, " +
           "SUM(CASE WHEN i.completed = true THEN 1L ELSE 0L END) as completedCount " +
           "FROM TodoItemEntity i WHERE i.userId = :userId AND i.parentItemId IS NULL GROUP BY i.listId")
    List<TodoListItemCount> countTopLevelItemsByList(@Param("userId") Long userId);

    interface TodoListItemCount {
        Long getListId();
        Long getTotal();
        Long getCompletedCount();
    }
}
