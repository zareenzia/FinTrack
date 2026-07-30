package org.example.finzin.repository;

import org.example.finzin.entity.HouseholdMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMemberEntity, Long> {
    List<HouseholdMemberEntity> findByHouseholdId(Long householdId);
    List<HouseholdMemberEntity> findByHouseholdIdOrderByJoinedAtAsc(Long householdId);
    Optional<HouseholdMemberEntity> findByUserId(Long userId);
    Optional<HouseholdMemberEntity> findByHouseholdIdAndUserId(Long householdId, Long userId);
    void deleteByHouseholdId(Long householdId);
    void deleteByUserId(Long userId);
}
