package org.example.finzin.purchaseplanner;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.gamification.GamificationEventType;
import org.example.finzin.purchaseplanner.dto.ExpenseDraftResponse;
import org.example.finzin.purchaseplanner.dto.PurchaseDashboardSummaryResponse;
import org.example.finzin.purchaseplanner.dto.PurchaseItemRequest;
import org.example.finzin.purchaseplanner.dto.PurchaseItemResponse;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.PurchaseItemActivityRepository;
import org.example.finzin.repository.PurchaseItemRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching BudgetPlanServiceTest's convention) for PurchaseItemService:
 * validation, CRUD, Kanban status transitions with their activity-log/indexing side effects, the
 * Purchased/Cancelled finalization paths, and response assembly.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseItemServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private PurchaseItemActivityRepository activityRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PurchaseItemImageStorageService imageStorageService;
    @Mock private PurchaseAffordabilityService affordabilityService;
    @Mock private BudgetPlanService budgetPlanService;
    @Mock private DocumentIndexer documentIndexer;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PurchaseItemService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseItemService(purchaseItemRepository, activityRepository, categoryRepository,
                transactionRepository, imageStorageService, affordabilityService, budgetPlanService,
                documentIndexer, eventPublisher);
    }

    private PurchaseItemEntity purchaseItem(Long id, String needLevel, String status, double price) {
        PurchaseItemEntity p = new PurchaseItemEntity();
        p.setId(id);
        p.setUserId(USER_ID);
        p.setItemName("Laptop");
        p.setEstimatedPrice(price);
        p.setNeedLevel(needLevel);
        p.setPriority("MEDIUM");
        p.setStatus(status);
        return p;
    }

    private PurchaseItemRequest validRequest() {
        return new PurchaseItemRequest("Laptop", 1000.0, "Electronics", "MUST_HAVE", "HIGH",
                "Dell", "BestBuy", "https://example.com", "notes", "2026-09", "2026-09-15", null, "PLANNING");
    }

    // ================================================================================
    // validate
    // ================================================================================

    @Test
    void validateRejectsBlankOrMissingItemName() {
        assertEquals("Item name is required",
                service.validate(new PurchaseItemRequest("", 10.0, null, null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void validateRejectsNegativeOrMissingEstimatedPrice() {
        assertEquals("Estimated price must be a non-negative number",
                service.validate(new PurchaseItemRequest("Item", -1.0, null, null, null, null, null, null, null, null, null, null, null)));
        assertEquals("Estimated price must be a non-negative number",
                service.validate(new PurchaseItemRequest("Item", null, null, null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void validateRejectsInvalidNeedLevel() {
        assertEquals("needLevel must be one of MUST_HAVE, SHOULD_HAVE, NICE_TO_HAVE",
                service.validate(new PurchaseItemRequest("Item", 10.0, null, "URGENT", null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void validateRejectsInvalidPriority() {
        assertEquals("priority must be one of CRITICAL, HIGH, MEDIUM, LOW",
                service.validate(new PurchaseItemRequest("Item", 10.0, null, null, "URGENT", null, null, null, null, null, null, null, null)));
    }

    @Test
    void validateRejectsMalformedTargetMonth() {
        assertEquals("targetMonth must be in yyyy-MM format",
                service.validate(new PurchaseItemRequest("Item", 10.0, null, null, null, null, null, null, null, "not-a-month", null, null, null)));
    }

    @Test
    void validateRejectsMalformedExpectedPurchaseDate() {
        assertEquals("expectedPurchaseDate must be a valid date",
                service.validate(new PurchaseItemRequest("Item", 10.0, null, null, null, null, null, null, null, null, "not-a-date", null, null)));
    }

    @Test
    void validateRejectsInvalidStatus() {
        assertEquals("status must be one of PLANNING, WAITING, READY (use the dedicated endpoints for Purchased/Cancelled)",
                service.validate(new PurchaseItemRequest("Item", 10.0, null, null, null, null, null, null, null, null, null, null, "PURCHASED")));
    }

    @Test
    void validateAcceptsFullyValidRequest() {
        assertNull(service.validate(validRequest()));
    }

    // ================================================================================
    // create / update
    // ================================================================================

    @Test
    void createSavesLogsCreatedActivityAndIndexes() {
        when(purchaseItemRepository.save(any())).thenAnswer(inv -> {
            PurchaseItemEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        PurchaseItemEntity result = service.create(USER_ID, validRequest());

        assertEquals(USER_ID, result.getUserId());
        assertEquals("Laptop", result.getItemName());
        assertEquals("PLANNING", result.getStatus());
        ArgumentCaptor<org.example.finzin.entity.PurchaseItemActivityEntity> activityCaptor =
                ArgumentCaptor.forClass(org.example.finzin.entity.PurchaseItemActivityEntity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertEquals("CREATED", activityCaptor.getValue().getActivityType());
        verify(documentIndexer).indexPurchaseItem(result);
    }

    @Test
    void createDefaultsNeedLevelAndPriorityWhenBlank() {
        when(purchaseItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PurchaseItemRequest req = new PurchaseItemRequest("Item", 10.0, null, "", "", null, null, null, null, null, null, null, null);

        PurchaseItemEntity result = service.create(USER_ID, req);

        assertEquals("SHOULD_HAVE", result.getNeedLevel());
        assertEquals("MEDIUM", result.getPriority());
    }

    @Test
    void updateLogsPriceChangeActivityWhenPriceDiffers() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 500.0);
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.update(existing, validRequest());

        assertEquals(1000.0, existing.getEstimatedPrice());
        ArgumentCaptor<org.example.finzin.entity.PurchaseItemActivityEntity> captor =
                ArgumentCaptor.forClass(org.example.finzin.entity.PurchaseItemActivityEntity.class);
        verify(activityRepository, times(2)).save(captor.capture());
        List<String> types = captor.getAllValues().stream().map(org.example.finzin.entity.PurchaseItemActivityEntity::getActivityType).toList();
        assertTrue(types.contains("PRICE_CHANGE"));
        assertTrue(types.contains("EDITED"), "other fields also changed (category/brand/etc.) so EDITED must log too");
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    @Test
    void updateLogsNoActivityWhenNothingActuallyChanged() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 1000.0);
        existing.setPriority("HIGH");
        existing.setCategory("Electronics");
        existing.setBrand("Dell");
        existing.setStore("BestBuy");
        existing.setPurchaseUrl("https://example.com");
        existing.setNotes("notes");
        existing.setTargetMonth("2026-09");
        existing.setExpectedPurchaseDate(java.time.LocalDate.of(2026, 9, 15));
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.update(existing, validRequest());

        verify(activityRepository, never()).save(any());
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    // ================================================================================
    // patchNeedLevel / reorderWithinColumn
    // ================================================================================

    @Test
    void patchNeedLevelRejectsInvalidValue() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        assertThrows(PurchaseItemException.class, () -> service.patchNeedLevel(existing, "URGENT"));
    }

    @Test
    void patchNeedLevelIsNoOpWhenUnchanged() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);

        PurchaseItemEntity result = service.patchNeedLevel(existing, "MUST_HAVE");

        assertSame(existing, result);
        verify(purchaseItemRepository, never()).save(any());
    }

    @Test
    void patchNeedLevelClearsBoardPositionLogsAndIndexesOnChange() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        existing.setBoardPosition(3);
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.patchNeedLevel(existing, "SHOULD_HAVE");

        assertEquals("SHOULD_HAVE", existing.getNeedLevel());
        assertNull(existing.getBoardPosition());
        verify(activityRepository).save(any());
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    @Test
    void reorderWithinColumnRejectsInvalidNeedLevelOrEmptyIds() {
        assertThrows(PurchaseItemException.class, () -> service.reorderWithinColumn(USER_ID, "URGENT", List.of(1L)));
        assertThrows(PurchaseItemException.class, () -> service.reorderWithinColumn(USER_ID, "MUST_HAVE", List.of()));
    }

    @Test
    void reorderWithinColumnRejectsItemFromWrongUserOrColumn() {
        PurchaseItemEntity wrongColumn = purchaseItem(1L, "SHOULD_HAVE", "PLANNING", 100.0);
        when(purchaseItemRepository.findAllById(List.of(1L))).thenReturn(List.of(wrongColumn));

        assertThrows(PurchaseItemException.class, () -> service.reorderWithinColumn(USER_ID, "MUST_HAVE", List.of(1L)));
    }

    @Test
    void reorderWithinColumnAssignsSequentialPositions() {
        PurchaseItemEntity i1 = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        PurchaseItemEntity i2 = purchaseItem(2L, "MUST_HAVE", "PLANNING", 200.0);
        when(purchaseItemRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(i1, i2));

        service.reorderWithinColumn(USER_ID, "MUST_HAVE", List.of(2L, 1L));

        assertEquals(0, i2.getBoardPosition());
        assertEquals(1, i1.getBoardPosition());
        verify(purchaseItemRepository).saveAll(List.of(i2, i1));
    }

    // ================================================================================
    // patchTargetMonth
    // ================================================================================

    @Test
    void patchTargetMonthRejectsMalformedValue() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        assertThrows(PurchaseItemException.class, () -> service.patchTargetMonth(existing, "bad"));
    }

    @Test
    void patchTargetMonthTreatsBlankAsNullAndLogsChange() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        existing.setTargetMonth("2026-01");
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.patchTargetMonth(existing, "  ");

        assertNull(existing.getTargetMonth());
        verify(activityRepository).save(any());
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    // ================================================================================
    // patchStatus
    // ================================================================================

    @Test
    void patchStatusRejectsWhenAlreadyFinalized() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PURCHASED", 100.0);
        PurchaseItemException ex = assertThrows(PurchaseItemException.class, () -> service.patchStatus(existing, "WAITING"));
        assertEquals("ALREADY_FINALIZED", ex.getErrorTag());
    }

    @Test
    void patchStatusRejectsInvalidTargetStatus() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        assertThrows(PurchaseItemException.class, () -> service.patchStatus(existing, "PURCHASED"));
    }

    @Test
    void patchStatusChangesAndLogsWhenDifferent() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.patchStatus(existing, "WAITING");

        assertEquals("WAITING", existing.getStatus());
        verify(activityRepository).save(any());
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    // ================================================================================
    // cancel
    // ================================================================================

    @Test
    void cancelRejectsWhenAlreadyFinalized() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "CANCELLED", 100.0);
        assertThrows(PurchaseItemException.class, () -> service.cancel(existing, "NOT_NEEDED"));
    }

    @Test
    void cancelRejectsInvalidReason() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        assertThrows(PurchaseItemException.class, () -> service.cancel(existing, "BECAUSE"));
    }

    @Test
    void cancelSetsStatusReasonAndTimestampThenLogs() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.cancel(existing, "TOO_EXPENSIVE");

        assertEquals("CANCELLED", existing.getStatus());
        assertEquals("TOO_EXPENSIVE", existing.getCancelReason());
        assertTrue(existing.getCancelledAt() != null);
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    // ================================================================================
    // buildExpenseDraft
    // ================================================================================

    @Test
    void buildExpenseDraftResolvesCategoryIdCaseInsensitivelyAndBuildsDetails() {
        PurchaseItemEntity item = purchaseItem(1L, "MUST_HAVE", "PLANNING", 250.0);
        item.setCategory("electronics");
        item.setStore("BestBuy");
        item.setBrand("Dell");
        CategoryEntity cat = new CategoryEntity(USER_ID, "Electronics", null, "#fff", "tag");
        cat.setId(9L);
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(cat));

        ExpenseDraftResponse draft = service.buildExpenseDraft(item);

        assertEquals(9L, draft.categoryId());
        assertEquals("Purchased from BestBuy · Dell", draft.details());
        assertEquals(250.0, draft.amount());
    }

    @Test
    void buildExpenseDraftLeavesCategoryNullWhenNoLabelSet() {
        PurchaseItemEntity item = purchaseItem(1L, "MUST_HAVE", "PLANNING", 250.0);

        ExpenseDraftResponse draft = service.buildExpenseDraft(item);

        assertNull(draft.categoryId());
        verifyNoInteractions(categoryRepository);
    }

    // ================================================================================
    // markPurchased
    // ================================================================================

    @Test
    void markPurchasedRejectsWhenAlreadyFinalized() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "CANCELLED", 100.0);
        assertThrows(PurchaseItemException.class, () -> service.markPurchased(existing, null));
    }

    @Test
    void markPurchasedRejectsUnownedOrMissingTransaction() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        when(transactionRepository.findByIdAndUserId(77L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(PurchaseItemException.class, () -> service.markPurchased(existing, 77L));
    }

    @Test
    void markPurchasedLinksTransactionSetsStatusAndPublishesGoalCompletedEvent() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        TransactionEntity tx = new TransactionEntity();
        tx.setId(77L);
        when(transactionRepository.findByIdAndUserId(77L, USER_ID)).thenReturn(Optional.of(tx));
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.markPurchased(existing, 77L);

        assertEquals("PURCHASED", existing.getStatus());
        assertEquals(77L, existing.getLinkedTransactionId());
        assertTrue(existing.getPurchasedAt() != null);
        ArgumentCaptor<GamificationEvent> eventCaptor = ArgumentCaptor.forClass(GamificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(GamificationEventType.GOAL_COMPLETED, eventCaptor.getValue().type());
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    @Test
    void markPurchasedWithoutTransactionIdJustFinalizesStatus() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.markPurchased(existing, null);

        assertEquals("PURCHASED", existing.getStatus());
        assertNull(existing.getLinkedTransactionId());
        verifyNoInteractions(transactionRepository);
    }

    // ================================================================================
    // delete
    // ================================================================================

    @Test
    void deleteRemovesImageActivityAndItemThenDeindexes() {
        PurchaseItemEntity existing = purchaseItem(5L, "MUST_HAVE", "PLANNING", 100.0);
        existing.setImagePath("photo.png");

        service.delete(existing);

        verify(imageStorageService).deleteBestEffort("photo.png");
        verify(activityRepository).deleteByPurchaseItemId(5L);
        verify(purchaseItemRepository).deleteById(5L);
        verify(documentIndexer).deletePurchaseItem(USER_ID, 5L);
    }

    // ================================================================================
    // attachImage / removeImage
    // ================================================================================

    @Test
    void attachImageThrowsWhenValidationFails() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "abc".getBytes());
        when(imageStorageService.validate(file)).thenReturn(new PurchaseItemImageStorageService.ValidationError("bad type"));

        PurchaseItemException ex = assertThrows(PurchaseItemException.class, () -> service.attachImage(existing, file));
        assertEquals("bad type", ex.getUserMessage());
    }

    @Test
    void attachImageReplacesExistingImageAndIndexes() throws Exception {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        existing.setImagePath("old.png");
        MockMultipartFile file = new MockMultipartFile("file", "new.png", "image/png", "img".getBytes());
        when(imageStorageService.validate(file)).thenReturn(null);
        when(imageStorageService.save(file)).thenReturn(new PurchaseItemImageStorageService.StoredFile("new.png", "/user-uploads/purchase-items/new.png"));
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.attachImage(existing, file);

        verify(imageStorageService).deleteBestEffort("old.png");
        assertEquals("new.png", existing.getImagePath());
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    @Test
    void removeImageIsNoOpWhenNoImagePresent() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);

        PurchaseItemEntity result = service.removeImage(existing);

        assertSame(existing, result);
        verifyNoInteractions(imageStorageService);
        verify(purchaseItemRepository, never()).save(any());
    }

    @Test
    void removeImageClearsPathDeletesFileAndIndexes() {
        PurchaseItemEntity existing = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        existing.setImagePath("photo.png");
        when(purchaseItemRepository.save(existing)).thenReturn(existing);

        service.removeImage(existing);

        verify(imageStorageService).deleteBestEffort("photo.png");
        assertNull(existing.getImagePath());
        verify(documentIndexer).indexPurchaseItem(existing);
    }

    // ================================================================================
    // Response assembly / dashboard / analytics
    // ================================================================================

    private void stubAffordabilityDefaults() {
        lenient().when(affordabilityService.buildContext(eq(USER_ID), any()))
                .thenReturn(new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of()));
        lenient().when(affordabilityService.computeAffordability(any(), any()))
                .thenReturn(new PurchaseAffordabilityService.AffordabilityResult("CAN_BUY_NOW", "Can Buy Now", 0, 100.0, 50.0));
        lenient().when(affordabilityService.computeDecisionMatrix(any(), any()))
                .thenReturn(new PurchaseAffordabilityService.DecisionMatrixResult(5, 5, 5, 5, 100.0));
    }

    @Test
    void toResponseIncludesLinkedGoalNameAndProgressWhenLinked() {
        stubAffordabilityDefaults();
        PurchaseItemEntity item = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        item.setLinkedSavingsGoalId(9L);
        Map<String, Object> goalStatus = Map.of("categoryName", "Vacation Fund", "percentUsed", 42.0);
        when(budgetPlanService.findSavingsBudgetStatus(USER_ID, 9L)).thenReturn(Optional.of(goalStatus));

        PurchaseItemResponse response = service.toResponse(item,
                new PurchaseAffordabilityService.AffordabilityContext(0, 0, 0, 0, List.of()));

        assertEquals("Vacation Fund", response.linkedSavingsGoalName());
        assertEquals(42.0, response.linkedSavingsGoalProgressPercent());
    }

    @Test
    void toResponseListOnlyBuildsAffordabilityContextFromActiveStatuses() {
        stubAffordabilityDefaults();
        PurchaseItemEntity active = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        PurchaseItemEntity purchased = purchaseItem(2L, "MUST_HAVE", "PURCHASED", 200.0);

        List<PurchaseItemResponse> result = service.toResponseList(USER_ID, List.of(active, purchased));

        assertEquals(2, result.size());
        ArgumentCaptor<List<PurchaseItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(affordabilityService).buildContext(eq(USER_ID), captor.capture());
        assertEquals(List.of(active), captor.getValue());
    }

    @Test
    void computeDashboardSummaryAggregatesOnlyActiveItems() {
        PurchaseItemEntity must = purchaseItem(1L, "MUST_HAVE", "PLANNING", 100.0);
        PurchaseItemEntity should = purchaseItem(2L, "SHOULD_HAVE", "READY", 200.0);
        when(purchaseItemRepository.findByUserIdAndStatusIn(eq(USER_ID), any())).thenReturn(List.of(must, should));

        PurchaseDashboardSummaryResponse summary = service.computeDashboardSummary(USER_ID);

        assertEquals(300.0, summary.totalWishlistValue());
        assertEquals(1, summary.mustHaveCount());
        assertEquals(1, summary.shouldHaveCount());
        assertEquals(0, summary.niceToHaveCount());
        assertEquals(1, summary.readyToBuyCount());
        assertEquals(2, summary.activeCount());
    }

    @Test
    void computeAnalyticsCountsCancelledAndMoneySaved() {
        PurchaseItemEntity cancelled = purchaseItem(1L, "MUST_HAVE", "CANCELLED", 500.0);
        PurchaseItemEntity active = purchaseItem(2L, "SHOULD_HAVE", "PLANNING", 100.0);
        when(purchaseItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cancelled, active));

        var analytics = service.computeAnalytics(USER_ID);

        assertEquals(1, analytics.cancelledCount());
        assertEquals(500.0, analytics.moneySavedByCancelling());
        assertEquals(100.0, analytics.totalWishlistValue());
    }
}
