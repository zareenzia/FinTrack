package org.example.finzin.repository;

import org.example.finzin.entity.SharedTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SharedTransactionRepository extends JpaRepository<SharedTransactionEntity, Long> {
    List<SharedTransactionEntity> findByHouseholdIdOrderByExpenseDateDesc(Long householdId);
    List<SharedTransactionEntity> findByHouseholdIdAndExpenseDateBetween(Long householdId, LocalDate start, LocalDate end);
    Optional<SharedTransactionEntity> findByTransactionId(Long transactionId);
    void deleteByHouseholdId(Long householdId);
}
