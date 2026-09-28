package org.example.finzin.family;

import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.entity.SharedTransactionShareEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.family.dto.SharedExpenseRequest;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.SharedTransactionRepository;
import org.example.finzin.repository.SharedTransactionShareRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedExpenseServiceTest {

    private static final Long HOUSEHOLD_ID = 1L;
    private static final Long PAYER_ID = 10L;
    private static final Long OTHER_MEMBER_ID = 20L;

    @Mock private SharedTransactionRepository sharedTransactionRepository;
    @Mock private SharedTransactionShareRepository shareRepository;
    @Mock private HouseholdMemberRepository memberRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private SharedExpenseService sharedExpenseService;

    @BeforeEach
    void setUp() {
        sharedExpenseService = new SharedExpenseService(sharedTransactionRepository, shareRepository, memberRepository,
                transactionRepository, userRepository, notificationService);
    }

    private HouseholdEntity household() {
        HouseholdEntity h = new HouseholdEntity();
        h.setId(HOUSEHOLD_ID);
        h.setName("Fam");
        return h;
    }

    private HouseholdMemberEntity member(Long userId) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(HOUSEHOLD_ID);
        m.setUserId(userId);
        return m;
    }

    private TransactionEntity personalTransaction(Long id, Long userId, double amount) {
        CategoryEntity category = new CategoryEntity();
        TransactionEntity tx = new TransactionEntity(userId, amount, "Groceries run", category, "expense", LocalDateTime.now(), LocalDateTime.now());
        tx.setId(id);
        return tx;
    }

    private void givenBothAreHouseholdMembers() {
        when(memberRepository.findByHouseholdId(HOUSEHOLD_ID)).thenReturn(List.of(member(PAYER_ID), member(OTHER_MEMBER_ID)));
    }

    // ===================== requireSharedTransaction =====================

    @Test
    void requireSharedTransactionThrowsNotFoundWhenMissing() {
        when(sharedTransactionRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(FamilyException.class, () -> sharedExpenseService.requireSharedTransaction(1L, HOUSEHOLD_ID));
    }

    @Test
    void requireSharedTransactionThrowsNotFoundWhenItBelongsToAnotherHousehold() {
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setId(1L);
        tx.setHouseholdId(999L);
        when(sharedTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));

        assertThrows(FamilyException.class, () -> sharedExpenseService.requireSharedTransaction(1L, HOUSEHOLD_ID));
    }

    // ===================== createSharedExpense =====================

    @Test
    void createSharedExpenseRequiresATransactionId() {
        SharedExpenseRequest req = new SharedExpenseRequest(null, "EQUAL", List.of(new SharedExpenseRequest.ShareInput(PAYER_ID, null, null)));
        assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
    }

    @Test
    void createSharedExpenseRejectsATransactionThatDoesntBelongToThePayer() {
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.empty());
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "EQUAL", List.of(new SharedExpenseRequest.ShareInput(PAYER_ID, null, null)));

        assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
    }

    @Test
    void createSharedExpenseRejectsATransactionAlreadyShared() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.of(new SharedTransactionEntity()));
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "EQUAL", List.of(new SharedExpenseRequest.ShareInput(PAYER_ID, null, null)));

        FamilyException ex = assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void createSharedExpenseRejectsAnUnsupportedSplitMethod() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "HALF_HALF", List.of(new SharedExpenseRequest.ShareInput(PAYER_ID, null, null)));

        assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
    }

    @Test
    void createSharedExpenseRejectsAPayerWhoIsntAHouseholdMember() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        when(memberRepository.findByHouseholdId(HOUSEHOLD_ID)).thenReturn(List.of(member(OTHER_MEMBER_ID)));
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "EQUAL", List.of(new SharedExpenseRequest.ShareInput(PAYER_ID, null, null)));

        FamilyException ex = assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
        assertEquals("NOT_MEMBER", ex.getErrorTag());
    }

    @Test
    void createSharedExpenseRejectsAContributorWhoIsntAHouseholdMember() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        givenBothAreHouseholdMembers();
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "EQUAL", List.of(
                new SharedExpenseRequest.ShareInput(PAYER_ID, null, null),
                new SharedExpenseRequest.ShareInput(999L, null, null)));

        assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
    }

    @Test
    void createSharedExpenseRejectsDuplicateContributorsInTheShareList() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        givenBothAreHouseholdMembers();
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "EQUAL", List.of(
                new SharedExpenseRequest.ShareInput(PAYER_ID, null, null),
                new SharedExpenseRequest.ShareInput(PAYER_ID, null, null)));

        assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
    }

    @Test
    void createSharedExpenseSplitsEquallyAndPutsAnyRoundingRemainderOnTheLastContributor() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        givenBothAreHouseholdMembers();
        when(memberRepository.findByHouseholdId(HOUSEHOLD_ID)).thenReturn(List.of(member(PAYER_ID), member(OTHER_MEMBER_ID), member(30L)));
        when(sharedTransactionRepository.save(any())).thenAnswer(inv -> {
            SharedTransactionEntity e = inv.getArgument(0);
            e.setId(77L);
            return e;
        });
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "EQUAL", List.of(
                new SharedExpenseRequest.ShareInput(PAYER_ID, null, null),
                new SharedExpenseRequest.ShareInput(OTHER_MEMBER_ID, null, null),
                new SharedExpenseRequest.ShareInput(30L, null, null)));

        sharedExpenseService.createSharedExpense(household(), PAYER_ID, req);

        ArgumentCaptor<SharedTransactionShareEntity> captor = ArgumentCaptor.forClass(SharedTransactionShareEntity.class);
        verify(shareRepository, times(3)).save(captor.capture());
        double total = captor.getAllValues().stream().mapToDouble(SharedTransactionShareEntity::getShareAmount).sum();
        assertEquals(100.0, total, 0.001, "the three shares (with rounding) must still add up to the exact original total");
    }

    @Test
    void createSharedExpenseRejectsPercentagesThatDontAddUpToOneHundred() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        givenBothAreHouseholdMembers();
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "PERCENTAGE", List.of(
                new SharedExpenseRequest.ShareInput(PAYER_ID, null, 50.0),
                new SharedExpenseRequest.ShareInput(OTHER_MEMBER_ID, null, 30.0)));

        assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
    }

    @Test
    void createSharedExpenseSplitsByPercentageWhenPercentagesAddToOneHundred() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 200.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        givenBothAreHouseholdMembers();
        when(sharedTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "PERCENTAGE", List.of(
                new SharedExpenseRequest.ShareInput(PAYER_ID, null, 25.0),
                new SharedExpenseRequest.ShareInput(OTHER_MEMBER_ID, null, 75.0)));

        sharedExpenseService.createSharedExpense(household(), PAYER_ID, req);

        ArgumentCaptor<SharedTransactionShareEntity> captor = ArgumentCaptor.forClass(SharedTransactionShareEntity.class);
        verify(shareRepository, times(2)).save(captor.capture());
        SharedTransactionShareEntity otherShare = captor.getAllValues().stream()
                .filter(s -> s.getUserId().equals(OTHER_MEMBER_ID)).findFirst().orElseThrow();
        assertEquals(150.0, otherShare.getShareAmount(), 0.001);
        assertEquals(false, otherShare.getIsPayer());
    }

    @Test
    void createSharedExpenseRejectsFixedAmountsThatDontSumToTheTotal() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        givenBothAreHouseholdMembers();
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "FIXED_AMOUNT", List.of(
                new SharedExpenseRequest.ShareInput(PAYER_ID, 30.0, null),
                new SharedExpenseRequest.ShareInput(OTHER_MEMBER_ID, 30.0, null)));

        assertThrows(FamilyException.class, () -> sharedExpenseService.createSharedExpense(household(), PAYER_ID, req));
    }

    @Test
    void createSharedExpenseOnlyNotifiesNonPayerContributorsAndNotUninvolvedMembers() {
        TransactionEntity tx = personalTransaction(5L, PAYER_ID, 100.0);
        when(transactionRepository.findByIdAndUserId(5L, PAYER_ID)).thenReturn(Optional.of(tx));
        when(sharedTransactionRepository.findByTransactionId(5L)).thenReturn(Optional.empty());
        when(memberRepository.findByHouseholdId(HOUSEHOLD_ID)).thenReturn(List.of(member(PAYER_ID), member(OTHER_MEMBER_ID), member(999L)));
        when(sharedTransactionRepository.save(any())).thenAnswer(inv -> {
            SharedTransactionEntity e = inv.getArgument(0);
            e.setId(77L);
            return e;
        });
        SharedExpenseRequest req = new SharedExpenseRequest(5L, "EQUAL", List.of(
                new SharedExpenseRequest.ShareInput(PAYER_ID, null, null),
                new SharedExpenseRequest.ShareInput(OTHER_MEMBER_ID, null, null)));

        sharedExpenseService.createSharedExpense(household(), PAYER_ID, req);

        verify(notificationService, times(1)).create(any(), any(), any(), any(), any(), any());
    }

    // ===================== unshareExpense =====================

    @Test
    void unshareExpenseDeletesTheSharesAndTheSharedTransactionButNeverTouchesTheRealTransaction() {
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setId(77L);

        sharedExpenseService.unshareExpense(tx);

        verify(shareRepository).deleteBySharedTransactionId(77L);
        verify(sharedTransactionRepository).deleteById(77L);
        verify(transactionRepository, times(0)).deleteById(any());
    }

    // ===================== listForHousehold =====================

    @Test
    void listForHouseholdUsesTheDateRangeQueryWhenBothDatesAreProvided() {
        java.time.LocalDate start = java.time.LocalDate.of(2026, 1, 1);
        java.time.LocalDate end = java.time.LocalDate.of(2026, 1, 31);
        lenient().when(sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(HOUSEHOLD_ID, start, end)).thenReturn(List.of());

        sharedExpenseService.listForHousehold(HOUSEHOLD_ID, start, end);

        verify(sharedTransactionRepository).findByHouseholdIdAndExpenseDateBetween(HOUSEHOLD_ID, start, end);
        verify(sharedTransactionRepository, times(0)).findByHouseholdIdOrderByExpenseDateDesc(any());
    }

    @Test
    void listForHouseholdFallsBackToAllExpensesWhenNoDateRangeIsProvided() {
        lenient().when(sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(HOUSEHOLD_ID)).thenReturn(List.of());

        sharedExpenseService.listForHousehold(HOUSEHOLD_ID, null, null);

        verify(sharedTransactionRepository).findByHouseholdIdOrderByExpenseDateDesc(HOUSEHOLD_ID);
    }

    // ===================== toResponse =====================

    @Test
    void toResponseMapsSharesAndFlagsThePayerCorrectly() {
        SharedTransactionEntity tx = new SharedTransactionEntity();
        tx.setId(77L);
        tx.setHouseholdId(HOUSEHOLD_ID);
        tx.setPayerUserId(PAYER_ID);
        tx.setTotalAmount(100.0);
        tx.setSplitMethod("EQUAL");
        tx.setDescription("Dinner");
        when(shareRepository.findBySharedTransactionId(77L)).thenReturn(List.of(
                shareOf(PAYER_ID, 50.0, true), shareOf(OTHER_MEMBER_ID, 50.0, false)));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        var response = sharedExpenseService.toResponse(tx);

        assertEquals(2, response.shares().size());
        assertEquals(true, response.shares().stream().filter(s -> s.userId().equals(PAYER_ID)).findFirst().orElseThrow().isPayer());
    }

    private SharedTransactionShareEntity shareOf(Long userId, double amount, boolean isPayer) {
        SharedTransactionShareEntity s = new SharedTransactionShareEntity();
        s.setUserId(userId);
        s.setShareAmount(amount);
        s.setIsPayer(isPayer);
        return s;
    }
}
