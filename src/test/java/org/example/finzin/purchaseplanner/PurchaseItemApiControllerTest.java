package org.example.finzin.purchaseplanner;

import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.purchaseplanner.dto.AffordabilityView;
import org.example.finzin.purchaseplanner.dto.DecisionMatrixView;
import org.example.finzin.purchaseplanner.dto.ExpenseDraftResponse;
import org.example.finzin.purchaseplanner.dto.PurchaseAnalyticsResponse;
import org.example.finzin.purchaseplanner.dto.PurchaseDashboardSummaryResponse;
import org.example.finzin.purchaseplanner.dto.PurchaseItemDetailResponse;
import org.example.finzin.purchaseplanner.dto.PurchaseItemResponse;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link PurchaseItemApiController}. */
@WebMvcTest(PurchaseItemApiController.class)
class PurchaseItemApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private PurchaseItemService purchaseItemService;

    private PurchaseItemEntity item(Long id) {
        PurchaseItemEntity e = new PurchaseItemEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setItemName("Laptop");
        e.setEstimatedPrice(1500.0);
        e.setNeedLevel("MUST_HAVE");
        e.setPriority("HIGH");
        e.setStatus("PLANNING");
        return e;
    }

    private PurchaseItemResponse response(Long id, String status) {
        return new PurchaseItemResponse(id, "Laptop", 1500.0, "Electronics", "MUST_HAVE", "HIGH",
                null, null, null, null, null, null, null, null, null, null, null,
                status, null,
                new AffordabilityView("WAIT", "Wait 2 Months", 2, 500.0, 250.0),
                new DecisionMatrixView(4.0, 3.0, 2.0, 3.0, 60.0),
                null, null, "2026-01-01T00:00:00", "2026-01-01T00:00:00", null);
    }

    private PurchaseItemDetailResponse detailResponse(Long id, String status) {
        return new PurchaseItemDetailResponse(response(id, status), List.of(), List.of());
    }

    // ── GET / ──────────────────────────────────────────────────────────────

    @Test
    void list_returnsMappedItems() throws Exception {
        PurchaseItemEntity e = item(1L);
        given(purchaseItemService.listForUser(USER_ID)).willReturn(List.of(e));
        given(purchaseItemService.toResponseList(USER_ID, List.of(e))).willReturn(List.of(response(1L, "PLANNING")));

        mockMvc.perform(get("/api/financial-planner/purchase-items").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].itemName").value("Laptop"));
    }

    // ── GET /summary ───────────────────────────────────────────────────────

    @Test
    void summary_returnsDashboardSummary() throws Exception {
        given(purchaseItemService.computeDashboardSummary(USER_ID)).willReturn(
                new PurchaseDashboardSummaryResponse(1500.0, 1, 0, 0, 0, 0.0, 1));

        mockMvc.perform(get("/api/financial-planner/purchase-items/summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWishlistValue").value(1500.0))
                .andExpect(jsonPath("$.mustHaveCount").value(1));
    }

    // ── GET /analytics ─────────────────────────────────────────────────────

    @Test
    void analytics_returnsAnalyticsResponse() throws Exception {
        given(purchaseItemService.computeAnalytics(USER_ID)).willReturn(
                new PurchaseAnalyticsResponse(1500.0, 1500.0, 0, 0, 0.0, 1, 0, 0, List.of(), List.of()));

        mockMvc.perform(get("/api/financial-planner/purchase-items/analytics").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageItemCost").value(1500.0));
    }

    // ── GET /{id} ──────────────────────────────────────────────────────────

    @Test
    void getDetail_returnsNotFound_whenNotOwned() throws Exception {
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/financial-planner/purchase-items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDetail_returnsDetail_onSuccess() throws Exception {
        PurchaseItemEntity e = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(e));
        given(purchaseItemService.toDetailResponse(USER_ID, e)).willReturn(detailResponse(1L, "PLANNING"));

        mockMvc.perform(get("/api/financial-planner/purchase-items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.id").value(1));
    }

    // ── POST / (create) ────────────────────────────────────────────────────

    @Test
    void create_returnsBadRequest_whenValidationFails() throws Exception {
        given(purchaseItemService.validate(any())).willReturn("Item name is required");

        mockMvc.perform(post("/api/financial-planner/purchase-items").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedPrice\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Item name is required"));
    }

    @Test
    void create_returnsCreatedItem_onSuccess() throws Exception {
        given(purchaseItemService.validate(any())).willReturn(null);
        PurchaseItemEntity saved = item(9L);
        given(purchaseItemService.create(eq(USER_ID), any())).willReturn(saved);
        given(purchaseItemService.toDetailResponse(USER_ID, saved)).willReturn(detailResponse(9L, "PLANNING"));

        mockMvc.perform(post("/api/financial-planner/purchase-items").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Laptop\",\"estimatedPrice\":1500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.id").value(9));
    }

    // ── PUT /{id} (update) ─────────────────────────────────────────────────

    @Test
    void update_returnsNotFound_whenNotOwned() throws Exception {
        given(purchaseItemService.validate(any())).willReturn(null);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/financial-planner/purchase-items/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Laptop\",\"estimatedPrice\":1500}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returnsUpdatedItem_onSuccess() throws Exception {
        given(purchaseItemService.validate(any())).willReturn(null);
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.update(eq(existing), any())).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "PLANNING"));

        mockMvc.perform(put("/api/financial-planner/purchase-items/1").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Laptop\",\"estimatedPrice\":1600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.id").value(1));
    }

    // ── DELETE /{id} ───────────────────────────────────────────────────────

    @Test
    void delete_returnsNotFound_whenNotOwned() throws Exception {
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/financial-planner/purchase-items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());

        verify(purchaseItemService, never()).delete(any());
    }

    @Test
    void delete_returnsNoContent_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/financial-planner/purchase-items/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(purchaseItemService).delete(existing);
    }

    // ── PATCH /{id}/need-level ─────────────────────────────────────────────

    @Test
    void patchNeedLevel_returnsUpdatedItem_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.patchNeedLevel(existing, "SHOULD_HAVE")).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "PLANNING"));

        mockMvc.perform(patch("/api/financial-planner/purchase-items/1/need-level").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"needLevel\":\"SHOULD_HAVE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void patchNeedLevel_returnsBadRequest_whenServiceRejects() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.patchNeedLevel(existing, "INVALID"))
                .willThrow(PurchaseItemException.badRequest("needLevel must be one of MUST_HAVE, SHOULD_HAVE, NICE_TO_HAVE"));

        mockMvc.perform(patch("/api/financial-planner/purchase-items/1/need-level").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"needLevel\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchNeedLevel_returnsNotFound_whenNotOwned() throws Exception {
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(patch("/api/financial-planner/purchase-items/1/need-level").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"needLevel\":\"SHOULD_HAVE\"}"))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /{id}/target-month ───────────────────────────────────────────

    @Test
    void patchTargetMonth_returnsUpdatedItem_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.patchTargetMonth(existing, "2026-03")).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "PLANNING"));

        mockMvc.perform(patch("/api/financial-planner/purchase-items/1/target-month").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMonth\":\"2026-03\"}"))
                .andExpect(status().isOk());
    }

    // ── PATCH /{id}/status ─────────────────────────────────────────────────

    @Test
    void patchStatus_returnsConflict_whenAlreadyFinalized() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.patchStatus(existing, "READY")).willThrow(PurchaseItemException.alreadyFinalized("PURCHASED"));

        mockMvc.perform(patch("/api/financial-planner/purchase-items/1/status").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void patchStatus_returnsUpdatedItem_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.patchStatus(existing, "READY")).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "READY"));

        mockMvc.perform(patch("/api/financial-planner/purchase-items/1/status").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("READY"));
    }

    // ── PATCH /reorder ─────────────────────────────────────────────────────

    @Test
    void reorder_returnsSuccessTrue_onSuccess() throws Exception {
        mockMvc.perform(patch("/api/financial-planner/purchase-items/reorder").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"needLevel\":\"MUST_HAVE\",\"orderedIds\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(purchaseItemService).reorderWithinColumn(USER_ID, "MUST_HAVE", List.of(1L, 2L, 3L));
    }

    @Test
    void reorder_returnsBadRequest_whenServiceRejects() throws Exception {
        org.mockito.Mockito.doThrow(PurchaseItemException.badRequest("orderedIds must not be empty"))
                .when(purchaseItemService).reorderWithinColumn(eq(USER_ID), eq("MUST_HAVE"), any());

        mockMvc.perform(patch("/api/financial-planner/purchase-items/reorder").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"needLevel\":\"MUST_HAVE\",\"orderedIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /{id}/cancel ──────────────────────────────────────────────────

    @Test
    void cancel_returnsUpdatedItem_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.cancel(existing, "NOT_NEEDED")).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "CANCELLED"));

        mockMvc.perform(post("/api/financial-planner/purchase-items/1/cancel").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_NEEDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("CANCELLED"));
    }

    // ── GET /{id}/expense-draft ────────────────────────────────────────────

    @Test
    void expenseDraft_returnsNotFound_whenNotOwned() throws Exception {
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/financial-planner/purchase-items/1/expense-draft").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void expenseDraft_returnsDraft_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.buildExpenseDraft(existing)).willReturn(
                new ExpenseDraftResponse(1500.0, "Laptop", 3L, "Electronics", "2026-01-01", "Purchased from BestBuy"));

        mockMvc.perform(get("/api/financial-planner/purchase-items/1/expense-draft").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1500.0))
                .andExpect(jsonPath("$.categoryName").value("Electronics"));
    }

    // ── POST /{id}/mark-purchased ──────────────────────────────────────────

    @Test
    void markPurchased_returnsUpdatedItem_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.markPurchased(existing, 55L)).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "PURCHASED"));

        mockMvc.perform(post("/api/financial-planner/purchase-items/1/mark-purchased").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":55}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("PURCHASED"));
    }

    // ── POST /{id}/image ───────────────────────────────────────────────────

    @Test
    void uploadImage_returnsNotFound_whenNotOwned() throws Exception {
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/api/financial-planner/purchase-items/1/image").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadImage_returnsUpdatedItem_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.attachImage(eq(existing), any())).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "PLANNING"));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/api/financial-planner/purchase-items/1/image").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    void uploadImage_returnsBadRequest_whenValidationFails() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.attachImage(eq(existing), any())).willThrow(PurchaseItemException.badRequest("File too large"));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/api/financial-planner/purchase-items/1/image").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File too large"));
    }

    // ── DELETE /{id}/image ─────────────────────────────────────────────────

    @Test
    void deleteImage_returnsUpdatedItem_onSuccess() throws Exception {
        PurchaseItemEntity existing = item(1L);
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.of(existing));
        given(purchaseItemService.removeImage(existing)).willReturn(existing);
        given(purchaseItemService.toDetailResponse(USER_ID, existing)).willReturn(detailResponse(1L, "PLANNING"));

        mockMvc.perform(delete("/api/financial-planner/purchase-items/1/image").requestAttr("userId", USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    void deleteImage_returnsNotFound_whenNotOwned() throws Exception {
        given(purchaseItemService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/financial-planner/purchase-items/1/image").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }
}
