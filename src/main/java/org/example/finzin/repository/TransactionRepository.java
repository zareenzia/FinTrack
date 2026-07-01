package org.example.finzin.repository;

import org.example.finzin.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> findByCategory_IdOrderByDateDesc(Long categoryId);
    
    List<TransactionEntity> findByTransactionTypeOrderByDateDesc(String type);
    
    @Query("SELECT SUM(t.amount) FROM TransactionEntity t WHERE t.transactionType = :type")
    Double sumByTransactionType(@Param("type") String type);
}
