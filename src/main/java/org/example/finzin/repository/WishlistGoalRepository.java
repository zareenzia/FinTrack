package org.example.finzin.repository;

import org.example.finzin.entity.WishlistGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WishlistGoalRepository extends JpaRepository<WishlistGoalEntity, Long> {
    List<WishlistGoalEntity> findByUserId(Long userId);
    List<WishlistGoalEntity> findByUserIdAndStatus(Long userId, String status);
    void deleteByUserId(Long userId);
}
