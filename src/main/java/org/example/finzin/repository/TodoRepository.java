package org.example.finzin.repository;

import org.example.finzin.entity.TodoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<TodoEntity, Long> {
    List<TodoEntity> findByStatusOrderByDueDateAscPriorityAsc(String status);

    @Query("SELECT t FROM TodoEntity t WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY t.dueDate ASC, t.priority DESC")
    List<TodoEntity> searchTodos(@Param("search") String search);

    @Query("SELECT t FROM TodoEntity t WHERE t.status != 'completed' ORDER BY t.dueDate ASC, t.priority DESC")
    List<TodoEntity> findActiveTodos();

    @Query("SELECT t FROM TodoEntity t WHERE t.status != 'completed' AND t.dueDate = :date ORDER BY t.dueTime ASC, t.priority DESC")
    List<TodoEntity> findTodaysTodos(@Param("date") LocalDate date);

    @Query("SELECT t FROM TodoEntity t WHERE t.status != 'completed' AND t.dueDate < :date ORDER BY t.dueDate ASC, t.priority DESC")
    List<TodoEntity> findOverdueTodos(@Param("date") LocalDate date);

    long countByStatusAndCompleted(String status, Boolean completed);
    
    List<TodoEntity> findByUserIdAndStatus(Long userId, String status);
    List<TodoEntity> findByUserIdAndCompletedFalse(Long userId);
    
    @Query("SELECT t FROM TodoEntity t WHERE t.userId = :userId AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY t.dueDate ASC, t.priority DESC")
    List<TodoEntity> searchByUserIdAndTitle(@Param("userId") Long userId, @Param("search") String search);
    
    @Query("SELECT t FROM TodoEntity t WHERE t.userId = :userId AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY t.dueDate ASC, t.priority DESC")
    List<TodoEntity> searchByUserId(@Param("userId") Long userId, @Param("search") String search);
    
    @Query("SELECT t FROM TodoEntity t WHERE t.userId = :userId AND t.status != 'completed' ORDER BY t.dueDate ASC, t.priority DESC")
    List<TodoEntity> findActiveTodosByUserId(@Param("userId") Long userId);
    
    @Query("SELECT t FROM TodoEntity t WHERE t.userId = :userId AND t.status != 'completed' AND t.dueDate = :date ORDER BY t.dueTime ASC, t.priority DESC")
    List<TodoEntity> findTodaysTodosByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);
    
    @Query("SELECT t FROM TodoEntity t WHERE t.userId = :userId AND t.status != 'completed' AND t.dueDate < :date ORDER BY t.dueDate ASC, t.priority DESC")
    List<TodoEntity> findOverdueTodosByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);

    void deleteByUserId(Long userId);
}

