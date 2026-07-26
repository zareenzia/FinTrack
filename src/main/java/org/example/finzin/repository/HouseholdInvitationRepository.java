package org.example.finzin.repository;

import org.example.finzin.entity.HouseholdInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitationEntity, Long> {
    List<HouseholdInvitationEntity> findByHouseholdId(Long householdId);
    List<HouseholdInvitationEntity> findByHouseholdIdAndStatus(Long householdId, String status);
    List<HouseholdInvitationEntity> findByInviteeUserIdAndStatus(Long inviteeUserId, String status);
    List<HouseholdInvitationEntity> findByInvitedByUserId(Long invitedByUserId);
    List<HouseholdInvitationEntity> findByInviteeUserId(Long inviteeUserId);
    void deleteByHouseholdId(Long householdId);
    void deleteByInvitedByUserId(Long invitedByUserId);
    void deleteByInviteeUserId(Long inviteeUserId);
}
