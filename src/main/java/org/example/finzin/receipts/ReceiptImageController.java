package org.example.finzin.receipts;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.ReceiptEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Streams receipt image bytes. Kept separate from {@link ReceiptApiController} only because this
 * returns a binary body instead of JSON. Authenticated by the same ownership check as every other
 * per-user resource in this app — receipts are NOT served through the public {@code /user-uploads/**}
 * static path used for profile pictures, since they're materially more sensitive.
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptImageController {
    private static final Logger log = LoggerFactory.getLogger(ReceiptImageController.class);

    private final ReceiptService receiptService;
    private final ReceiptStorageService storageService;

    public ReceiptImageController(ReceiptService receiptService, ReceiptStorageService storageService) {
        this.receiptService = receiptService;
        this.storageService = storageService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<?> getImage(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        Optional<ReceiptEntity> receipt = receiptService.findOwned(userId, id);
        if (receipt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Receipt not found"));
        }
        ReceiptEntity entity = receipt.get();
        Path path = storageService.resolve(entity.getImagePath());
        if (!Files.isRegularFile(path)) {
            log.warn("Receipt {} references a missing file on disk: {}", entity.getId(), path);
            return ResponseEntity.status(404).body(Map.of("error", "Receipt image not found"));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(entity.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + entity.getId() + "\"")
                .body(new FileSystemResource(path));
    }
}
