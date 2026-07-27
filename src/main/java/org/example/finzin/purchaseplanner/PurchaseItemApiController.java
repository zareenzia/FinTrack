package org.example.finzin.purchaseplanner;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.purchaseplanner.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Lives under the Financial Planner API namespace (matching the frontend's existing BASE='/api/financial-planner'
 * convention) even though it's its own dedicated controller class, unlike the older inline-Map style used for
 * Investments/Loans/Subscriptions in FinancialPlannerApiController. */
@RestController
@RequestMapping("/api/financial-planner/purchase-items")
public class PurchaseItemApiController {

    private final PurchaseItemService purchaseItemService;

    public PurchaseItemApiController(PurchaseItemService purchaseItemService) {
        this.purchaseItemService = purchaseItemService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public List<PurchaseItemResponse> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        return purchaseItemService.toResponseList(userId, purchaseItemService.listForUser(userId));
    }

    @GetMapping("/summary")
    public PurchaseDashboardSummaryResponse summary(HttpServletRequest request) {
        return purchaseItemService.computeDashboardSummary(getUserId(request));
    }

    @GetMapping("/analytics")
    public PurchaseAnalyticsResponse analytics(HttpServletRequest request) {
        return purchaseItemService.computeAnalytics(getUserId(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return purchaseItemService.findOwned(userId, id)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(purchaseItemService.toDetailResponse(userId, item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody PurchaseItemRequest body) {
        Long userId = getUserId(request);
        String error = purchaseItemService.validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        PurchaseItemEntity saved = purchaseItemService.create(userId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseItemService.toDetailResponse(userId, saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @RequestBody PurchaseItemRequest body) {
        Long userId = getUserId(request);
        String error = purchaseItemService.validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        return purchaseItemService.findOwned(userId, id)
                .<ResponseEntity<?>>map(existing -> ResponseEntity.ok(
                        purchaseItemService.toDetailResponse(userId, purchaseItemService.update(existing, body))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return purchaseItemService.findOwned(userId, id)
                .<ResponseEntity<?>>map(existing -> {
                    purchaseItemService.delete(existing);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/need-level")
    public ResponseEntity<?> patchNeedLevel(HttpServletRequest request, @PathVariable Long id, @RequestBody NeedLevelRequest body) {
        return withOwnedItem(request, id, item -> purchaseItemService.patchNeedLevel(item, body.needLevel()));
    }

    @PatchMapping("/{id}/target-month")
    public ResponseEntity<?> patchTargetMonth(HttpServletRequest request, @PathVariable Long id, @RequestBody TargetMonthRequest body) {
        return withOwnedItem(request, id, item -> purchaseItemService.patchTargetMonth(item, body.targetMonth()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> patchStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody StatusRequest body) {
        return withOwnedItem(request, id, item -> purchaseItemService.patchStatus(item, body.status()));
    }

    /** Persists a Kanban column's card order after a same-column drag-and-drop reorder.
     *  Not under /{id} since it mutates the whole column's ordering, not a single item. */
    @PatchMapping("/reorder")
    public ResponseEntity<?> reorder(HttpServletRequest request, @RequestBody ReorderRequest body) {
        Long userId = getUserId(request);
        try {
            purchaseItemService.reorderWithinColumn(userId, body.needLevel(), body.orderedIds());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (PurchaseItemException e) {
            return mapPurchaseItemException(e);
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(HttpServletRequest request, @PathVariable Long id, @RequestBody CancelPurchaseRequest body) {
        return withOwnedItem(request, id, item -> purchaseItemService.cancel(item, body.reason()));
    }

    @GetMapping("/{id}/expense-draft")
    public ResponseEntity<?> expenseDraft(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return purchaseItemService.findOwned(userId, id)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(purchaseItemService.buildExpenseDraft(item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/mark-purchased")
    public ResponseEntity<?> markPurchased(HttpServletRequest request, @PathVariable Long id, @RequestBody MarkPurchasedRequest body) {
        return withOwnedItem(request, id, item -> purchaseItemService.markPurchased(item, body.transactionId()));
    }

    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadImage(HttpServletRequest request, @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Long userId = getUserId(request);
        return purchaseItemService.findOwned(userId, id)
                .<ResponseEntity<?>>map(existing -> {
                    try {
                        PurchaseItemEntity saved = purchaseItemService.attachImage(existing, file);
                        return ResponseEntity.ok(purchaseItemService.toDetailResponse(userId, saved));
                    } catch (PurchaseItemException e) {
                        return mapPurchaseItemException(e);
                    } catch (IOException e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to save the uploaded file."));
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<?> deleteImage(HttpServletRequest request, @PathVariable Long id) {
        return withOwnedItem(request, id, purchaseItemService::removeImage);
    }

    /** Shared "find owned item, apply a service mutation, map exceptions" wrapper for the single-field PATCH/action endpoints. */
    private ResponseEntity<?> withOwnedItem(HttpServletRequest request, Long id, java.util.function.Function<PurchaseItemEntity, PurchaseItemEntity> mutation) {
        Long userId = getUserId(request);
        return purchaseItemService.findOwned(userId, id)
                .<ResponseEntity<?>>map(existing -> {
                    try {
                        PurchaseItemEntity saved = mutation.apply(existing);
                        return ResponseEntity.ok(purchaseItemService.toDetailResponse(userId, saved));
                    } catch (PurchaseItemException e) {
                        return mapPurchaseItemException(e);
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> mapPurchaseItemException(PurchaseItemException e) {
        HttpStatus status = "ALREADY_FINALIZED".equals(e.getErrorTag()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error", e.getUserMessage()));
    }
}
