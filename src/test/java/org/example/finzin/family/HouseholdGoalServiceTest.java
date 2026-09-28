package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdGoalContributionEntity;
import org.example.finzin.entity.HouseholdGoalEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.family.dto.GoalContributionRequest;
import org.example.finzin.family.dto.HouseholdGoalRequest;
import org.example.finzin.repository.HouseholdGoalContributionRepository;
import org.example.finzin.repository.HouseholdGoalRepository;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdGoalServiceTest {

    private static final Long HOUSEHOLD_ID = 1L;
    private static final Long GOAL_ID = 5L;

    @Mock private HouseholdGoalRepository goalRepository;
    @Mock private HouseholdGoalContributionRepository contributionRepository;
    @Mock private HouseholdMemberRepository memberRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private HouseholdGoalService goalService;

    @BeforeEach
    void setUp() {
        goalService = new HouseholdGoalService(goalRepository, contributionRepository, memberRepository,
                transactionRepository, userRepository, notificationService);
    }

    private HouseholdEntity household() {
        HouseholdEntity h = new HouseholdEntity();
        h.setId(HOUSEHOLD_ID);
        h.setName("Fam");
        return h;
    }

    private HouseholdGoalEntity goal(double target, String status) {
        HouseholdGoalEntity g = new HouseholdGoalEntity();
        g.setId(GOAL_ID);
        g.setHouseholdId(HOUSEHOLD_ID);
        g.setName("Vacation");
        g.setTargetAmount(target);
        g.setStatus(status);
        g.setCreatedByUserId(1L);
        return g;
    }

    private TransactionEntity savingsTransaction(Long id, Long userId, double amount) {
        TransactionEntity tx = new TransactionEntity(userId, amount, "Saved", null, "savings", LocalDateTime.now(), LocalDateTime.now());
        tx.setId(id);
        return tx;
    }

    // ===================== requireGoal =====================

    @Test
    void requireGoalThrowsNotFoundWhenMissing() {
        when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.empty());
        assertThrows(FamilyException.class, () -> goalService.requireGoal(GOAL_ID, HOUSEHOLD_ID));
    }

    @Test
    void requireGoalThrowsNotFoundWhenItBelongsToAnotherHousehold() {
        HouseholdGoalEntity other = goal(1000.0, "ACTIVE");
        other.setHouseholdId(999L);
        when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(other));

        assertThrows(FamilyException.class, () -> goalService.requireGoal(GOAL_ID, HOUSEHOLD_ID));
    }

    // ===================== create =====================

    @Test
    void createRejectsABlankName() {
        assertThrows(FamilyException.class, () -> goalService.create(household(), 1L, new HouseholdGoalRequest("  ", 1000.0, null)));
    }

    @Test
    void createRejectsANonPositiveTargetAmount() {
        assertThrows(FamilyException.class, () -> goalService.create(household(), 1L, new HouseholdGoalRequest("Trip", 0.0, null)));
    }

    @Test
    void createRejectsAMalformedTargetDate() {
        assertThrows(FamilyException.class, () -> goalService.create(household(), 1L, new HouseholdGoalRequest("Trip", 1000.0, "not-a-date")));
    }

    @Test
    void createSavesAnActiveGoal() {
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HouseholdGoalEntity saved = goalService.create(household(), 7L, new HouseholdGoalRequest("  Trip  ", 5000.0, "2027-01-01"));

        assertEquals("Trip", saved.getName());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(7L, saved.getCreatedByUserId());
    }

    // ===================== update / archive =====================

    @Test
    void updateRejectsInvalidInputTheSameWayCreateDoes() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        assertThrows(FamilyException.class, () -> goalService.update(g, new HouseholdGoalRequest(null, 1000.0, null)));
    }

    @Test
    void archiveSetsStatusToArchived() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        when(goalRepository.save(g)).thenReturn(g);

        HouseholdGoalEntity result = goalService.archive(g);

        assertEquals("ARCHIVED", result.getStatus());
    }

    // ===================== delete =====================

    @Test
    void deleteRefusesAGoalThatAlreadyHasContributions() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        when(contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(GOAL_ID))
                .thenReturn(List.of(new HouseholdGoalContributionEntity()));

        FamilyException ex = assertThrows(FamilyException.class, () -> goalService.delete(g));
        assertEquals("CONFLICT", ex.getErrorTag());
        verify(goalRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesAGoalWithNoContributionHistory() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        when(contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(GOAL_ID)).thenReturn(List.of());

        goalService.delete(g);

        verify(goalRepository).delete(g);
    }

    // ===================== contribute =====================

    @Test
    void contributeRejectsAnArchivedGoal() {
        HouseholdGoalEntity g = goal(1000.0, "ARCHIVED");
        FamilyException ex = assertThrows(FamilyException.class,
                () -> goalService.contribute(household(), g, 2L, new GoalContributionRequest(10L, null)));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void contributeRequiresATransactionId() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        assertThrows(FamilyException.class, () -> goalService.contribute(household(), g, 2L, new GoalContributionRequest(null, null)));
    }

    @Test
    void contributeRejectsATransactionThatDoesntBelongToTheContributor() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        when(transactionRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> goalService.contribute(household(), g, 2L, new GoalContributionRequest(10L, null)));
    }

    @Test
    void contributeRejectsANonSavingsTransaction() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        TransactionEntity expenseTx = new TransactionEntity(2L, 100.0, "Coffee", null, "expense", LocalDateTime.now(), LocalDateTime.now());
        expenseTx.setId(10L);
        when(transactionRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(expenseTx));

        FamilyException ex = assertThrows(FamilyException.class,
                () -> goalService.contribute(household(), g, 2L, new GoalContributionRequest(10L, null)));
        assertEquals("BAD_REQUEST", ex.getErrorTag());
    }

    @Test
    void contributeRejectsATransactionAlreadyContributedElsewhere() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        TransactionEntity tx = savingsTransaction(10L, 2L, 500.0);
        when(transactionRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(tx));
        when(contributionRepository.findByTransactionId(10L)).thenReturn(Optional.of(new HouseholdGoalContributionEntity()));

        FamilyException ex = assertThrows(FamilyException.class,
                () -> goalService.contribute(household(), g, 2L, new GoalContributionRequest(10L, null)));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void contributeRecordsTheContributionAndNotifiesOtherMembersWithoutAchievingTheGoal() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        TransactionEntity tx = savingsTransaction(10L, 2L, 300.0);
        when(transactionRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(tx));
        when(contributionRepository.findByTransactionId(10L)).thenReturn(Optional.empty());
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(GOAL_ID))
                .thenReturn(List.of(contributionOf(300.0)));
        when(memberRepository.findByHouseholdId(HOUSEHOLD_ID)).thenReturn(List.of(
                memberOf(2L), memberOf(3L)));

        goalService.contribute(household(), g, 2L, new GoalContributionRequest(10L, "note"));

        assertEquals("ACTIVE", g.getStatus(), "300 of 1000 must not mark the goal achieved");
        verify(goalRepository, never()).save(g);
        verify(notificationService).create(eq(3L), eq("HOUSEHOLD_GOAL_CONTRIBUTION"), anyString(), anyString(), eq("HOUSEHOLD_GOAL"), eq(GOAL_ID));
        verify(notificationService, never()).create(eq(2L), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void contributeMarksTheGoalAchievedAndNotifiesWithTheAchievementMessageWhenTargetIsReached() {
        HouseholdGoalEntity g = goal(1000.0, "ACTIVE");
        TransactionEntity tx = savingsTransaction(10L, 2L, 700.0);
        when(transactionRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(tx));
        when(contributionRepository.findByTransactionId(10L)).thenReturn(Optional.empty());
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(GOAL_ID))
                .thenReturn(List.of(contributionOf(300.0), contributionOf(700.0)));
        when(goalRepository.save(g)).thenReturn(g);
        when(memberRepository.findByHouseholdId(HOUSEHOLD_ID)).thenReturn(List.of(memberOf(2L), memberOf(3L)));

        goalService.contribute(household(), g, 2L, new GoalContributionRequest(10L, null));

        assertEquals("ACHIEVED", g.getStatus());
        verify(notificationService).create(eq(3L), eq("HOUSEHOLD_GOAL_ACHIEVED"), anyString(), anyString(), eq("HOUSEHOLD_GOAL"), eq(GOAL_ID));
    }

    private HouseholdGoalContributionEntity contributionOf(double amount) {
        HouseholdGoalContributionEntity c = new HouseholdGoalContributionEntity();
        c.setAmount(amount);
        return c;
    }

    private HouseholdMemberEntity memberOf(Long userId) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(HOUSEHOLD_ID);
        m.setUserId(userId);
        return m;
    }

    // ===================== removeContribution =====================

    @Test
    void removeContributionRevertsAnAchievedGoalBackToActiveWhenTheRemainingTotalDropsBelowTarget() {
        HouseholdGoalEntity g = goal(1000.0, "ACHIEVED");
        HouseholdGoalContributionEntity toRemove = contributionOf(700.0);
        when(contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(GOAL_ID)).thenReturn(List.of(contributionOf(300.0)));
        when(goalRepository.save(g)).thenReturn(g);

        goalService.removeContribution(g, toRemove);

        verify(contributionRepository).delete(toRemove);
        assertEquals("ACTIVE", g.getStatus());
    }

    @Test
    void removeContributionLeavesAnAchievedGoalAloneIfStillOverTarget() {
        HouseholdGoalEntity g = goal(1000.0, "ACHIEVED");
        HouseholdGoalContributionEntity toRemove = contributionOf(100.0);
        when(contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(GOAL_ID)).thenReturn(List.of(contributionOf(1200.0)));

        goalService.removeContribution(g, toRemove);

        assertEquals("ACHIEVED", g.getStatus());
        verify(goalRepository, never()).save(any());
    }

    // ===================== requireContribution =====================

    @Test
    void requireContributionThrowsNotFoundWhenItBelongsToADifferentGoal() {
        HouseholdGoalContributionEntity c = contributionOf(100.0);
        c.setHouseholdGoalId(999L);
        when(contributionRepository.findById(20L)).thenReturn(Optional.of(c));

        assertThrows(FamilyException.class, () -> goalService.requireContribution(20L, GOAL_ID));
    }
}
