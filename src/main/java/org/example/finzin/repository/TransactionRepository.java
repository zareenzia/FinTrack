package org.example.finzin.repository;

import org.example.finzin.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> findByCategory_IdOrderByDateDesc(Long categoryId);
    List<TransactionEntity> findByTransactionTypeOrderByDateDesc(String type);
    List<TransactionEntity> findByUserId(Long userId);
    List<TransactionEntity> findByUserIdOrderByDateDesc(Long userId);
    long countByUserId(Long userId);
    
    @Query("SELECT SUM(t.amount) FROM TransactionEntity t WHERE t.transactionType = :type")
    Double sumByTransactionType(@Param("type") String type);
    
    @Query("SELECT SUM(t.amount) FROM TransactionEntity t WHERE t.userId = :userId AND t.transactionType = :type")
    Double sumByUserIdAndTransactionType(@Param("userId") Long userId, @Param("type") String type);
    
    @Query("SELECT SUM(t.amount) FROM TransactionEntity t WHERE t.userId = :userId AND t.transactionType = :type")
    Double sumByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    boolean existsBySourceAccountIdOrDestinationAccountId(Long sourceAccountId, Long destinationAccountId);

    @Query("SELECT SUM(t.amount) FROM TransactionEntity t WHERE t.userId = :userId AND t.transactionType = :type " +
            "AND t.category.id = :categoryId AND t.date >= :start AND t.date < :end")
    Double sumByUserIdAndTypeAndCategoryAndDateRange(@Param("userId") Long userId, @Param("type") String type,
                                                      @Param("categoryId") Long categoryId,
                                                      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(t.amount) FROM TransactionEntity t WHERE t.userId = :userId AND t.transactionType = :type " +
            "AND t.date >= :start AND t.date < :end")
    Double sumByUserIdAndTypeAndDateRange(@Param("userId") Long userId, @Param("type") String type,
                                          @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId AND t.date >= :start AND t.date < :end")
    List<TransactionEntity> findByUserIdAndDateRange(@Param("userId") Long userId,
                                                      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<TransactionEntity> findBySourceAccountIdOrDestinationAccountIdOrderByDateAsc(Long sourceAccountId, Long destinationAccountId);
}

