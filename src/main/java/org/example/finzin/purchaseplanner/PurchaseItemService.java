package org.example.finzin.purchaseplanner;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.PurchaseItemActivityEntity;
import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.gamification.GamificationEventType;
import org.example.finzin.purchaseplanner.dto.*;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.PurchaseItemActivityRepository;
import org.example.finzin.repository.PurchaseItemRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** CRUD + orchestration for purchase items. Every mutating method keeps the semantic search index
 * in sync via {@link DocumentIndexer} and appends one immutable {@link PurchaseItemActivityEntity}
 * row per meaningful change, powering both the Activity Timeline and (for price) Price History. */
@Service
public class PurchaseItemService {

    private static final List<String> NEED_LEVELS = List.of("MUST_HAVE", "SHOULD_HAVE", "NICE_TO_HAVE");
    private static final List<String> PRIORITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    /** The non-final statuses: valid PATCH /status targets, and what "active" means everywhere else (dashboard/analytics/affordability). */
    private static final List<String> ACTIVE_STATUSES = List.of("PLANNING", "WAITING", "READY");
    private static final List<String> CANCEL_REASONS = List.of("TOO_EXPENSIVE", "NOT_NEEDED", "CHANGED_MIND", "BOUGHT_ELSEWHERE");

    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseItemActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final PurchaseItemImageStorageService imageStorageService;
    private final PurchaseAffordabilityService affordabilityService;
    private final BudgetPlanService budgetPlanService;
    private final DocumentIndexer documentIndexer;
    private final ApplicationEventPublisher eventPublisher;

    public PurchaseItemService(PurchaseItemRepository purchaseItemRepository, PurchaseItemActivityRepository activityRepository,
                                CategoryRepository categoryRepository, TransactionRepository transactionRepository,
                                PurchaseItemImageStorageService imageStorageService, PurchaseAffordabilityService affordabilityService,
                                BudgetPlanService budgetPlanService, DocumentIndexer documentIndexer,
                                ApplicationEventPublisher eventPublisher) {
        this.purchaseItemRepository = purchaseItemRepository;
        this.activityRepository = activityRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.imageStorageService = imageStorageService;
        this.affordabilityService = affordabilityService;
        this.budgetPlanService = budgetPlanService;
        this.documentIndexer = documentIndexer;
        this.eventPublisher = eventPublisher;
    }

    // ============== CRUD ==============

    public List<PurchaseItemEntity> listForUser(Long userId) {
        return purchaseItemRepository.findByUserId(userId);
    }

    public Optional<PurchaseItemEntity> findOwned(Long userId, Long id) {
        return purchaseItemRepository.findByIdAndUserId(id, userId);
    }

    public String validate(PurchaseItemRequest body) {
        if (body.itemName() == null || body.itemName().isBlank()) return "Item name is required";
        if (body.estimatedPrice() == null || body.estimatedPrice() < 0) return "Estimated price must be a non-negative number";
        if (body.needLevel() != null && !body.needLevel().isBlank() && !NEED_LEVELS.contains(body.needLevel())) {
            return "needLevel must be one of MUST_HAVE, SHOULD_HAVE, NICE_TO_HAVE";
        }
        if (body.priority() != null && !body.priority().isBlank() && !PRIORITIES.contains(body.priority())) {
            return "priority must be one of CRITICAL, HIGH, MEDIUM, LOW";
        }
        if (body.targetMonth() != null && !body.targetMonth().isBlank()) {
            try { YearMonth.parse(body.targetMonth()); } catch (Exception e) { return "targetMonth must be in yyyy-MM format"; }
        }
        if (body.expectedPurchaseDate() != null && !body.expectedPurchaseDate().isBlank()) {
            try { LocalDate.parse(body.expectedPurchaseDate()); } catch (Exception e) { return "expectedPurchaseDate must be a valid date"; }
        }
        if (body.status() != null && !body.status().isBlank() && !ACTIVE_STATUSES.contains(body.status())) {
            return "status must be one of PLANNING, WAITING, READY (use the dedicated endpoints for Purchased/Cancelled)";
        }
        return null;
    }

