package org.example.finzin.repository;

import org.example.finzin.entity.SharedTransactionShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedTransactionShareRepository extends JpaRepository<SharedTransactionShareEntity, Long> {
    List<SharedTransactionShareEntity> findBySharedTransactionId(Long sharedTransactionId);
    List<SharedTransactionShareEntity> findBySharedTransactionIdIn(List<Long> sharedTransactionIds);
    void deleteBySharedTransactionId(Long sharedTransactionId);
}
