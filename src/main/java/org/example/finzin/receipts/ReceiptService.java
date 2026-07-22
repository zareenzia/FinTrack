package org.example.finzin.receipts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.ReceiptEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.receipts.dto.LinkReceiptRequest;
import org.example.finzin.receipts.dto.ReceiptResponse;
import org.example.finzin.receipts.extract.ReceiptFieldExtractionResult;
import org.example.finzin.receipts.extract.ReceiptFieldExtractor;
import org.example.finzin.receipts.ocr.OcrResult;
import org.example.finzin.receipts.ocr.ReceiptOcrEngine;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.ReceiptRepository;
import org.example.finzin.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReceiptService {
    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final ReceiptRepository receiptRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final ReceiptStorageService storageService;
    private final ReceiptOcrEngine ocrEngine;
    private final ReceiptFieldExtractor fieldExtractor;
    private final ReceiptSettingsService settingsService;
    private final ObjectMapper objectMapper;

    public ReceiptService(ReceiptRepository receiptRepository, TransactionRepository transactionRepository,
                           CategoryRepository categoryRepository, ReceiptStorageService storageService,
                           ReceiptOcrEngine ocrEngine, ReceiptFieldExtractor fieldExtractor,
                           ReceiptSettingsService settingsService, ObjectMapper objectMapper) {
        this.receiptRepository = receiptRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.storageService = storageService;
        this.ocrEngine = ocrEngine;
        this.fieldExtractor = fieldExtractor;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public ReceiptResponse scan(Long userId, MultipartFile file) throws IOException {
        if (!settingsService.isEnabled(userId)) {
            throw ReceiptException.disabled();
        }
        ReceiptStorageService.ValidationError validationError = storageService.validate(file);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError.message());
        }
        if (!ocrEngine.isAvailable()) {
            throw ReceiptException.ocrNotConfigured();
        }

        ReceiptStorageService.StoredFile stored = storageService.save(file);
        OcrResult ocrResult;
        try {
            ocrResult = ocrEngine.extractText(storageService.resolve(stored.filename()), stored.mimeType());
        } catch (RuntimeException e) {
            storageService.deleteBestEffort(stored.filename());
            throw e;
        }

        ReceiptFieldExtractionResult extraction = fieldExtractor.extract(ocrResult.text());

        ReceiptEntity entity = new ReceiptEntity();
        entity.setUserId(userId);
        entity.setImagePath(stored.filename());
        entity.setMimeType(stored.mimeType());
        entity.setFileSizeBytes(stored.sizeBytes());
        entity.setOcrText(ocrResult.text());
        applyExtraction(entity, extraction, userId);

        ReceiptEntity saved = receiptRepository.save(entity);
        return ReceiptResponse.from(saved, objectMapper, extraction.warning());
    }

    /** Empty means "not found" (receipt or target transaction, either not owned or missing) — controller maps to 404 either way. */
    public Optional<ReceiptResponse> link(Long userId, Long receiptId, LinkReceiptRequest body) {
        Optional<ReceiptEntity> existing = receiptRepository.findByIdAndUserId(receiptId, userId);
        if (existing.isEmpty()) return Optional.empty();
        ReceiptEntity entity = existing.get();
        if (entity.getTransactionId() != null) {
            throw ReceiptException.alreadyLinked();
        }

        Optional<TransactionEntity> transaction = transactionRepository.findByIdAndUserId(body.transactionId(), userId);
        if (transaction.isEmpty()) return Optional.empty();

        entity.setTransactionId(transaction.get().getId());
        if (body.merchantName() != null && !body.merchantName().isBlank()) {
            entity.setMerchantName(body.merchantName().trim());
        }
        if (body.notes() != null && !body.notes().isBlank()) {
            entity.setSuggestedNotes(body.notes().trim());
        }
        ReceiptEntity saved = receiptRepository.save(entity);
        return Optional.of(ReceiptResponse.from(saved, objectMapper, null));
    }

    public Optional<ReceiptResponse> replaceImage(Long userId, Long receiptId, MultipartFile file) throws IOException {
        Optional<ReceiptEntity> existing = receiptRepository.findByIdAndUserId(receiptId, userId);
        if (existing.isEmpty()) return Optional.empty();

        ReceiptStorageService.ValidationError validationError = storageService.validate(file);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError.message());
        }
        if (!ocrEngine.isAvailable()) {
            throw ReceiptException.ocrNotConfigured();
        }

        ReceiptEntity entity = existing.get();
        String oldFilename = entity.getImagePath();

        ReceiptStorageService.StoredFile stored = storageService.save(file);
        OcrResult ocrResult;
        try {
            ocrResult = ocrEngine.extractText(storageService.resolve(stored.filename()), stored.mimeType());
        } catch (RuntimeException e) {
            storageService.deleteBestEffort(stored.filename());
            throw e;
        }
        ReceiptFieldExtractionResult extraction = fieldExtractor.extract(ocrResult.text());

        entity.setImagePath(stored.filename());
        entity.setMimeType(stored.mimeType());
        entity.setFileSizeBytes(stored.sizeBytes());
        entity.setOcrText(ocrResult.text());
        applyExtraction(entity, extraction, userId);

        ReceiptEntity saved = receiptRepository.save(entity);
        storageService.deleteBestEffort(oldFilename);
        return Optional.of(ReceiptResponse.from(saved, objectMapper, extraction.warning()));
    }

    public Optional<ReceiptEntity> findOwned(Long userId, Long receiptId) {
        return receiptRepository.findByIdAndUserId(receiptId, userId);
    }

    public boolean delete(Long userId, Long receiptId) {
        Optional<ReceiptEntity> existing = receiptRepository.findByIdAndUserId(receiptId, userId);
        if (existing.isEmpty()) return false;
        storageService.deleteBestEffort(existing.get().getImagePath());
        receiptRepository.delete(existing.get());
        return true;
    }

    public Map<Long, Long> byTransactionIds(Long userId, List<Long> transactionIds) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (ReceiptEntity receipt : receiptRepository.findByUserIdAndTransactionIdIn(userId, transactionIds)) {
            if (receipt.getTransactionId() != null) {
                result.put(receipt.getTransactionId(), receipt.getId());
            }
        }
        return result;
    }

    /**
     * Deletes every receipt (image file + row) created before {@code cutoff}, confirmed or not —
     * receipts are a scan-time convenience, not permanent storage, so nothing is kept indefinitely
     * regardless of whether it ended up linked to a transaction. The transaction itself is never
     * touched (no back-reference exists from TransactionEntity to ReceiptEntity).
     */
    @Transactional
    public int cleanupExpiredReceipts(LocalDateTime cutoff) {
        List<ReceiptEntity> expired = receiptRepository.findByCreatedAtBefore(cutoff);
        for (ReceiptEntity receipt : expired) {
            storageService.deleteBestEffort(receipt.getImagePath());
        }
        receiptRepository.deleteAll(expired);
        return expired.size();
    }

    private void applyExtraction(ReceiptEntity entity, ReceiptFieldExtractionResult extraction, Long userId) {
        entity.setMerchantName(extraction.merchantName());
        entity.setReceiptNumber(extraction.receiptNumber());
        entity.setInvoiceNumber(extraction.invoiceNumber());
        entity.setReceiptDate(extraction.receiptDate());
        entity.setReceiptTime(extraction.receiptTime());
        entity.setExtractedCurrency(extraction.currency());
        entity.setExtractedAmount(extraction.totalAmount());
        entity.setExtractedTax(extraction.taxAmount());
        entity.setExtractedDiscount(extraction.discountAmount());
        entity.setExtractedSubtotal(extraction.subtotalAmount());
        entity.setPaymentMethod(extraction.paymentMethod());
        entity.setSuggestedNotes(extraction.suggestedNotes());
        entity.setConfidenceScore(extraction.confidenceScore());
        entity.setExtractionSource(extraction.source());
        entity.setItemsJson(writeJsonSafely(extraction.items()));
        entity.setFieldConfidenceJson(writeJsonSafely(extraction.fieldConfidence()));

        entity.setPredictedCategoryLabel(extraction.predictedCategoryLabel());
        entity.setPredictedCategoryId(resolveCategoryId(userId, extraction.predictedCategoryLabel()));
    }

    private Long resolveCategoryId(Long userId, String predictedLabel) {
        if (predictedLabel == null || predictedLabel.isBlank()) return null;
        List<CategoryEntity> categories = categoryRepository.findByUserId(userId);
        for (CategoryEntity category : categories) {
            if (category.getName() != null && category.getName().trim().equalsIgnoreCase(predictedLabel.trim())) {
                return category.getId();
            }
        }
        return null;
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize receipt extraction field: {}", e.getMessage());
            return null;
        }
    }
}