    public PurchaseItemEntity create(Long userId, PurchaseItemRequest body) {
        PurchaseItemEntity entity = new PurchaseItemEntity();
        entity.setUserId(userId);
        applyRequest(entity, body);
        PurchaseItemEntity saved = purchaseItemRepository.save(entity);
        logActivity(saved, "CREATED", null, null, null, "Purchase item added to wishlist");
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    public PurchaseItemEntity update(PurchaseItemEntity existing, PurchaseItemRequest body) {
        Double oldPrice = existing.getEstimatedPrice();
        String beforeSnapshot = describeForDiff(existing);

        applyRequest(existing, body);
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);

        if (oldPrice != null && saved.getEstimatedPrice() != null && Double.compare(oldPrice, saved.getEstimatedPrice()) != 0) {
            logActivity(saved, "PRICE_CHANGE", "estimatedPrice", String.valueOf(oldPrice), String.valueOf(saved.getEstimatedPrice()),
                    "Price changed from " + oldPrice + " to " + saved.getEstimatedPrice());
        }
        if (!beforeSnapshot.equals(describeForDiff(saved))) {
            logActivity(saved, "EDITED", null, null, null, "Purchase details updated");
        }
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    private void applyRequest(PurchaseItemEntity entity, PurchaseItemRequest body) {
        entity.setItemName(body.itemName());
        entity.setEstimatedPrice(body.estimatedPrice());
        entity.setCategory(body.category());
        entity.setNeedLevel(body.needLevel() != null && !body.needLevel().isBlank() ? body.needLevel() : "SHOULD_HAVE");
        entity.setPriority(body.priority() != null && !body.priority().isBlank() ? body.priority() : "MEDIUM");
        entity.setBrand(body.brand());
        entity.setStore(body.store());
        entity.setPurchaseUrl(body.purchaseUrl());
        entity.setNotes(body.notes());
        entity.setTargetMonth(body.targetMonth() != null && !body.targetMonth().isBlank() ? body.targetMonth() : null);
        entity.setExpectedPurchaseDate(body.expectedPurchaseDate() != null && !body.expectedPurchaseDate().isBlank()
                ? LocalDate.parse(body.expectedPurchaseDate()) : null);
        entity.setLinkedSavingsGoalId(body.linkedSavingsGoalId());
        if (body.status() != null && !body.status().isBlank() && ACTIVE_STATUSES.contains(body.status())) {
            entity.setStatus(body.status());
        } else if (entity.getStatus() == null) {
            entity.setStatus("PLANNING");
        }
    }

    private String describeForDiff(PurchaseItemEntity e) {
        return String.join("|", nz(e.getItemName()), nz(e.getCategory()), nz(e.getNeedLevel()), nz(e.getPriority()),
                nz(e.getBrand()), nz(e.getStore()), nz(e.getPurchaseUrl()), nz(e.getNotes()), nz(e.getTargetMonth()),
                e.getExpectedPurchaseDate() == null ? "" : e.getExpectedPurchaseDate().toString(),
                e.getLinkedSavingsGoalId() == null ? "" : e.getLinkedSavingsGoalId().toString());
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // ============== Kanban / Timeline / Status transitions ==============

    public PurchaseItemEntity patchNeedLevel(PurchaseItemEntity existing, String newNeedLevel) {
        if (newNeedLevel == null || !NEED_LEVELS.contains(newNeedLevel)) {
            throw PurchaseItemException.badRequest("needLevel must be one of MUST_HAVE, SHOULD_HAVE, NICE_TO_HAVE");
        }
        String old = existing.getNeedLevel();
        if (Objects.equals(old, newNeedLevel)) return existing;
        existing.setNeedLevel(newNeedLevel);
        existing.setBoardPosition(null); // manual order doesn't carry across columns
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);
        logActivity(saved, "NEED_LEVEL_CHANGE", "needLevel", old, newNeedLevel, "Moved from " + old + " to " + newNeedLevel);
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    /** Persists a Kanban column's on-screen card order after a same-column drag-and-drop reorder.
     *  Not logged as an activity/timeline event — purely cosmetic ordering, not a data change. */
    @Transactional
    public void reorderWithinColumn(Long userId, String needLevel, List<Long> orderedIds) {
        if (needLevel == null || !NEED_LEVELS.contains(needLevel)) {
            throw PurchaseItemException.badRequest("needLevel must be one of MUST_HAVE, SHOULD_HAVE, NICE_TO_HAVE");
        }
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw PurchaseItemException.badRequest("orderedIds must not be empty");
        }
        Map<Long, PurchaseItemEntity> byId = purchaseItemRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(PurchaseItemEntity::getId, i -> i));
        List<PurchaseItemEntity> ordered = new ArrayList<>();
        for (Long id : orderedIds) {
            PurchaseItemEntity item = byId.get(id);
            if (item == null || !Objects.equals(item.getUserId(), userId) || !needLevel.equals(item.getNeedLevel())) {
                throw PurchaseItemException.badRequest("One or more items couldn't be reordered — try refreshing the board.");
            }
            ordered.add(item);
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setBoardPosition(i);
        }
        purchaseItemRepository.saveAll(ordered);
    }

