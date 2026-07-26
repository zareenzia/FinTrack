package org.example.finzin.purchaseplanner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Purchase-item photos are low-sensitivity (product photos, not receipts) so this follows the
 * simpler public precedent (AuthController's profile-picture upload) rather than the gated
 * receipts pattern: files just live under {@code user-uploads/purchase-items/}, already served
 * for free by WebConfig's public "/user-uploads/**" static handler — no separate streaming
 * controller/ownership-gated GET needed.
 */
@Service
public class PurchaseItemImageStorageService {

    private static final List<String> ALLOWED_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    @Value("${app.purchase-planner.upload.dir:user-uploads/purchase-items}")
    private String uploadDir;

    public record ValidationError(String message) {}

    public ValidationError validate(MultipartFile file) {
        if (file == null || file.isEmpty()) return new ValidationError("No file provided");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return new ValidationError("Only JPEG, PNG, or WebP images are allowed");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            return new ValidationError("Image must be 5MB or smaller");
        }
        return null;
    }

    public record StoredFile(String filename, String publicUrl) {}

    public StoredFile save(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String filename = UUID.randomUUID() + ext;

        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        Path dest = uploadPath.resolve(filename);
        file.transferTo(dest);

        return new StoredFile(filename, "/user-uploads/purchase-items/" + filename);
    }

    public void deleteBestEffort(String filename) {
        if (filename == null || filename.isBlank()) return;
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(filename));
        } catch (Exception ignored) {
        }
    }
}
