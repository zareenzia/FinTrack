package org.example.finzin.receipts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.ReceiptEntity;
import org.example.finzin.receipts.extract.ReceiptFieldExtractor;
import org.example.finzin.receipts.ocr.ReceiptOcrEngine;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.ReceiptRepository;
import org.example.finzin.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Receipts are a scan-time convenience, never permanent storage — every receipt (image file + row)
 * must be deleted after the retention window regardless of whether it was ever confirmed into a
 * transaction. This guards that both the file and the row are removed, and that untouched rows
 * are left alone.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceCleanupTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReceiptStorageService storageService;
    @Mock private ReceiptOcrEngine ocrEngine;
    @Mock private ReceiptFieldExtractor fieldExtractor;
    @Mock private ReceiptSettingsService settingsService;

    private ReceiptService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(receiptRepository, transactionRepository, categoryRepository,
                storageService, ocrEngine, fieldExtractor, settingsService, new ObjectMapper());
    }

    private ReceiptEntity receipt(Long id, String imagePath, Long transactionId) {
        ReceiptEntity e = new ReceiptEntity();
        e.setId(id);
        e.setUserId(1L);
        e.setImagePath(imagePath);
        e.setMimeType("image/png");
        e.setFileSizeBytes(100L);
        e.setExtractionSource("HEURISTIC");
        e.setTransactionId(transactionId);
        return e;
    }

    @Test
    void deletesBothTheFileAndTheRowForEveryExpiredReceiptRegardlessOfConfirmationStatus() {
        ReceiptEntity draft = receipt(1L, "draft.png", null);
        ReceiptEntity confirmed = receipt(2L, "confirmed.png", 99L);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
        when(receiptRepository.findByCreatedAtBefore(cutoff)).thenReturn(List.of(draft, confirmed));

        int deleted = service.cleanupExpiredReceipts(cutoff);

        assertEquals(2, deleted);
        verify(storageService).deleteBestEffort("draft.png");
        verify(storageService).deleteBestEffort("confirmed.png");
        verify(receiptRepository).deleteAll(List.of(draft, confirmed));
    }

    @Test
    void doesNothingWhenNoReceiptsAreExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
        when(receiptRepository.findByCreatedAtBefore(cutoff)).thenReturn(List.of());

        int deleted = service.cleanupExpiredReceipts(cutoff);

        assertEquals(0, deleted);
        verify(storageService, times(0)).deleteBestEffort(any());
    }
}
