package org.example.finzin.repository;

import org.example.finzin.entity.UserAchievementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievementEntity, Long> {
    List<UserAchievementEntity> findByUserId(Long userId);
    Optional<UserAchievementEntity> findByUserIdAndAchievementId(Long userId, Long achievementId);
    long countByUserIdAndStatus(Long userId, String status);
    void deleteByUserId(Long userId);
}
