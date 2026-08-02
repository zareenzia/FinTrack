package org.example.finzin.receipts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.ReceiptEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.gamification.GamificationEventType;
import org.example.finzin.receipts.dto.LinkReceiptRequest;
import org.example.finzin.receipts.dto.ReceiptResponse;
import org.example.finzin.receipts.extract.ReceiptFieldExtractionResult;
import org.example.finzin.receipts.extract.ReceiptFieldExtractor;
import org.example.finzin.receipts.ocr.OcrResult;
import org.example.finzin.receipts.ocr.ReceiptOcrEngine;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.ReceiptRepository;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for the non-cleanup public surface of ReceiptService: scanning
 * (settings/validation/OCR gating, successful extraction, best-effort file cleanup on OCR
 * failure), linking a draft receipt to a transaction, replacing a receipt's image, ownership
 * lookups, deletion, and the transactionId->receiptId lookup map. {@link ReceiptServiceCleanupTest}
 * already covers {@code cleanupExpiredReceipts}, so that method is intentionally not retested here.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private ReceiptRepository receiptRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReceiptStorageService storageService;
    @Mock private ReceiptOcrEngine ocrEngine;
    @Mock private ReceiptFieldExtractor fieldExtractor;
    @Mock private ReceiptSettingsService settingsService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ReceiptService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(receiptRepository, transactionRepository, categoryRepository,
                storageService, ocrEngine, fieldExtractor, settingsService, new ObjectMapper(), eventPublisher);
    }

    private ReceiptEntity receipt(Long id, String imagePath, Long transactionId) {
        ReceiptEntity e = new ReceiptEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setImagePath(imagePath);
        e.setMimeType("image/png");
        e.setFileSizeBytes(100L);
        e.setExtractionSource("HEURISTIC");
        e.setTransactionId(transactionId);
        return e;
    }

    private ReceiptFieldExtractionResult extraction(String merchant, Double amount) {
        return new ReceiptFieldExtractionResult(merchant, null, null, null, null, "USD", amount,
                null, null, null, null, List.of(), null, null, 0.8, Map.of(), "HEURISTIC", null);
    }

    private MultipartFile validFile() {
        return new MockMultipartFile("file", "receipt.png", "image/png", "img-bytes".getBytes());
    }

    // ================================================================================
    // scan — gating checks
    // ================================================================================

    @Test
    void scanThrowsWhenReceiptScannerDisabledForUser() {
        when(settingsService.isEnabled(USER_ID)).thenReturn(false);

        ReceiptException ex = assertThrows(ReceiptException.class, () -> service.scan(USER_ID, validFile()));

        assertEquals("SETTINGS_DISABLED", ex.getErrorTag());
        verifyNoInteractions(storageService, ocrEngine, fieldExtractor);
    }

    @Test
    void scanThrowsIllegalArgumentWhenFileValidationFails() {
        when(settingsService.isEnabled(USER_ID)).thenReturn(true);
        when(storageService.validate(any())).thenReturn(new ReceiptStorageService.ValidationError("bad file"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.scan(USER_ID, validFile()));

        assertEquals("bad file", ex.getMessage());
        verifyNoInteractions(ocrEngine);
    }

    @Test
    void scanThrowsWhenOcrEngineUnavailable() throws Exception {
        when(settingsService.isEnabled(USER_ID)).thenReturn(true);
        when(storageService.validate(any())).thenReturn(null);
        when(ocrEngine.isAvailable()).thenReturn(false);

        ReceiptException ex = assertThrows(ReceiptException.class, () -> service.scan(USER_ID, validFile()));

        assertEquals("OCR_NOT_CONFIGURED", ex.getErrorTag());
        verify(storageService, never()).save(any());
    }

    // ================================================================================
    // scan — success and OCR-failure cleanup
    // ================================================================================

    @Test
    void scanSavesEntityWithExtractedFieldsAndPublishesGamificationEvent() throws Exception {
        when(settingsService.isEnabled(USER_ID)).thenReturn(true);
        when(storageService.validate(any())).thenReturn(null);
        when(ocrEngine.isAvailable()).thenReturn(true);
        ReceiptStorageService.StoredFile stored = new ReceiptStorageService.StoredFile("abc.png", "image/png", 123L);
        when(storageService.save(any())).thenReturn(stored);
        Path resolvedPath = Paths.get("secure-uploads/receipts/abc.png");
        when(storageService.resolve("abc.png")).thenReturn(resolvedPath);
        when(ocrEngine.extractText(resolvedPath, "image/png")).thenReturn(new OcrResult("Walmart total 45.50"));
        when(fieldExtractor.extract("Walmart total 45.50")).thenReturn(extraction("Walmart", 45.50));
        when(receiptRepository.save(any())).thenAnswer(inv -> {
            ReceiptEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        ReceiptResponse response = service.scan(USER_ID, validFile());

        assertEquals("Walmart", response.merchantName());
        assertEquals(45.50, response.totalAmount());
        assertEquals("DRAFT", response.status());
        ArgumentCaptor<GamificationEvent> captor = ArgumentCaptor.forClass(GamificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(GamificationEventType.RECEIPT_SCANNED, captor.getValue().type());
        assertEquals(USER_ID, captor.getValue().userId());
    }

    @Test
    void scanDeletesStoredFileAndRethrowsWhenOcrExtractionThrows() throws Exception {
        when(settingsService.isEnabled(USER_ID)).thenReturn(true);
        when(storageService.validate(any())).thenReturn(null);
        when(ocrEngine.isAvailable()).thenReturn(true);
        ReceiptStorageService.StoredFile stored = new ReceiptStorageService.StoredFile("abc.png", "image/png", 123L);
        when(storageService.save(any())).thenReturn(stored);
        when(storageService.resolve("abc.png")).thenReturn(Paths.get("secure-uploads/receipts/abc.png"));
        when(ocrEngine.extractText(any(), any())).thenThrow(new RuntimeException("tesseract crashed"));

        assertThrows(RuntimeException.class, () -> service.scan(USER_ID, validFile()));

        verify(storageService).deleteBestEffort("abc.png");
        verify(receiptRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ================================================================================
    // link
    // ================================================================================

    @Test
    void linkReturnsEmptyWhenReceiptNotFoundOrNotOwned() {
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertTrue(service.link(USER_ID, 1L, new LinkReceiptRequest(9L, null, null)).isEmpty());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void linkThrowsWhenReceiptAlreadyLinked() {
        ReceiptEntity existing = receipt(1L, "a.png", 50L);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));

        ReceiptException ex = assertThrows(ReceiptException.class,
                () -> service.link(USER_ID, 1L, new LinkReceiptRequest(9L, null, null)));
        assertEquals("ALREADY_LINKED", ex.getErrorTag());
    }

    @Test
    void linkReturnsEmptyWhenTargetTransactionNotFoundOrNotOwned() {
        ReceiptEntity existing = receipt(1L, "a.png", null);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(transactionRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());

        assertTrue(service.link(USER_ID, 1L, new LinkReceiptRequest(9L, null, null)).isEmpty());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void linkSetsTransactionIdAndOverridesMerchantNameAndNotesWhenProvided() {
        ReceiptEntity existing = receipt(1L, "a.png", null);
        existing.setMerchantName("Original Merchant");
        TransactionEntity tx = new TransactionEntity();
        tx.setId(9L);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(transactionRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(tx));
        when(receiptRepository.save(existing)).thenReturn(existing);

        Optional<ReceiptResponse> result = service.link(USER_ID, 1L, new LinkReceiptRequest(9L, "  Corrected Store  ", "  gift  "));

        assertTrue(result.isPresent());
        assertEquals(9L, existing.getTransactionId());
        assertEquals("Corrected Store", existing.getMerchantName());
        assertEquals("gift", existing.getSuggestedNotes());
        assertEquals("CONFIRMED", result.get().status());
    }

    @Test
    void linkKeepsOriginalMerchantNameWhenOverrideIsBlank() {
        ReceiptEntity existing = receipt(1L, "a.png", null);
        existing.setMerchantName("Original Merchant");
        TransactionEntity tx = new TransactionEntity();
        tx.setId(9L);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(transactionRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(tx));
        when(receiptRepository.save(existing)).thenReturn(existing);

        service.link(USER_ID, 1L, new LinkReceiptRequest(9L, "   ", null));

        assertEquals("Original Merchant", existing.getMerchantName());
    }

    // ================================================================================
    // replaceImage
    // ================================================================================

    @Test
    void replaceImageReturnsEmptyWhenReceiptNotFound() throws Exception {
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertTrue(service.replaceImage(USER_ID, 1L, validFile()).isEmpty());
        verifyNoInteractions(storageService);
    }

    @Test
    void replaceImageThrowsIllegalArgumentWhenValidationFails() throws Exception {
        ReceiptEntity existing = receipt(1L, "old.png", null);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(storageService.validate(any())).thenReturn(new ReceiptStorageService.ValidationError("nope"));

        assertThrows(IllegalArgumentException.class, () -> service.replaceImage(USER_ID, 1L, validFile()));
    }

    @Test
    void replaceImageThrowsWhenOcrEngineUnavailable() {
        ReceiptEntity existing = receipt(1L, "old.png", null);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(storageService.validate(any())).thenReturn(null);
        when(ocrEngine.isAvailable()).thenReturn(false);

        ReceiptException ex = assertThrows(ReceiptException.class, () -> service.replaceImage(USER_ID, 1L, validFile()));
        assertEquals("OCR_NOT_CONFIGURED", ex.getErrorTag());
    }

    @Test
    void replaceImageStoresNewFileReextractsAndDeletesOldFile() throws Exception {
        ReceiptEntity existing = receipt(1L, "old.png", null);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(storageService.validate(any())).thenReturn(null);
        when(ocrEngine.isAvailable()).thenReturn(true);
        ReceiptStorageService.StoredFile stored = new ReceiptStorageService.StoredFile("new.png", "image/png", 200L);
        when(storageService.save(any())).thenReturn(stored);
        Path resolvedPath = Paths.get("secure-uploads/receipts/new.png");
        when(storageService.resolve("new.png")).thenReturn(resolvedPath);
        when(ocrEngine.extractText(resolvedPath, "image/png")).thenReturn(new OcrResult("Target total 20.00"));
        when(fieldExtractor.extract("Target total 20.00")).thenReturn(extraction("Target", 20.00));
        when(receiptRepository.save(existing)).thenReturn(existing);

        Optional<ReceiptResponse> result = service.replaceImage(USER_ID, 1L, validFile());

        assertTrue(result.isPresent());
        assertEquals("new.png", existing.getImagePath());
        assertEquals("Target", existing.getMerchantName());
        verify(storageService).deleteBestEffort("old.png");
    }

    @Test
    void replaceImageDeletesNewlyStoredFileAndRethrowsWhenOcrExtractionThrows() throws Exception {
        ReceiptEntity existing = receipt(1L, "old.png", null);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(storageService.validate(any())).thenReturn(null);
        when(ocrEngine.isAvailable()).thenReturn(true);
        ReceiptStorageService.StoredFile stored = new ReceiptStorageService.StoredFile("new.png", "image/png", 200L);
        when(storageService.save(any())).thenReturn(stored);
        when(storageService.resolve("new.png")).thenReturn(Paths.get("secure-uploads/receipts/new.png"));
        when(ocrEngine.extractText(any(), any())).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> service.replaceImage(USER_ID, 1L, validFile()));

        verify(storageService).deleteBestEffort("new.png");
        verify(storageService, never()).deleteBestEffort("old.png");
        assertEquals("old.png", existing.getImagePath(), "the entity must not be mutated when re-extraction fails");
    }

    // ================================================================================
    // findOwned / delete
    // ================================================================================

    @Test
    void findOwnedDelegatesToRepository() {
        ReceiptEntity existing = receipt(1L, "a.png", null);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));

        assertEquals(existing, service.findOwned(USER_ID, 1L).get());
    }

    @Test
    void deleteReturnsFalseWhenNotFound() {
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertFalse(service.delete(USER_ID, 1L));
        verifyNoInteractions(storageService);
    }

    @Test
    void deleteRemovesFileAndRowWhenFound() {
        ReceiptEntity existing = receipt(1L, "a.png", null);
        when(receiptRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));

        boolean result = service.delete(USER_ID, 1L);

        assertTrue(result);
        verify(storageService).deleteBestEffort("a.png");
        verify(receiptRepository).delete(existing);
    }

    // ================================================================================
    // byTransactionIds
    // ================================================================================

    @Test
    void byTransactionIdsBuildsMapKeyedByTransactionId() {
        ReceiptEntity r1 = receipt(1L, "a.png", 100L);
        ReceiptEntity r2 = receipt(2L, "b.png", 200L);
        when(receiptRepository.findByUserIdAndTransactionIdIn(USER_ID, List.of(100L, 200L))).thenReturn(List.of(r1, r2));

        Map<Long, Long> result = service.byTransactionIds(USER_ID, List.of(100L, 200L));

        assertEquals(1L, result.get(100L));
        assertEquals(2L, result.get(200L));
    }

    @Test
    void byTransactionIdsSkipsReceiptsWithNullTransactionId() {
        ReceiptEntity draft = receipt(1L, "a.png", null);
        when(receiptRepository.findByUserIdAndTransactionIdIn(eq(USER_ID), any())).thenReturn(List.of(draft));

        Map<Long, Long> result = service.byTransactionIds(USER_ID, List.of(100L));

        assertTrue(result.isEmpty());
    }
}
