package org.example.finzin.receipts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test for ReceiptStorageService's validation and real (but disposable) filesystem
 * I/O. Unlike PurchaseItemImageStorageService, the upload directory here is a constructor
 * parameter (not a plain {@code @Value} field), so a {@code @TempDir} path can be passed straight
 * into {@code new ReceiptStorageService(...)} with no reflection needed.
 */
class ReceiptStorageServiceTest {

    @TempDir
    Path tempDir;

    private ReceiptStorageService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptStorageService(tempDir.toString());
    }

    // ================================================================================
    // validate
    // ================================================================================

    @Test
    void validateRejectsNullOrEmptyFile() {
        assertEquals("No file provided", service.validate(null).message());
        MockMultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        assertEquals("No file provided", service.validate(empty).message());
    }

    @Test
    void validateRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "x.gif", "image/gif", "data".getBytes());
        assertEquals("Unsupported file type. Use JPG, PNG, or WebP.", service.validate(file).message());
    }

    @Test
    void validateRejectsNullContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "x", null, "data".getBytes());
        assertEquals("Unsupported file type. Use JPG, PNG, or WebP.", service.validate(file).message());
    }

    @Test
    void validateRejectsFileOverTenMegabytes() {
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", tooBig);
        assertEquals("File too large. Maximum size is 10 MB.", service.validate(file).message());
    }

    @Test
    void validateAcceptsWellFormedJpegPngAndWebp() {
        assertNull(service.validate(new MockMultipartFile("file", "a.jpg", "image/jpeg", "d".getBytes())));
        assertNull(service.validate(new MockMultipartFile("file", "b.png", "image/png", "d".getBytes())));
        assertNull(service.validate(new MockMultipartFile("file", "c.webp", "image/webp", "d".getBytes())));
    }

    // ================================================================================
    // save
    // ================================================================================

    @Test
    void saveDerivesExtensionFromContentTypeNotOriginalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "whatever.txt", "image/png", "hello".getBytes());

        ReceiptStorageService.StoredFile stored = service.save(file);

        assertTrue(stored.filename().endsWith(".png"), "extension must come from contentType, not the original filename");
        assertEquals("image/png", stored.mimeType());
        assertEquals(5L, stored.sizeBytes());
        assertTrue(Files.exists(tempDir.resolve(stored.filename())));
    }

    @Test
    void saveDefaultsToJpgExtensionForJpegContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpeg", "image/jpeg", "x".getBytes());

        ReceiptStorageService.StoredFile stored = service.save(file);

        assertTrue(stored.filename().endsWith(".jpg"));
    }

    @Test
    void saveUsesWebpExtensionForWebpContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo", "image/webp", "x".getBytes());

        ReceiptStorageService.StoredFile stored = service.save(file);

        assertTrue(stored.filename().endsWith(".webp"));
    }

    @Test
    void saveCreatesUploadDirectoryWhenMissing() throws Exception {
        Path nested = tempDir.resolve("nested/sub/dir");
        ReceiptStorageService nestedService = new ReceiptStorageService(nested.toString());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        ReceiptStorageService.StoredFile stored = nestedService.save(file);

        assertTrue(Files.exists(nested.resolve(stored.filename())));
    }

    @Test
    void saveGeneratesDistinctFilenamesForConsecutiveUploads() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        ReceiptStorageService.StoredFile first = service.save(file);
        ReceiptStorageService.StoredFile second = service.save(file);

        assertFalse(first.filename().equals(second.filename()), "each upload must get its own UUID-based filename");
        assertTrue(Files.exists(tempDir.resolve(first.filename())));
        assertTrue(Files.exists(tempDir.resolve(second.filename())));
    }

    // ================================================================================
    // resolve / deleteBestEffort
    // ================================================================================

    @Test
    void resolveJoinsUploadDirAndFilename() {
        assertEquals(tempDir.resolve("abc.png"), service.resolve("abc.png"));
    }

    @Test
    void deleteBestEffortRemovesExistingFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());
        ReceiptStorageService.StoredFile stored = service.save(file);
        assertTrue(Files.exists(service.resolve(stored.filename())));

        service.deleteBestEffort(stored.filename());

        assertFalse(Files.exists(service.resolve(stored.filename())));
    }

    @Test
    void deleteBestEffortIsNoOpForNullOrMissingFilename() {
        service.deleteBestEffort(null);
        service.deleteBestEffort("does-not-exist.png");
        // No exception thrown is the assertion — deleteBestEffort swallows all failures.
    }
}
