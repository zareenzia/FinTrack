package org.example.finzin.repository;

import org.example.finzin.entity.TodoListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TodoListRepository extends JpaRepository<TodoListEntity, Long> {
    List<TodoListEntity> findByUserId(Long userId);
    Optional<TodoListEntity> findByIdAndUserId(Long id, Long userId);
    List<TodoListEntity> findByUserIdAndFolderId(Long userId, Long folderId);
    Optional<TodoListEntity> findFirstByUserIdAndNameIgnoreCase(Long userId, String name);
    void deleteByUserId(Long userId);
}
