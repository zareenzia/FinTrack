package org.example.finzin.repository;

import org.example.finzin.entity.ReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<ReceiptEntity, Long> {
    Optional<ReceiptEntity> findByIdAndUserId(Long id, Long userId);
    List<ReceiptEntity> findByUserIdAndTransactionIdIn(Long userId, List<Long> transactionIds);
    List<ReceiptEntity> findByCreatedAtBefore(LocalDateTime cutoff);
    List<ReceiptEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
