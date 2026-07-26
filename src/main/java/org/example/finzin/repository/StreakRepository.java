package org.example.finzin.repository;

import org.example.finzin.entity.StreakEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StreakRepository extends JpaRepository<StreakEntity, Long> {
    Optional<StreakEntity> findByUserIdAndStreakType(Long userId, String streakType);
    List<StreakEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