    public PurchaseItemEntity patchTargetMonth(PurchaseItemEntity existing, String newTargetMonth) {
        String normalized = (newTargetMonth == null || newTargetMonth.isBlank()) ? null : newTargetMonth;
        if (normalized != null) {
            try { YearMonth.parse(normalized); } catch (Exception e) { throw PurchaseItemException.badRequest("targetMonth must be in yyyy-MM format"); }
        }
        String old = existing.getTargetMonth();
        if (Objects.equals(old, normalized)) return existing;
        existing.setTargetMonth(normalized);
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);
        logActivity(saved, "MONTH_CHANGE", "targetMonth", old, normalized, "Target month changed");
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    public PurchaseItemEntity patchStatus(PurchaseItemEntity existing, String newStatus) {
        if (!ACTIVE_STATUSES.contains(existing.getStatus())) throw PurchaseItemException.alreadyFinalized(existing.getStatus());
        if (newStatus == null || !ACTIVE_STATUSES.contains(newStatus)) {
            throw PurchaseItemException.badRequest("status must be one of PLANNING, WAITING, READY");
        }
        String old = existing.getStatus();
        if (Objects.equals(old, newStatus)) return existing;
        existing.setStatus(newStatus);
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);
        logActivity(saved, "STATUS_CHANGE", "status", old, newStatus, "Status changed from " + old + " to " + newStatus);
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    public PurchaseItemEntity cancel(PurchaseItemEntity existing, String reason) {
        if (!ACTIVE_STATUSES.contains(existing.getStatus())) throw PurchaseItemException.alreadyFinalized(existing.getStatus());
        if (reason == null || !CANCEL_REASONS.contains(reason)) {
            throw PurchaseItemException.badRequest("reason must be one of TOO_EXPENSIVE, NOT_NEEDED, CHANGED_MIND, BOUGHT_ELSEWHERE");
        }
        String oldStatus = existing.getStatus();
        existing.setStatus("CANCELLED");
        existing.setCancelReason(reason);
        existing.setCancelledAt(LocalDateTime.now());
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);
        logActivity(saved, "CANCELLED", "status", oldStatus, "CANCELLED", "Cancelled: " + reason);
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    // ============== Purchased -> Expense handoff (reuses the existing transaction system) ==============

    public ExpenseDraftResponse buildExpenseDraft(PurchaseItemEntity item) {
        Long categoryId = resolveCategoryId(item.getUserId(), item.getCategory());
        String categoryName = categoryId != null
                ? categoryRepository.findById(categoryId).map(CategoryEntity::getName).orElse(null)
                : null;

        StringBuilder details = new StringBuilder();
        if (item.getStore() != null && !item.getStore().isBlank()) details.append("Purchased from ").append(item.getStore());
        if (item.getBrand() != null && !item.getBrand().isBlank()) {
            if (details.length() > 0) details.append(" · ");
            details.append(item.getBrand());
        }

        return new ExpenseDraftResponse(item.getEstimatedPrice(), item.getItemName(), categoryId, categoryName,
                LocalDate.now().toString(), details.length() > 0 ? details.toString() : null);
    }

