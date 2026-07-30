package org.example.finzin.repository;

import org.example.finzin.entity.PurchaseItemActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseItemActivityRepository extends JpaRepository<PurchaseItemActivityEntity, Long> {
    List<PurchaseItemActivityEntity> findByPurchaseItemIdOrderByCreatedAtDesc(Long purchaseItemId);
    List<PurchaseItemActivityEntity> findByPurchaseItemIdAndActivityTypeOrderByCreatedAtAsc(Long purchaseItemId, String activityType);
    void deleteByPurchaseItemId(Long purchaseItemId);
    void deleteByUserId(Long userId);
}
