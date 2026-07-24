package org.example.finzin.repository;

import org.example.finzin.entity.UserChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserChallengeRepository extends JpaRepository<UserChallengeEntity, Long> {
    boolean existsByUserIdAndPeriodKey(Long userId, String periodKey);
    List<UserChallengeEntity> findByUserIdAndPeriodKey(Long userId, String periodKey);
    Optional<UserChallengeEntity> findByUserIdAndChallengeIdAndPeriodKey(Long userId, Long challengeId, String periodKey);
    List<UserChallengeEntity> findByUserIdAndStatus(Long userId, String status);
    void deleteByUserId(Long userId);
}
