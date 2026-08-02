package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdGoalEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.entity.UserEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    private static final Long HOUSEHOLD_ID = 1L;

    @Mock private HouseholdRepository householdRepository;
    @Mock private HouseholdMemberRepository memberRepository;
    @Mock private HouseholdInvitationRepository invitationRepository;
    @Mock private SharedTransactionRepository sharedTransactionRepository;
    @Mock private SharedTransactionShareRepository shareRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private HouseholdBudgetRepository budgetRepository;
    @Mock private HouseholdGoalRepository goalRepository;
    @Mock private HouseholdGoalContributionRepository goalContributionRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private HouseholdService householdService;

    @BeforeEach
    void setUp() {
        householdService = new HouseholdService(householdRepository, memberRepository, invitationRepository,
                sharedTransactionRepository, shareRepository, settlementRepository, budgetRepository, goalRepository,
                goalContributionRepository, userRepository, notificationService);
    }

    private HouseholdEntity household(Long id, Long ownerId) {
        HouseholdEntity h = new HouseholdEntity();
        h.setId(id);
        h.setName("The Household");
        h.setOwnerId(ownerId);
        return h;
    }

    private HouseholdMemberEntity member(Long householdId, Long userId, String role) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(householdId);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    // ===================== currentMembership / requireHousehold =====================

    @Test
    void currentMembershipReturnsNullWhenTheUserBelongsToNoHousehold() {
        when(memberRepository.findByUserId(42L)).thenReturn(Optional.empty());

        assertNull(householdService.currentMembership(42L));
    }

    @Test
    void requireHouseholdThrowsNotFoundWhenTheHouseholdDoesNotExist() {
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.empty());

        FamilyException ex = assertThrows(FamilyException.class, () -> householdService.requireHousehold(HOUSEHOLD_ID));
        assertEquals("NOT_FOUND", ex.getErrorTag());
    }

    // ===================== create =====================

    @Test
    void createRejectsABlankName() {
        FamilyException ex = assertThrows(FamilyException.class, () -> householdService.create(1L, "   "));
        assertEquals("BAD_REQUEST", ex.getErrorTag());
    }

    @Test
    void createRejectsAUserWhoAlreadyBelongsToAHousehold() {
        when(memberRepository.findByUserId(1L)).thenReturn(Optional.of(member(5L, 1L, "MEMBER")));

        FamilyException ex = assertThrows(FamilyException.class, () -> householdService.create(1L, "New House"));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void createSavesTheHouseholdAndMakesTheCreatorAnAdminMember() {
        when(memberRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(householdRepository.save(any())).thenAnswer(inv -> {
            HouseholdEntity h = inv.getArgument(0);
            h.setId(HOUSEHOLD_ID);
            return h;
        });

        HouseholdEntity saved = householdService.create(1L, "  My Household  ");

        assertEquals("My Household", saved.getName(), "name must be trimmed");
        assertEquals(1L, saved.getOwnerId());
        ArgumentCaptor<HouseholdMemberEntity> captor = ArgumentCaptor.forClass(HouseholdMemberEntity.class);
        verify(memberRepository).save(captor.capture());
        assertEquals("ADMIN", captor.getValue().getRole());
        assertEquals(HOUSEHOLD_ID, captor.getValue().getHouseholdId());
        assertEquals(1L, captor.getValue().getUserId());
    }

    // ===================== update =====================

    @Test
    void updateRejectsABlankName() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        assertThrows(FamilyException.class, () -> householdService.update(h, ""));
    }

    @Test
    void updateTrimsAndSavesTheNewName() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        when(householdRepository.save(h)).thenReturn(h);

        HouseholdEntity result = householdService.update(h, "  Renamed  ");

        assertEquals("Renamed", result.getName());
    }

    // ===================== delete =====================

    @Test
    void deleteCascadesEveryHouseholdScopedTableBeforeDeletingTheHouseholdItself() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setId(9L);
        when(sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(HOUSEHOLD_ID)).thenReturn(List.of(tx));
        HouseholdGoalEntity goal = new HouseholdGoalEntity();
        goal.setId(3L);
        when(goalRepository.findByHouseholdIdOrderByCreatedAtDesc(HOUSEHOLD_ID)).thenReturn(List.of(goal));

        householdService.delete(h);

        verify(settlementRepository).deleteByHouseholdId(HOUSEHOLD_ID);
        verify(shareRepository).deleteBySharedTransactionId(9L);
        verify(sharedTransactionRepository).deleteByHouseholdId(HOUSEHOLD_ID);
        verify(goalContributionRepository).deleteByHouseholdGoalId(3L);
        verify(goalRepository).deleteByHouseholdId(HOUSEHOLD_ID);
        verify(budgetRepository).deleteByHouseholdId(HOUSEHOLD_ID);
        verify(invitationRepository).deleteByHouseholdId(HOUSEHOLD_ID);
        verify(memberRepository).deleteByHouseholdId(HOUSEHOLD_ID);
        verify(householdRepository).deleteById(HOUSEHOLD_ID);
    }

    // ===================== transferOwnership =====================

    @Test
    void transferOwnershipRejectsANonMemberAsTheNewOwner() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 99L)).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> householdService.transferOwnership(h, 99L));
    }

    @Test
    void transferOwnershipDemotesTheOldOwnerAndPromotesTheNewOne() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        HouseholdMemberEntity newOwner = member(HOUSEHOLD_ID, 2L, "MEMBER");
        HouseholdMemberEntity oldOwner = member(HOUSEHOLD_ID, 1L, "ADMIN");
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 2L)).thenReturn(Optional.of(newOwner));
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 1L)).thenReturn(Optional.of(oldOwner));
        when(householdRepository.save(h)).thenReturn(h);

        householdService.transferOwnership(h, 2L);

        assertEquals("MEMBER", oldOwner.getRole());
        assertEquals("ADMIN", newOwner.getRole());
        assertEquals(2L, h.getOwnerId());
        verify(memberRepository).save(oldOwner);
        verify(memberRepository).save(newOwner);
    }

    // ===================== leave =====================

    @Test
    void leaveByAPlainMemberOnlyRemovesTheirOwnMembership() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        HouseholdMemberEntity plain = member(HOUSEHOLD_ID, 2L, "MEMBER");
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 2L)).thenReturn(Optional.of(plain));

        householdService.leave(h, 2L);

        verify(memberRepository).delete(plain);
        verify(householdRepository, never()).deleteById(any());
        verify(memberRepository, never()).findByHouseholdIdOrderByJoinedAtAsc(any());
    }

    @Test
    void leaveByTheOwnerTransfersOwnershipToTheEarliestJoinedOtherMember() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        HouseholdMemberEntity owner = member(HOUSEHOLD_ID, 1L, "ADMIN");
        HouseholdMemberEntity earliestOther = member(HOUSEHOLD_ID, 2L, "MEMBER");
        HouseholdMemberEntity laterOther = member(HOUSEHOLD_ID, 3L, "MEMBER");
        when(memberRepository.findByHouseholdIdOrderByJoinedAtAsc(HOUSEHOLD_ID)).thenReturn(List.of(owner, earliestOther, laterOther));
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 2L)).thenReturn(Optional.of(earliestOther));
        lenient().when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 1L)).thenReturn(Optional.of(owner));
        when(householdRepository.save(h)).thenReturn(h);
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 1L)).thenReturn(Optional.of(owner));

        householdService.leave(h, 1L);

        assertEquals(2L, h.getOwnerId(), "ownership must transfer to the earliest-joined remaining member");
        verify(memberRepository).delete(owner);
        verify(householdRepository, never()).deleteById(any());
    }

    @Test
    void leaveByTheOwnerDeletesTheHouseholdWhenTheyAreTheLastMember() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        HouseholdMemberEntity owner = member(HOUSEHOLD_ID, 1L, "ADMIN");
        when(memberRepository.findByHouseholdIdOrderByJoinedAtAsc(HOUSEHOLD_ID)).thenReturn(List.of(owner));
        when(sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(HOUSEHOLD_ID)).thenReturn(List.of());
        when(goalRepository.findByHouseholdIdOrderByCreatedAtDesc(HOUSEHOLD_ID)).thenReturn(List.of());

        householdService.leave(h, 1L);

        verify(householdRepository).deleteById(HOUSEHOLD_ID);
        // delete() short-circuits before the individual per-member removal at the end of leave().
        verify(memberRepository, never()).findByHouseholdIdAndUserId(HOUSEHOLD_ID, 1L);
    }

    // ===================== removeMember =====================

    @Test
    void removeMemberRefusesToRemoveTheOwner() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);

        FamilyException ex = assertThrows(FamilyException.class, () -> householdService.removeMember(h, 1L));
        assertEquals("BAD_REQUEST", ex.getErrorTag());
    }

    @Test
    void removeMemberThrowsNotFoundWhenTargetIsntAMember() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 5L)).thenReturn(Optional.empty());

        FamilyException ex = assertThrows(FamilyException.class, () -> householdService.removeMember(h, 5L));
        assertEquals("NOT_FOUND", ex.getErrorTag());
    }

    @Test
    void removeMemberDeletesTheMembershipAndNotifiesTheRemovedUser() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        HouseholdMemberEntity target = member(HOUSEHOLD_ID, 5L, "MEMBER");
        when(memberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, 5L)).thenReturn(Optional.of(target));

        householdService.removeMember(h, 5L);

        verify(memberRepository).delete(target);
        verify(notificationService).create(eq(5L), eq("HOUSEHOLD_MEMBER_REMOVED"), anyString(), anyString(), eq("HOUSEHOLD"), eq(HOUSEHOLD_ID));
    }

    // ===================== toResponse =====================

    @Test
    void toResponseResolvesTheRequestingUsersOwnRoleAndMapsAllMembers() {
        HouseholdEntity h = household(HOUSEHOLD_ID, 1L);
        HouseholdMemberEntity m1 = member(HOUSEHOLD_ID, 1L, "ADMIN");
        HouseholdMemberEntity m2 = member(HOUSEHOLD_ID, 2L, "MEMBER");
        when(memberRepository.findByHouseholdIdOrderByJoinedAtAsc(HOUSEHOLD_ID)).thenReturn(List.of(m1, m2));
        UserEntity u1 = new UserEntity();
        u1.setId(1L);
        u1.setFullName("Alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        HouseholdResponse response = householdService.toResponse(h, 2L);

        assertTrue(response.hasHousehold());
        assertEquals("MEMBER", response.myRole());
        assertEquals(2, response.members().size());
        assertEquals("Alice", response.members().get(0).fullName());
        assertNull(response.members().get(1).fullName(), "a member whose user record is missing degrades gracefully to null fields, not an exception");
    }
}
