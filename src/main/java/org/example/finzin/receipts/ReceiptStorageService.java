package org.example.finzin.receipts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * File save/replace/delete + type/size validation for receipt images. Mirrors
 * {@code AuthController}'s profile-picture handling, but writes under a dedicated directory that
 * is never registered as a public static resource — see the Security note in the receipt-scanner
 * implementation plan for why receipts are not stored under {@code user-uploads/}.
 */
@Service
public class ReceiptStorageService {
    private static final List<String> ALLOWED_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final String uploadDir;

    public ReceiptStorageService(@Value("${app.receipts.upload.dir:secure-uploads/receipts}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public record ValidationError(String message) {}

    public ValidationError validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ValidationError("No file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return new ValidationError("Unsupported file type. Use JPG, PNG, or WebP.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            return new ValidationError("File too large. Maximum size is 10 MB.");
        }
        return null;
    }

    public record StoredFile(String filename, String mimeType, long sizeBytes) {}

    public StoredFile save(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        String ext = "jpg";
        if ("image/png".equals(contentType)) ext = "png";
        else if ("image/webp".equals(contentType)) ext = "webp";

        String filename = UUID.randomUUID() + "." + ext;
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        Path dest = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        return new StoredFile(filename, contentType, file.getSize());
    }

    public Path resolve(String filename) {
        return Paths.get(uploadDir).resolve(filename);
    }

    public void deleteBestEffort(String filename) {
        if (filename == null) return;
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (Exception ignored) {
        }
    }
}
