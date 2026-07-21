package org.example.finzin.ai.rag;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiMessageEntity;
import org.example.finzin.entity.BudgetEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.NoteEntity;
import org.example.finzin.entity.SavingsBudgetEntity;
import org.example.finzin.entity.TodoEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.BudgetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.SavingsBudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The single entry point other services call to keep the search index in sync. Every method is
 * @Async (fire-and-forget) and swallows its own failures — indexing must never break the
 * user-facing operation that triggered it. Self-contained: takes only the already-saved entity
 * (plus userId for deletes), re-fetching whatever else it needs itself rather than depending on
 * the calling service, to avoid circular bean dependencies.
 */
@Component
public class DocumentIndexer {
    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final EmbeddingService embeddingService;
    private final DocumentMapper documentMapper;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final SavingsBudgetRepository savingsBudgetRepository;

    public DocumentIndexer(EmbeddingService embeddingService, DocumentMapper documentMapper,
                            CategoryRepository categoryRepository, BudgetRepository budgetRepository,
                            SavingsBudgetRepository savingsBudgetRepository) {
        this.embeddingService = embeddingService;
        this.documentMapper = documentMapper;
        this.categoryRepository = categoryRepository;
        this.budgetRepository = budgetRepository;
        this.savingsBudgetRepository = savingsBudgetRepository;
    }

    @Async("indexingTaskExecutor")
    public void indexTransaction(TransactionEntity t) {
        safely("TRANSACTION", t.getId(), () -> {
            var doc = documentMapper.mapTransaction(t);
            embeddingService.indexDocument(t.getUserId(), IndexedEntityType.TRANSACTION, t.getId(), doc.title(), doc.content(), doc.metadata());
        });
    }

    @Async("indexingTaskExecutor")
    public void deleteTransaction(Long userId, Long transactionId) {
        safely("TRANSACTION", transactionId, () -> embeddingService.deleteDocument(userId, IndexedEntityType.TRANSACTION, transactionId));
    }

    @Async("indexingTaskExecutor")
    public void indexNote(NoteEntity n) {
        safely("NOTE", n.getId(), () -> {
            var doc = documentMapper.mapNote(n);
            embeddingService.indexDocument(n.getUserId(), IndexedEntityType.NOTE, n.getId(), doc.title(), doc.content(), doc.metadata());
        });
    }

    @Async("indexingTaskExecutor")
    public void deleteNote(Long userId, Long noteId) {
        safely("NOTE", noteId, () -> embeddingService.deleteDocument(userId, IndexedEntityType.NOTE, noteId));
    }

    @Async("indexingTaskExecutor")
    public void indexTodo(TodoEntity t) {
        safely("TODO", t.getId(), () -> {
            var doc = documentMapper.mapTodo(t);
            embeddingService.indexDocument(t.getUserId(), IndexedEntityType.TODO, t.getId(), doc.title(), doc.content(), doc.metadata());
        });
    }

    @Async("indexingTaskExecutor")
    public void deleteTodo(Long userId, Long todoId) {
        safely("TODO", todoId, () -> embeddingService.deleteDocument(userId, IndexedEntityType.TODO, todoId));
    }

    @Async("indexingTaskExecutor")
    public void indexAccount(AccountEntity a) {
        safely("ACCOUNT", a.getId(), () -> {
            var doc = documentMapper.mapAccount(a);
            embeddingService.indexDocument(a.getUserId(), IndexedEntityType.ACCOUNT, a.getId(), doc.title(), doc.content(), doc.metadata());
        });
    }

    @Async("indexingTaskExecutor")
    public void deleteAccount(Long userId, Long accountId) {
        safely("ACCOUNT", accountId, () -> embeddingService.deleteDocument(userId, IndexedEntityType.ACCOUNT, accountId));
    }

    @Async("indexingTaskExecutor")
    public void indexGoldAsset(GoldAssetEntity g) {
        safely("GOLD_ASSET", g.getId(), () -> {
            var doc = documentMapper.mapGoldAsset(g);
            embeddingService.indexDocument(g.getUserId(), IndexedEntityType.GOLD_ASSET, g.getId(), doc.title(), doc.content(), doc.metadata());
        });
    }

    @Async("indexingTaskExecutor")
    public void deleteGoldAsset(Long userId, Long assetId) {
        safely("GOLD_ASSET", assetId, () -> embeddingService.deleteDocument(userId, IndexedEntityType.GOLD_ASSET, assetId));
    }

    @Async("indexingTaskExecutor")
    public void indexBudgetPlan(BudgetPlanEntity plan) {
        safely("BUDGET_PLAN", plan.getId(), () -> {
            List<BudgetEntity> categoryBudgets = budgetRepository.findByBudgetPlanId(plan.getId());
            List<SavingsBudgetEntity> savingsBudgets = savingsBudgetRepository.findByBudgetPlanId(plan.getId());

            List<Map<String, Object>> categoryMaps = categoryBudgets.stream().map(b -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoryName", resolveCategoryName(b.getCategoryId()));
                m.put("budgetAmount", b.getBudgetAmount());
                return m;
            }).collect(Collectors.toList());

            List<Map<String, Object>> savingsMaps = savingsBudgets.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoryName", resolveCategoryName(s.getCategoryId()));
                m.put("targetAmount", s.getTargetAmount());
                return m;
            }).collect(Collectors.toList());

            var doc = documentMapper.mapBudgetPlan(plan, categoryMaps, savingsMaps);
            embeddingService.indexDocument(plan.getUserId(), IndexedEntityType.BUDGET_PLAN, plan.getId(), doc.title(), doc.content(), doc.metadata());
        });
    }

    @Async("indexingTaskExecutor")
    public void deleteBudgetPlan(Long userId, Long planId) {
        safely("BUDGET_PLAN", planId, () -> embeddingService.deleteDocument(userId, IndexedEntityType.BUDGET_PLAN, planId));
    }

    @Async("indexingTaskExecutor")
    public void indexConversationTurn(AiConversationEntity conversation, List<AiMessageEntity> recentMessages) {
        safely("AI_CONVERSATION", conversation.getId(), () -> {
            var doc = documentMapper.mapConversation(conversation, recentMessages);
            embeddingService.indexDocument(conversation.getUserId(), IndexedEntityType.AI_CONVERSATION, conversation.getId(), doc.title(), doc.content(), doc.metadata());
        });
    }

    @Async("indexingTaskExecutor")
    public void deleteConversation(Long userId, Long conversationId) {
        safely("AI_CONVERSATION", conversationId, () -> embeddingService.deleteDocument(userId, IndexedEntityType.AI_CONVERSATION, conversationId));
    }

    private String resolveCategoryName(Long categoryId) {
        if (categoryId == null) return "Uncategorized";
        return categoryRepository.findById(categoryId).map(CategoryEntity::getName).orElse("Uncategorized");
    }

    private void safely(String entityType, Long entityId, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Indexing failed entityType={} entityId={} errorType={}", entityType, entityId, e.getClass().getSimpleName());
        }
    }
}
