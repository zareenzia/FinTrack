package org.example.finzin.receipts;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.receipts.dto.LinkReceiptRequest;
import org.example.finzin.receipts.dto.ReceiptResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptApiController {
    private final ReceiptService receiptService;

    public ReceiptApiController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @PostMapping(value = "/scan", consumes = "multipart/form-data")
    public ResponseEntity<?> scan(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        Long userId = getUserId(request);
        try {
            ReceiptResponse response = receiptService.scan(userId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ReceiptException e) {
            return mapReceiptException(e);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to save the uploaded file."));
        }
    }

    @PostMapping("/{id}/link")
    public ResponseEntity<?> link(HttpServletRequest request, @PathVariable Long id, @RequestBody LinkReceiptRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.transactionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "transactionId is required"));
        }
        try {
            return receiptService.link(userId, id, body)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Receipt or transaction not found")));
        } catch (ReceiptException e) {
            return mapReceiptException(e);
        }
    }

    @PutMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public ResponseEntity<?> replaceImage(HttpServletRequest request, @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Long userId = getUserId(request);
        try {
            return receiptService.replaceImage(userId, id, file)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Receipt not found")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ReceiptException e) {
            return mapReceiptException(e);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to save the uploaded file."));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        boolean deleted = receiptService.delete(userId, id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Receipt not found"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-transaction")
    public Map<Long, Long> byTransaction(HttpServletRequest request, @RequestParam("ids") String ids) {
        Long userId = getUserId(request);
        List<Long> transactionIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        if (transactionIds.isEmpty()) return Map.of();
        return new LinkedHashMap<>(receiptService.byTransactionIds(userId, transactionIds));
    }

    private ResponseEntity<?> mapReceiptException(ReceiptException e) {
        HttpStatus status = switch (e.getErrorTag()) {
            case "OCR_NOT_CONFIGURED", "SETTINGS_DISABLED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "OCR_FAILED" -> HttpStatus.UNPROCESSABLE_CONTENT;
            case "ALREADY_LINKED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Map.of("error", e.getUserMessage()));
    }
}
