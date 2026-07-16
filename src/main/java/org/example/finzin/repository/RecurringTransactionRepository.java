package org.example.finzin.repository;

import org.example.finzin.entity.RecurringTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransactionEntity, Long> {
    List<RecurringTransactionEntity> findByUserId(Long userId);
    List<RecurringTransactionEntity> findByUserIdAndStatus(Long userId, String status);
    List<RecurringTransactionEntity> findByStatusAndNextExecutionDateLessThanEqual(String status, LocalDate date);
    List<RecurringTransactionEntity> findByUserIdAndStatusAndNextExecutionDateLessThanEqual(Long userId, String status, LocalDate date);
}