    private Long resolveCategoryId(Long userId, String label) {
        if (label == null || label.isBlank()) return null;
        for (CategoryEntity category : categoryRepository.findByUserId(userId)) {
            if (category.getName() != null && category.getName().trim().equalsIgnoreCase(label.trim())) {
                return category.getId();
            }
        }
        return null;
    }

    /** transactionId is optional: when present, links a transaction already created via the normal
     * POST /api/transactions flow ("Yes, log expense"); when null, just finalizes the status ("No,
     * just mark purchased" — e.g. the user already logged it elsewhere). Never creates a transaction itself. */
    public PurchaseItemEntity markPurchased(PurchaseItemEntity existing, Long transactionId) {
        if (!ACTIVE_STATUSES.contains(existing.getStatus())) throw PurchaseItemException.alreadyFinalized(existing.getStatus());
        if (transactionId != null) {
            TransactionEntity transaction = transactionRepository.findByIdAndUserId(transactionId, existing.getUserId()).orElse(null);
            if (transaction == null) throw PurchaseItemException.badRequest("That transaction doesn't exist or doesn't belong to you.");
            existing.setLinkedTransactionId(transactionId);
        }

        String oldStatus = existing.getStatus();
        existing.setStatus("PURCHASED");
        existing.setPurchasedAt(LocalDateTime.now());
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);
        logActivity(saved, "PURCHASED", "status", oldStatus, "PURCHASED",
                transactionId != null ? "Marked purchased, linked to transaction #" + transactionId : "Marked purchased (no linked expense)");
        documentIndexer.indexPurchaseItem(saved);
        // GOAL_COMPLETED previously only fired from the old Wishlist Goals feature (now replaced by
        // this module) — reusing the same event/metadata shape ("goalId") so GamificationEventListener
        // needs no changes; a purchase reaching PURCHASED is this module's equivalent "goal achieved" moment.
        eventPublisher.publishEvent(new GamificationEvent(saved.getUserId(), GamificationEventType.GOAL_COMPLETED,
                Map.of("goalId", saved.getId())));
        return saved;
    }

    // ============== Delete / Image ==============

    /** @Transactional because deleteByPurchaseItemId is a Spring Data *derived* delete query — unlike
     * the built-in CRUD delete()/deleteById(), it only gets a real (non-read-only) transaction when
     * invoked from within an explicitly transactional method (mirrors AccountDeletionService.deleteAccount()). */
    @Transactional
    public void delete(PurchaseItemEntity existing) {
        imageStorageService.deleteBestEffort(existing.getImagePath());
        activityRepository.deleteByPurchaseItemId(existing.getId());
        Long userId = existing.getUserId();
        Long id = existing.getId();
        purchaseItemRepository.deleteById(id);
        documentIndexer.deletePurchaseItem(userId, id);
    }

    public PurchaseItemEntity attachImage(PurchaseItemEntity existing, MultipartFile file) throws IOException {
        PurchaseItemImageStorageService.ValidationError error = imageStorageService.validate(file);
        if (error != null) throw PurchaseItemException.badRequest(error.message());
        if (existing.getImagePath() != null) imageStorageService.deleteBestEffort(existing.getImagePath());
        PurchaseItemImageStorageService.StoredFile stored = imageStorageService.save(file);
        existing.setImagePath(stored.filename());
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    public PurchaseItemEntity removeImage(PurchaseItemEntity existing) {
        if (existing.getImagePath() == null) return existing;
        imageStorageService.deleteBestEffort(existing.getImagePath());
        existing.setImagePath(null);
        PurchaseItemEntity saved = purchaseItemRepository.save(existing);
        documentIndexer.indexPurchaseItem(saved);
        return saved;
    }

    // ============== Response assembly ==============

    public List<PurchaseItemResponse> toResponseList(Long userId, List<PurchaseItemEntity> items) {
        List<PurchaseItemEntity> activeItems = items.stream()
                .filter(i -> ACTIVE_STATUSES.contains(i.getStatus()))
                .collect(Collectors.toList());
        PurchaseAffordabilityService.AffordabilityContext ctx = affordabilityService.buildContext(userId, activeItems);
        return items.stream().map(i -> toResponse(i, ctx)).collect(Collectors.toList());
    }

    public PurchaseItemResponse toResponse(PurchaseItemEntity item, PurchaseAffordabilityService.AffordabilityContext ctx) {
        PurchaseAffordabilityService.AffordabilityResult aff = affordabilityService.computeAffordability(item, ctx);
        PurchaseAffordabilityService.DecisionMatrixResult dm = affordabilityService.computeDecisionMatrix(item, ctx);

        AffordabilityView affordabilityView = new AffordabilityView(aff.status(), aff.statusLabel(), aff.monthsRemaining(),
                round2(aff.netAvailableNow()), round2(aff.monthlySavingsCapacity()));
        DecisionMatrixView decisionMatrixView = new DecisionMatrixView(dm.needScore(), dm.urgencyScore(), dm.budgetScore(),
                dm.savingsScore(), round2(dm.overallReadinessPercent()));

        String linkedGoalName = null;
        Double linkedGoalProgress = null;
        if (item.getLinkedSavingsGoalId() != null) {
            var status = budgetPlanService.findSavingsBudgetStatus(item.getUserId(), item.getLinkedSavingsGoalId()).orElse(null);
            if (status != null) {
                linkedGoalName = (String) status.get("categoryName");
                linkedGoalProgress = (Double) status.get("percentUsed");
            }
        }

        return new PurchaseItemResponse(
                item.getId(), item.getItemName(), item.getEstimatedPrice(), item.getCategory(), item.getNeedLevel(), item.getPriority(),
                item.getBrand(), item.getStore(), item.getPurchaseUrl(), item.getNotes(), item.getTargetMonth(),
                item.getExpectedPurchaseDate() != null ? item.getExpectedPurchaseDate().toString() : null,
                item.getImagePath() != null ? "/user-uploads/purchase-items/" + item.getImagePath() : null,
                item.getLinkedSavingsGoalId(), linkedGoalName, linkedGoalProgress,
                item.getLinkedTransactionId(), item.getStatus(), item.getCancelReason(), affordabilityView, decisionMatrixView,
                item.getPurchasedAt() != null ? item.getPurchasedAt().toString() : null,
                item.getCancelledAt() != null ? item.getCancelledAt().toString() : null,
                item.getCreatedAt() != null ? item.getCreatedAt().toString() : null,
                item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : null,
                item.getBoardPosition()
        );
    }

    public PurchaseItemDetailResponse toDetailResponse(Long userId, PurchaseItemEntity item) {
        List<PurchaseItemEntity> activeItems = purchaseItemRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        PurchaseAffordabilityService.AffordabilityContext ctx = affordabilityService.buildContext(userId, activeItems);
        PurchaseItemResponse response = toResponse(item, ctx);

        List<PurchaseItemActivityResponse> timeline = activityRepository.findByPurchaseItemIdOrderByCreatedAtDesc(item.getId()).stream()
                .map(a -> new PurchaseItemActivityResponse(a.getId(), a.getActivityType(), a.getFieldName(), a.getOldValue(),
                        a.getNewValue(), a.getNote(), a.getCreatedAt() != null ? a.getCreatedAt().toString() : null))
                .collect(Collectors.toList());

        List<PriceHistoryEntry> priceHistory = activityRepository
                .findByPurchaseItemIdAndActivityTypeOrderByCreatedAtAsc(item.getId(), "PRICE_CHANGE").stream()
                .map(a -> new PriceHistoryEntry(parseDoubleSafe(a.getOldValue()), parseDoubleSafe(a.getNewValue()),
                        a.getCreatedAt() != null ? a.getCreatedAt().toString() : null))
                .collect(Collectors.toList());

        return new PurchaseItemDetailResponse(response, timeline, priceHistory);
    }

    // ============== Dashboard / Analytics ==============

    public PurchaseDashboardSummaryResponse computeDashboardSummary(Long userId) {
        List<PurchaseItemEntity> activeItems = purchaseItemRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        double totalValue = activeItems.stream().mapToDouble(PurchaseItemEntity::getEstimatedPrice).sum();
        int mustCount = (int) activeItems.stream().filter(i -> "MUST_HAVE".equals(i.getNeedLevel())).count();
        int shouldCount = (int) activeItems.stream().filter(i -> "SHOULD_HAVE".equals(i.getNeedLevel())).count();
        int niceCount = (int) activeItems.stream().filter(i -> "NICE_TO_HAVE".equals(i.getNeedLevel())).count();
        int readyCount = (int) activeItems.stream().filter(i -> "READY".equals(i.getStatus())).count();
        String currentMonth = YearMonth.now().toString();
        double plannedThisMonth = activeItems.stream()
                .filter(i -> currentMonth.equals(i.getTargetMonth()))
                .mapToDouble(PurchaseItemEntity::getEstimatedPrice).sum();

        return new PurchaseDashboardSummaryResponse(round2(totalValue), mustCount, shouldCount, niceCount, readyCount,
                round2(plannedThisMonth), activeItems.size());
    }

    public PurchaseAnalyticsResponse computeAnalytics(Long userId) {
        List<PurchaseItemEntity> all = purchaseItemRepository.findByUserId(userId);
        List<PurchaseItemEntity> activeItems = all.stream().filter(i -> ACTIVE_STATUSES.contains(i.getStatus())).collect(Collectors.toList());

        double totalValue = activeItems.stream().mapToDouble(PurchaseItemEntity::getEstimatedPrice).sum();
        double averageCost = activeItems.isEmpty() ? 0 : totalValue / activeItems.size();

        int currentYear = LocalDate.now().getYear();
        int purchasedThisYear = (int) all.stream()
                .filter(i -> "PURCHASED".equals(i.getStatus()) && i.getPurchasedAt() != null && i.getPurchasedAt().getYear() == currentYear)
                .count();

        List<PurchaseItemEntity> cancelledItems = all.stream().filter(i -> "CANCELLED".equals(i.getStatus())).collect(Collectors.toList());
        double moneySavedByCancelling = cancelledItems.stream().mapToDouble(PurchaseItemEntity::getEstimatedPrice).sum();

        int mustCount = (int) activeItems.stream().filter(i -> "MUST_HAVE".equals(i.getNeedLevel())).count();
        int shouldCount = (int) activeItems.stream().filter(i -> "SHOULD_HAVE".equals(i.getNeedLevel())).count();
        int niceCount = (int) activeItems.stream().filter(i -> "NICE_TO_HAVE".equals(i.getNeedLevel())).count();

        // Forward-leaning window (3 months back, 8 months forward) rather than a purely trailing one —
        // a purchase planner's items mostly target FUTURE months, so a trailing-only window would
        // show almost nothing for the primary use case (planned-but-not-yet-purchased items).
        List<MonthlyCount> monthlyPlanned = new ArrayList<>();
        List<MonthlyCount> monthlyCompleted = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(3);
        for (int i = 0; i < 12; i++) {
            String monthKey = cursor.toString();
            long plannedCount = all.stream().filter(p -> monthKey.equals(p.getTargetMonth())).count();
            long completedCount = all.stream()
                    .filter(p -> "PURCHASED".equals(p.getStatus()) && p.getPurchasedAt() != null
                            && YearMonth.from(p.getPurchasedAt()).toString().equals(monthKey))
                    .count();
            monthlyPlanned.add(new MonthlyCount(monthKey, (int) plannedCount));
            monthlyCompleted.add(new MonthlyCount(monthKey, (int) completedCount));
            cursor = cursor.plusMonths(1);
        }

        return new PurchaseAnalyticsResponse(round2(totalValue), round2(averageCost), purchasedThisYear, cancelledItems.size(),
                round2(moneySavedByCancelling), mustCount, shouldCount, niceCount, monthlyPlanned, monthlyCompleted);
    }

    // ============== Helpers ==============

    private void logActivity(PurchaseItemEntity item, String activityType, String fieldName, String oldValue, String newValue, String note) {
        PurchaseItemActivityEntity activity = new PurchaseItemActivityEntity();
        activity.setPurchaseItemId(item.getId());
        activity.setUserId(item.getUserId());
        activity.setActivityType(activityType);
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activity.setNote(note);
        activityRepository.save(activity);
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static Double parseDoubleSafe(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }
}
