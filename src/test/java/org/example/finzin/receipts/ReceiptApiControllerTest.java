package org.example.finzin.receipts;

import org.example.finzin.receipts.dto.ReceiptResponse;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link ReceiptApiController}. */
@WebMvcTest(ReceiptApiController.class)
class ReceiptApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private ReceiptService receiptService;

    private ReceiptResponse receiptResponse(Long id, String status, Long transactionId) {
        return new ReceiptResponse(id, status, transactionId, "Walmart", null, null, null, null, "USD", 25.5,
                null, null, null, null, List.of(), null, null, null, null, Map.of(), "HEURISTIC", null,
                "/api/receipts/" + id + "/image", "image/jpeg", 1024L, null,
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    // ── POST /scan ─────────────────────────────────────────────────────────

    @Test
    void scan_returnsCreatedReceipt_onSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());
        given(receiptService.scan(eq(USER_ID), any())).willReturn(receiptResponse(1L, "DRAFT", null));

        mockMvc.perform(multipart("/api/receipts/scan").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void scan_returnsBadRequest_whenValidationFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.txt", "text/plain", "data".getBytes());
        given(receiptService.scan(eq(USER_ID), any())).willThrow(new IllegalArgumentException("Unsupported file type. Use JPG, PNG, or WebP."));

        mockMvc.perform(multipart("/api/receipts/scan").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported file type. Use JPG, PNG, or WebP."));
    }

    @Test
    void scan_returnsServiceUnavailable_whenDisabled() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());
        given(receiptService.scan(eq(USER_ID), any())).willThrow(ReceiptException.disabled());

        mockMvc.perform(multipart("/api/receipts/scan").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void scan_returnsUnprocessable_whenOcrFailed() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());
        given(receiptService.scan(eq(USER_ID), any())).willThrow(ReceiptException.ocrFailed());

        mockMvc.perform(multipart("/api/receipts/scan").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().is(422));
    }

    @Test
    void scan_returnsInternalServerError_onIOException() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());
        given(receiptService.scan(eq(USER_ID), any())).willThrow(new java.io.IOException("disk full"));

        mockMvc.perform(multipart("/api/receipts/scan").file(file).requestAttr("userId", USER_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to save the uploaded file."));
    }

    // ── POST /{id}/link ────────────────────────────────────────────────────

    @Test
    void link_returnsBadRequest_whenTransactionIdMissing() throws Exception {
        // A literal JSON "null" body never reaches the controller: Spring rejects a null-deserialized
        // non-optional @RequestBody with HttpMessageNotReadableException before the handler method
        // runs, so the response is a framework-generated 400 with no body (not the controller's
        // "transactionId is required" JSON) and the service is never invoked.
        mockMvc.perform(post("/api/receipts/1/link").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(receiptService);
    }

    @Test
    void link_returnsNotFound_whenReceiptOrTransactionMissing() throws Exception {
        given(receiptService.link(eq(USER_ID), eq(1L), any())).willReturn(Optional.empty());

        mockMvc.perform(post("/api/receipts/1/link").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void link_returnsLinkedReceipt_onSuccess() throws Exception {
        given(receiptService.link(eq(USER_ID), eq(1L), any())).willReturn(Optional.of(receiptResponse(1L, "CONFIRMED", 5L)));

        mockMvc.perform(post("/api/receipts/1/link").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.transactionId").value(5));
    }

    @Test
    void link_returnsConflict_whenAlreadyLinked() throws Exception {
        given(receiptService.link(eq(USER_ID), eq(1L), any())).willThrow(ReceiptException.alreadyLinked());

        mockMvc.perform(post("/api/receipts/1/link").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":5}"))
                .andExpect(status().isConflict());
    }

    // ── PUT /{id}/image ────────────────────────────────────────────────────

    @Test
    void replaceImage_returnsNotFound_whenReceiptMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());
        given(receiptService.replaceImage(eq(USER_ID), eq(1L), any())).willReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/receipts/1/image").file(file)
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceImage_returnsUpdatedReceipt_onSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());
        given(receiptService.replaceImage(eq(USER_ID), eq(1L), any())).willReturn(Optional.of(receiptResponse(1L, "DRAFT", null)));

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/receipts/1/image").file(file)
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── DELETE /{id} ───────────────────────────────────────────────────────

    @Test
    void delete_returnsNotFound_whenReceiptMissing() throws Exception {
        given(receiptService.delete(USER_ID, 1L)).willReturn(false);

        mockMvc.perform(delete("/api/receipts/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returnsNoContent_onSuccess() throws Exception {
        given(receiptService.delete(USER_ID, 1L)).willReturn(true);

        mockMvc.perform(delete("/api/receipts/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());
    }

    // ── GET /by-transaction ────────────────────────────────────────────────

    @Test
    void byTransaction_returnsMapping() throws Exception {
        given(receiptService.byTransactionIds(USER_ID, List.of(1L, 2L))).willReturn(Map.of(1L, 10L, 2L, 20L));

        mockMvc.perform(get("/api/receipts/by-transaction").requestAttr("userId", USER_ID).param("ids", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1']").value(10))
                .andExpect(jsonPath("$['2']").value(20));
    }

    @Test
    void byTransaction_returnsEmptyMap_whenIdsBlank() throws Exception {
        mockMvc.perform(get("/api/receipts/by-transaction").requestAttr("userId", USER_ID).param("ids", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
