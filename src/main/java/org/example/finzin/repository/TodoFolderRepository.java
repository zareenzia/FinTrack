package org.example.finzin.repository;

import org.example.finzin.entity.TodoFolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TodoFolderRepository extends JpaRepository<TodoFolderEntity, Long> {
    List<TodoFolderEntity> findByUserId(Long userId);
    Optional<TodoFolderEntity> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
