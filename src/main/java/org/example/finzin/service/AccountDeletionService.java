package org.example.finzin.service;

import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.BudgetTemplateEntity;
import org.example.finzin.entity.ReceiptEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.receipts.ReceiptStorageService;
import org.example.finzin.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Permanently deletes a user account and every row/file tied to it. This is the actual
 * implementation behind Settings &gt; Danger Zone &gt; Delete My Account, which previously only
 * showed a toast and redirected to login without deleting anything server-side.
 *
 * Deletion order matters in exactly one place: TransactionEntity has a real DB foreign key on
 * category_id (fk_transaction_category), so transactions must be deleted before categories.
 * Everything else here is a plain userId column with no enforced FK, so order is otherwise free —
 * this still deletes children (savings budgets under a plan, template categories under a
 * template) before their parents for clarity.
 */
@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final ReceiptRepository receiptRepository;
    private final ReceiptSettingsRepository receiptSettingsRepository;
    private final ReceiptStorageService receiptStorageService;
    private final NotificationRepository notificationRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetPlanRepository budgetPlanRepository;
    private final SavingsBudgetRepository savingsBudgetRepository;
    private final BudgetTemplateRepository budgetTemplateRepository;
    private final BudgetTemplateCategoryRepository budgetTemplateCategoryRepository;
    private final GoldAssetRepository goldAssetRepository;
    private final GoldPriceSettingRepository goldPriceSettingRepository;
    private final AssetRepository assetRepository;
    private final InvestmentRepository investmentRepository;
    private final LoanRepository loanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WishlistGoalRepository wishlistGoalRepository;
    private final NetWorthSnapshotRepository netWorthSnapshotRepository;
    private final NoteRepository noteRepository;
    private final TodoRepository todoRepository;
    private final SidebarPreferenceRepository sidebarPreferenceRepository;
    private final AppearancePreferenceRepository appearancePreferenceRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final AiDocumentEmbeddingRepository aiDocumentEmbeddingRepository;
    private final AiSettingsRepository aiSettingsRepository;
    private final VoiceCommandHistoryRepository voiceCommandHistoryRepository;
    private final VoiceSettingsRepository voiceSettingsRepository;

    @Value("${app.upload.dir:user-uploads/profiles}")
    private String profileUploadDir;

    public AccountDeletionService(UserRepository userRepository, AccountRepository accountRepository,
            TransactionRepository transactionRepository, CategoryRepository categoryRepository,
            RecurringTransactionRepository recurringTransactionRepository, ReceiptRepository receiptRepository,
            ReceiptSettingsRepository receiptSettingsRepository, ReceiptStorageService receiptStorageService,
            NotificationRepository notificationRepository, BudgetRepository budgetRepository,
            BudgetPlanRepository budgetPlanRepository, SavingsBudgetRepository savingsBudgetRepository,
            BudgetTemplateRepository budgetTemplateRepository, BudgetTemplateCategoryRepository budgetTemplateCategoryRepository,
            GoldAssetRepository goldAssetRepository, GoldPriceSettingRepository goldPriceSettingRepository,
            AssetRepository assetRepository, InvestmentRepository investmentRepository, LoanRepository loanRepository,
            SubscriptionRepository subscriptionRepository, WishlistGoalRepository wishlistGoalRepository,
            NetWorthSnapshotRepository netWorthSnapshotRepository, NoteRepository noteRepository, TodoRepository todoRepository,
            SidebarPreferenceRepository sidebarPreferenceRepository, AppearancePreferenceRepository appearancePreferenceRepository,
            AiConversationRepository aiConversationRepository, AiMessageRepository aiMessageRepository,
            AiDocumentEmbeddingRepository aiDocumentEmbeddingRepository, AiSettingsRepository aiSettingsRepository,
            VoiceCommandHistoryRepository voiceCommandHistoryRepository, VoiceSettingsRepository voiceSettingsRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.receiptRepository = receiptRepository;
        this.receiptSettingsRepository = receiptSettingsRepository;
        this.receiptStorageService = receiptStorageService;
        this.notificationRepository = notificationRepository;
        this.budgetRepository = budgetRepository;
        this.budgetPlanRepository = budgetPlanRepository;
        this.savingsBudgetRepository = savingsBudgetRepository;
        this.budgetTemplateRepository = budgetTemplateRepository;
        this.budgetTemplateCategoryRepository = budgetTemplateCategoryRepository;
        this.goldAssetRepository = goldAssetRepository;
        this.goldPriceSettingRepository = goldPriceSettingRepository;
        this.assetRepository = assetRepository;
        this.investmentRepository = investmentRepository;
        this.loanRepository = loanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.wishlistGoalRepository = wishlistGoalRepository;
        this.netWorthSnapshotRepository = netWorthSnapshotRepository;
        this.noteRepository = noteRepository;
        this.todoRepository = todoRepository;
        this.sidebarPreferenceRepository = sidebarPreferenceRepository;
        this.appearancePreferenceRepository = appearancePreferenceRepository;
        this.aiConversationRepository = aiConversationRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.aiDocumentEmbeddingRepository = aiDocumentEmbeddingRepository;
        this.aiSettingsRepository = aiSettingsRepository;
        this.voiceCommandHistoryRepository = voiceCommandHistoryRepository;
        this.voiceSettingsRepository = voiceSettingsRepository;
    }

    @Transactional
    public void deleteAccount(Long userId) {
        // Receipt image files live on disk, not in the DB — delete them before the rows that name them.
        for (ReceiptEntity receipt : receiptRepository.findByUserId(userId)) {
            receiptStorageService.deleteBestEffort(receipt.getImagePath());
        }
        receiptRepository.deleteByUserId(userId);
        receiptSettingsRepository.deleteByUserId(userId);

        aiMessageRepository.deleteByUserId(userId);
        aiConversationRepository.deleteByUserId(userId);
        aiDocumentEmbeddingRepository.deleteByUserId(userId);
        aiSettingsRepository.deleteByUserId(userId);
        voiceCommandHistoryRepository.deleteByUserId(userId);
        voiceSettingsRepository.deleteByUserId(userId);

        notificationRepository.deleteByUserId(userId);

        // Transactions before categories: transactions.category_id has a real FK constraint.
        transactionRepository.deleteByUserId(userId);
        recurringTransactionRepository.deleteByUserId(userId);
        categoryRepository.deleteByUserId(userId);

        // Savings budgets only reference their plan (no userId column of their own).
        for (BudgetPlanEntity plan : budgetPlanRepository.findByUserId(userId)) {
            savingsBudgetRepository.deleteAll(savingsBudgetRepository.findByBudgetPlanId(plan.getId()));
        }
        budgetRepository.deleteByUserId(userId);
        budgetPlanRepository.deleteByUserId(userId);

        // Template categories only reference their template (no userId column of their own).
        for (BudgetTemplateEntity template : budgetTemplateRepository.findByUserId(userId)) {
            budgetTemplateCategoryRepository.deleteByTemplateId(template.getId());
        }
        budgetTemplateRepository.deleteByUserId(userId);

        accountRepository.deleteByUserId(userId);

        goldAssetRepository.deleteByUserId(userId);
        goldPriceSettingRepository.deleteByUserId(userId);
        assetRepository.deleteByUserId(userId);
        investmentRepository.deleteByUserId(userId);
        loanRepository.deleteByUserId(userId);
        subscriptionRepository.deleteByUserId(userId);
        wishlistGoalRepository.deleteByUserId(userId);
        netWorthSnapshotRepository.deleteByUserId(userId);

        noteRepository.deleteByUserId(userId);
        todoRepository.deleteByUserId(userId);

        sidebarPreferenceRepository.deleteByUserId(userId);
        appearancePreferenceRepository.deleteByUserId(userId);

        userRepository.findById(userId).ifPresent(user -> deleteProfilePictureFile(user));
        userRepository.deleteById(userId);
    }

    private void deleteProfilePictureFile(UserEntity user) {
        if (user.getProfilePicture() == null) return;
        try {
            Path path = Paths.get(profileUploadDir).resolve(user.getProfilePicture());
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
