package org.example.finzin.repository;

import org.example.finzin.entity.PurchaseItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItemEntity, Long> {
    List<PurchaseItemEntity> findByUserId(Long userId);
    Optional<PurchaseItemEntity> findByIdAndUserId(Long id, Long userId);
    List<PurchaseItemEntity> findByUserIdAndStatusIn(Long userId, List<String> statuses);
    List<PurchaseItemEntity> findByStatusIn(List<String> statuses);
    void deleteByUserId(Long userId);
}
