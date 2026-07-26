package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdGoalEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.family.dto.HouseholdMemberResponse;
import org.example.finzin.family.dto.HouseholdResponse;
import org.example.finzin.repository.HouseholdBudgetRepository;
import org.example.finzin.repository.HouseholdGoalContributionRepository;
import org.example.finzin.repository.HouseholdGoalRepository;
import org.example.finzin.repository.HouseholdInvitationRepository;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.HouseholdRepository;
import org.example.finzin.repository.SettlementRepository;
import org.example.finzin.repository.SharedTransactionRepository;
import org.example.finzin.repository.SharedTransactionShareRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final HouseholdInvitationRepository invitationRepository;
    private final SharedTransactionRepository sharedTransactionRepository;
    private final SharedTransactionShareRepository shareRepository;
    private final SettlementRepository settlementRepository;
    private final HouseholdBudgetRepository budgetRepository;
    private final HouseholdGoalRepository goalRepository;
    private final HouseholdGoalContributionRepository goalContributionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public HouseholdService(HouseholdRepository householdRepository, HouseholdMemberRepository memberRepository,
                             HouseholdInvitationRepository invitationRepository, SharedTransactionRepository sharedTransactionRepository,
                             SharedTransactionShareRepository shareRepository, SettlementRepository settlementRepository,
                             HouseholdBudgetRepository budgetRepository, HouseholdGoalRepository goalRepository,
                             HouseholdGoalContributionRepository goalContributionRepository,
                             UserRepository userRepository, NotificationService notificationService) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.sharedTransactionRepository = sharedTransactionRepository;
        this.shareRepository = shareRepository;
        this.settlementRepository = settlementRepository;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
        this.goalContributionRepository = goalContributionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public HouseholdMemberEntity currentMembership(Long userId) {
        return memberRepository.findByUserId(userId).orElse(null);
    }

    public HouseholdEntity requireHousehold(Long householdId) {
        return householdRepository.findById(householdId).orElseThrow(() -> FamilyException.notFound("Household"));
    }

    public HouseholdEntity create(Long userId, String name) {
        if (name == null || name.isBlank()) throw FamilyException.badRequest("Household name is required");
        if (memberRepository.findByUserId(userId).isPresent()) {
            throw FamilyException.conflict("You already belong to a household. Leave it first to create a new one.");
        }
        HouseholdEntity household = new HouseholdEntity();
        household.setName(name.trim());
        household.setOwnerId(userId);
        HouseholdEntity saved = householdRepository.save(household);

        HouseholdMemberEntity owner = new HouseholdMemberEntity();
        owner.setHouseholdId(saved.getId());
        owner.setUserId(userId);
        owner.setRole("ADMIN");
        memberRepository.save(owner);
        return saved;
    }

    public HouseholdEntity update(HouseholdEntity household, String name) {
        if (name == null || name.isBlank()) throw FamilyException.badRequest("Household name is required");
        household.setName(name.trim());
        return householdRepository.save(household);
    }

    @Transactional
    public void delete(HouseholdEntity household) {
        Long householdId = household.getId();
        settlementRepository.deleteByHouseholdId(householdId);
        for (SharedTransactionEntity tx : sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(householdId)) {
            shareRepository.deleteBySharedTransactionId(tx.getId());
        }
        sharedTransactionRepository.deleteByHouseholdId(householdId);
        for (HouseholdGoalEntity goal : goalRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId)) {
            goalContributionRepository.deleteByHouseholdGoalId(goal.getId());
        }
        goalRepository.deleteByHouseholdId(householdId);
        budgetRepository.deleteByHouseholdId(householdId);
        invitationRepository.deleteByHouseholdId(householdId);
        memberRepository.deleteByHouseholdId(householdId);
        householdRepository.deleteById(householdId);
    }

    @Transactional
    public void transferOwnership(HouseholdEntity household, Long newOwnerUserId) {
        HouseholdMemberEntity newOwnerMember = memberRepository.findByHouseholdIdAndUserId(household.getId(), newOwnerUserId)
                .orElseThrow(() -> FamilyException.badRequest("That user is not a member of this household."));
        memberRepository.findByHouseholdIdAndUserId(household.getId(), household.getOwnerId()).ifPresent(oldOwner -> {
            oldOwner.setRole("MEMBER");
            memberRepository.save(oldOwner);
        });
        newOwnerMember.setRole("ADMIN");
        memberRepository.save(newOwnerMember);
        household.setOwnerId(newOwnerUserId);
        householdRepository.save(household);
    }

    /** Self-initiated departure. If the owner leaves, ownership transfers to the earliest-joined other
     * member, or the household is deleted outright if they were the last one. */
    @Transactional
    public void leave(HouseholdEntity household, Long userId) {
        boolean isOwner = household.getOwnerId().equals(userId);
        if (isOwner) {
            List<HouseholdMemberEntity> others = memberRepository.findByHouseholdIdOrderByJoinedAtAsc(household.getId()).stream()
                    .filter(m -> !m.getUserId().equals(userId))
                    .collect(Collectors.toList());
            if (others.isEmpty()) {
                delete(household);
                return;
            }
            transferOwnership(household, others.get(0).getUserId());
        }
        memberRepository.findByHouseholdIdAndUserId(household.getId(), userId).ifPresent(memberRepository::delete);
    }

    /** Admin-initiated removal of another member. */
    public void removeMember(HouseholdEntity household, Long targetUserId) {
        if (household.getOwnerId().equals(targetUserId)) {
            throw FamilyException.badRequest("The household owner can't be removed — transfer ownership first.");
        }
        HouseholdMemberEntity target = memberRepository.findByHouseholdIdAndUserId(household.getId(), targetUserId)
                .orElseThrow(() -> FamilyException.notFound("Member"));
        memberRepository.delete(target);
        notificationService.create(targetUserId, "HOUSEHOLD_MEMBER_REMOVED",
                "Removed from Household",
                "You've been removed from \"" + household.getName() + "\".",
                "HOUSEHOLD", household.getId());
    }

    public HouseholdResponse toResponse(HouseholdEntity household, Long requestingUserId) {
        List<HouseholdMemberEntity> members = memberRepository.findByHouseholdIdOrderByJoinedAtAsc(household.getId());
        String myRole = members.stream()
                .filter(m -> m.getUserId().equals(requestingUserId))
                .map(HouseholdMemberEntity::getRole)
                .findFirst().orElse(null);
        List<HouseholdMemberResponse> memberResponses = members.stream().map(m -> {
            UserEntity user = userRepository.findById(m.getUserId()).orElse(null);
            return new HouseholdMemberResponse(
                    m.getUserId(),
                    user != null ? user.getFullName() : null,
                    user != null ? user.getUsername() : null,
                    user != null ? user.getEmail() : null,
                    m.getRole(),
                    m.getRelationshipLabel(),
                    m.getJoinedAt() != null ? m.getJoinedAt().toString() : null
            );
        }).collect(Collectors.toList());
        return new HouseholdResponse(true, household.getId(), household.getName(), household.getOwnerId(),
                myRole, memberResponses, household.getCreatedAt() != null ? household.getCreatedAt().toString() : null);
    }
}
