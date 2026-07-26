package org.example.finzin.repository;

import org.example.finzin.entity.SettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<SettlementEntity, Long> {
    List<SettlementEntity> findByHouseholdIdOrderBySettledAtDesc(Long householdId);
    void deleteByHouseholdId(Long householdId);
}
