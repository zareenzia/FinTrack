package org.example.finzin.repository;

import org.example.finzin.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {
    List<SubscriptionEntity> findByUserId(Long userId);
    List<SubscriptionEntity> findByUserIdAndStatus(Long userId, String status);
}
