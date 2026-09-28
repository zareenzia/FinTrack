package org.example.finzin.service;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.BudgetTemplateEntity;
import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.entity.ReceiptEntity;
import org.example.finzin.entity.SavingsBudgetEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.family.HouseholdService;
import org.example.finzin.purchaseplanner.PurchaseItemImageStorageService;
import org.example.finzin.receipts.ReceiptStorageService;
import org.example.finzin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for AccountDeletionService — the real implementation behind
 * Settings &gt; Danger Zone &gt; Delete My Account. Because this class fans out to ~40
 * repositories, this suite doesn't attempt to verify every single deleteByUserId call; instead it
 * focuses on the branches that have actual logic (receipt/purchase-item file cleanup, shared
 * transaction unlinking, household membership handling, savings-budgets-per-plan and
 * template-categories-per-template cleanup, and the transactions-before-categories FK ordering)
 * plus a couple of representative deleteByUserId calls to confirm basic wiring.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private ReceiptSettingsRepository receiptSettingsRepository;
    @Mock private ReceiptStorageService receiptStorageService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetPlanRepository budgetPlanRepository;
    @Mock private SavingsBudgetRepository savingsBudgetRepository;
    @Mock private BudgetTemplateRepository budgetTemplateRepository;
    @Mock private BudgetTemplateCategoryRepository budgetTemplateCategoryRepository;
    @Mock private GoldAssetRepository goldAssetRepository;
    @Mock private GoldPriceSettingRepository goldPriceSettingRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private InvestmentRepository investmentRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private PurchaseItemActivityRepository purchaseItemActivityRepository;
    @Mock private PurchaseItemImageStorageService purchaseItemImageStorageService;
    @Mock private NetWorthSnapshotRepository netWorthSnapshotRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private TodoFolderRepository todoFolderRepository;
    @Mock private TodoListRepository todoListRepository;
    @Mock private TodoItemRepository todoItemRepository;
    @Mock private SidebarPreferenceRepository sidebarPreferenceRepository;
    @Mock private AppearancePreferenceRepository appearancePreferenceRepository;
    @Mock private AiConversationRepository aiConversationRepository;
    @Mock private AiMessageRepository aiMessageRepository;
    @Mock private AiDocumentEmbeddingRepository aiDocumentEmbeddingRepository;
    @Mock private AiSettingsRepository aiSettingsRepository;
    @Mock private VoiceCommandHistoryRepository voiceCommandHistoryRepository;
    @Mock private VoiceSettingsRepository voiceSettingsRepository;
    @Mock private GamificationSettingsRepository gamificationSettingsRepository;
    @Mock private UserXpRepository userXpRepository;
    @Mock private XpHistoryRepository xpHistoryRepository;
    @Mock private UserAchievementRepository userAchievementRepository;
    @Mock private UserStatCounterRepository userStatCounterRepository;
    @Mock private StreakRepository streakRepository;
    @Mock private UserChallengeRepository userChallengeRepository;
    @Mock private HouseholdMemberRepository householdMemberRepository;
    @Mock private HouseholdInvitationRepository householdInvitationRepository;
    @Mock private SharedTransactionRepository sharedTransactionRepository;
    @Mock private SharedTransactionShareRepository sharedTransactionShareRepository;
    @Mock private HouseholdGoalContributionRepository householdGoalContributionRepository;
    @Mock private HouseholdService householdService;

    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(userRepository, accountRepository, transactionRepository, categoryRepository,
                recurringTransactionRepository, receiptRepository, receiptSettingsRepository, receiptStorageService,
                notificationRepository, budgetRepository, budgetPlanRepository, savingsBudgetRepository,
                budgetTemplateRepository, budgetTemplateCategoryRepository, goldAssetRepository, goldPriceSettingRepository,
                assetRepository, investmentRepository, loanRepository, subscriptionRepository, purchaseItemRepository,
                purchaseItemActivityRepository, purchaseItemImageStorageService, netWorthSnapshotRepository, noteRepository,
                todoRepository, todoFolderRepository, todoListRepository, todoItemRepository, sidebarPreferenceRepository,
                appearancePreferenceRepository, aiConversationRepository, aiMessageRepository, aiDocumentEmbeddingRepository,
                aiSettingsRepository, voiceCommandHistoryRepository, voiceSettingsRepository, gamificationSettingsRepository,
                userXpRepository, xpHistoryRepository, userAchievementRepository, userStatCounterRepository, streakRepository,
                userChallengeRepository, householdMemberRepository, householdInvitationRepository, sharedTransactionRepository,
                sharedTransactionShareRepository, householdGoalContributionRepository, householdService);

        // Defaults so every test doesn't have to stub every findByUserId call individually.
        when(receiptRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(householdMemberRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(budgetTemplateRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(purchaseItemRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    }

    private ReceiptEntity receipt(Long id, String imagePath) {
        ReceiptEntity r = new ReceiptEntity();
        r.setId(id);
        r.setUserId(USER_ID);
        r.setImagePath(imagePath);
        return r;
    }

    private TransactionEntity transaction(Long id) {
        TransactionEntity t = new TransactionEntity();
        t.setId(id);
        return t;
    }

    private SharedTransactionEntity sharedTransaction(Long id) {
        SharedTransactionEntity s = new SharedTransactionEntity();
        s.setId(id);
        return s;
    }

    private BudgetPlanEntity budgetPlan(Long id) {
        BudgetPlanEntity p = new BudgetPlanEntity();
        p.setId(id);
        p.setUserId(USER_ID);
        return p;
    }

    private SavingsBudgetEntity savingsBudget(Long id, Long planId) {
        SavingsBudgetEntity s = new SavingsBudgetEntity();
        s.setId(id);
        s.setBudgetPlanId(planId);
        return s;
    }

    private BudgetTemplateEntity budgetTemplate(Long id) {
        BudgetTemplateEntity t = new BudgetTemplateEntity();
        t.setId(id);
        t.setUserId(USER_ID);
        return t;
    }

    private PurchaseItemEntity purchaseItem(Long id, String imagePath) {
        PurchaseItemEntity p = new PurchaseItemEntity();
        p.setId(id);
        p.setUserId(USER_ID);
        p.setImagePath(imagePath);
        return p;
    }

    // ================================================================================
    // Receipts: delete files before rows
    // ================================================================================

    @Test
    void deleteAccountBestEffortDeletesEveryReceiptImageFileThenTheReceiptRows() {
        when(receiptRepository.findByUserId(USER_ID)).thenReturn(List.of(receipt(1L, "a.png"), receipt(2L, "b.png")));

        service.deleteAccount(USER_ID);

        verify(receiptStorageService).deleteBestEffort("a.png");
        verify(receiptStorageService).deleteBestEffort("b.png");
        verify(receiptRepository).deleteByUserId(USER_ID);
        verify(receiptSettingsRepository).deleteByUserId(USER_ID);
    }

    // ================================================================================
    // Shared transactions: unlink before deleting the user's own transactions
    // ================================================================================

    @Test
    void deleteAccountUnlinksSharedTransactionForEachOfTheUsersTransactionsThatWasShared() {
        TransactionEntity tx1 = transaction(100L);
        TransactionEntity tx2 = transaction(101L);
        SharedTransactionEntity shared = sharedTransaction(500L);
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(tx1, tx2));
        when(sharedTransactionRepository.findByTransactionId(100L)).thenReturn(Optional.of(shared));
        when(sharedTransactionRepository.findByTransactionId(101L)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID);

        verify(sharedTransactionShareRepository).deleteBySharedTransactionId(500L);
        verify(sharedTransactionRepository).deleteById(500L);
        verify(householdGoalContributionRepository).deleteByTransactionId(100L);
        verify(householdGoalContributionRepository).deleteByTransactionId(101L);
        verify(transactionRepository).deleteByUserId(USER_ID);
    }

    @Test
    void deleteAccountDoesNotTouchSharedTransactionTablesWhenNoTransactionWasEverShared() {
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(transaction(100L)));
        when(sharedTransactionRepository.findByTransactionId(100L)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID);

        verify(sharedTransactionShareRepository, never()).deleteBySharedTransactionId(any());
        verify(sharedTransactionRepository, never()).deleteById(any());
    }

    @Test
    void deleteAccountDeletesTransactionsBeforeCategoriesBecauseOfTheRealForeignKey() {
        service.deleteAccount(USER_ID);

        InOrder inOrder = inOrder(transactionRepository, categoryRepository);
        inOrder.verify(transactionRepository).deleteByUserId(USER_ID);
        inOrder.verify(categoryRepository).deleteByUserId(USER_ID);
    }

    // ================================================================================
    // Household membership
    // ================================================================================

    @Test
    void deleteAccountLeavesTheHouseholdWhenTheUserIsAMember() {
        HouseholdMemberEntity membership = new HouseholdMemberEntity();
        membership.setId(1L);
        membership.setHouseholdId(55L);
        membership.setUserId(USER_ID);
        HouseholdEntity household = new HouseholdEntity();
        household.setId(55L);
        when(householdMemberRepository.findByUserId(USER_ID)).thenReturn(Optional.of(membership));
        when(householdService.requireHousehold(55L)).thenReturn(household);

        service.deleteAccount(USER_ID);

        verify(householdService).leave(household, USER_ID);
    }

    @Test
    void deleteAccountDoesNotInvokeHouseholdServiceWhenTheUserIsNotAMember() {
        when(householdMemberRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID);

        verifyNoInteractions(householdService);
    }

    @Test
    void deleteAccountAlwaysCleansUpHouseholdInvitationsSentOrReceivedByTheUser() {
        service.deleteAccount(USER_ID);

        verify(householdInvitationRepository).deleteByInvitedByUserId(USER_ID);
        verify(householdInvitationRepository).deleteByInviteeUserId(USER_ID);
    }

    // ================================================================================
    // Savings budgets: deleted per plan before the plans themselves
    // ================================================================================

    @Test
    void deleteAccountDeletesSavingsBudgetsForEveryPlanThenTheBudgetsAndPlansThemselves() {
        BudgetPlanEntity plan1 = budgetPlan(10L);
        BudgetPlanEntity plan2 = budgetPlan(11L);
        List<SavingsBudgetEntity> plan1Savings = List.of(savingsBudget(1L, 10L));
        List<SavingsBudgetEntity> plan2Savings = List.of(savingsBudget(2L, 11L), savingsBudget(3L, 11L));
        when(budgetPlanRepository.findByUserId(USER_ID)).thenReturn(List.of(plan1, plan2));
        when(savingsBudgetRepository.findByBudgetPlanId(10L)).thenReturn(plan1Savings);
        when(savingsBudgetRepository.findByBudgetPlanId(11L)).thenReturn(plan2Savings);

        service.deleteAccount(USER_ID);

        verify(savingsBudgetRepository).deleteAll(plan1Savings);
        verify(savingsBudgetRepository).deleteAll(plan2Savings);
        verify(budgetRepository).deleteByUserId(USER_ID);
        verify(budgetPlanRepository).deleteByUserId(USER_ID);
    }

    // ================================================================================
    // Budget template categories: deleted per template before the templates themselves
    // ================================================================================

    @Test
    void deleteAccountDeletesTemplateCategoriesForEveryTemplateThenTheTemplatesThemselves() {
        BudgetTemplateEntity template1 = budgetTemplate(20L);
        BudgetTemplateEntity template2 = budgetTemplate(21L);
        when(budgetTemplateRepository.findByUserId(USER_ID)).thenReturn(List.of(template1, template2));

        service.deleteAccount(USER_ID);

        verify(budgetTemplateCategoryRepository).deleteByTemplateId(20L);
        verify(budgetTemplateCategoryRepository).deleteByTemplateId(21L);
        verify(budgetTemplateRepository).deleteByUserId(USER_ID);
    }

    // ================================================================================
    // Purchase item photos: best-effort deleted before the rows naming them
    // ================================================================================

    @Test
    void deleteAccountBestEffortDeletesEveryPurchaseItemImageThenThePurchaseItemRows() {
        when(purchaseItemRepository.findByUserId(USER_ID))
                .thenReturn(List.of(purchaseItem(1L, "item1.png"), purchaseItem(2L, "item2.png")));

        service.deleteAccount(USER_ID);

        verify(purchaseItemImageStorageService).deleteBestEffort("item1.png");
        verify(purchaseItemImageStorageService).deleteBestEffort("item2.png");
        verify(purchaseItemActivityRepository).deleteByUserId(USER_ID);
        verify(purchaseItemRepository).deleteByUserId(USER_ID);
    }

    // ================================================================================
    // Profile picture file + final user row deletion
    // ================================================================================

    @Test
    void deleteAccountDeletesTheProfilePictureFileFromDiskWhenOneIsSet(@TempDir Path tempDir) throws Exception {
        Path pictureFile = tempDir.resolve("avatar.png");
        Files.writeString(pictureFile, "fake-image-bytes");
        assertTrue(Files.exists(pictureFile));
        ReflectionTestUtils.setField(service, "profileUploadDir", tempDir.toString());

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setProfilePicture("avatar.png");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.deleteAccount(USER_ID);

        assertFalse(Files.exists(pictureFile), "the profile picture file must be deleted from disk");
        verify(userRepository).deleteById(USER_ID);
    }

    @Test
    void deleteAccountSkipsProfilePictureFileDeletionWhenNoneIsSet(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(service, "profileUploadDir", tempDir.toString());
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setProfilePicture(null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.deleteAccount(USER_ID);

        verify(userRepository).deleteById(USER_ID);
    }

    @Test
    void deleteAccountStillDeletesTheUserRowEvenWhenTheUserLookupForTheProfilePictureComesUpEmpty() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID);

        verify(userRepository).deleteById(USER_ID);
    }

    // ================================================================================
    // Representative deleteByUserId wiring across the remaining unrelated feature areas
    // ================================================================================

    @Test
    void deleteAccountDeletesRowsAcrossOtherFeatureAreasKeyedByUserId() {
        service.deleteAccount(USER_ID);

        verify(notificationRepository).deleteByUserId(USER_ID);
        verify(aiMessageRepository).deleteByUserId(USER_ID);
        verify(aiConversationRepository).deleteByUserId(USER_ID);
        verify(voiceCommandHistoryRepository).deleteByUserId(USER_ID);
        verify(recurringTransactionRepository).deleteByUserId(USER_ID);
        verify(accountRepository).deleteByUserId(USER_ID);
        verify(goldAssetRepository).deleteByUserId(USER_ID);
        verify(investmentRepository).deleteByUserId(USER_ID);
        verify(loanRepository).deleteByUserId(USER_ID);
        verify(subscriptionRepository).deleteByUserId(USER_ID);
        verify(netWorthSnapshotRepository).deleteByUserId(USER_ID);
        verify(noteRepository).deleteByUserId(USER_ID);
        verify(todoRepository).deleteByUserId(USER_ID);
        verify(sidebarPreferenceRepository).deleteByUserId(USER_ID);
        verify(appearancePreferenceRepository).deleteByUserId(USER_ID);
        verify(xpHistoryRepository).deleteByUserId(USER_ID);
        verify(userXpRepository).deleteByUserId(USER_ID);
        verify(userAchievementRepository).deleteByUserId(USER_ID);
        verify(userStatCounterRepository).deleteByUserId(USER_ID);
        verify(streakRepository).deleteByUserId(USER_ID);
        verify(userChallengeRepository).deleteByUserId(USER_ID);
        verify(gamificationSettingsRepository).deleteByUserId(USER_ID);
    }
}
