package org.example.finzin.purchaseplanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test for PurchaseItemImageStorageService's validation and real (but disposable)
 * filesystem I/O. The service's upload directory is {@code @Value}-injected onto a plain field
 * rather than a constructor parameter, so this test constructs the service directly and points
 * that field at a JUnit {@code @TempDir} via reflection rather than mocking the filesystem.
 */
class PurchaseItemImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private PurchaseItemImageStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PurchaseItemImageStorageService();
        Field field = PurchaseItemImageStorageService.class.getDeclaredField("uploadDir");
        field.setAccessible(true);
        field.set(service, tempDir.toString());
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
        assertEquals("Only JPEG, PNG, or WebP images are allowed", service.validate(file).message());
    }

    @Test
    void validateRejectsNullContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "x", null, "data".getBytes());
        assertEquals("Only JPEG, PNG, or WebP images are allowed", service.validate(file).message());
    }

    @Test
    void validateRejectsFileOverFiveMegabytes() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", tooBig);
        assertEquals("Image must be 5MB or smaller", service.validate(file).message());
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
    void saveWritesFileUnderUploadDirWithGeneratedUuidNameAndPreservedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "product-photo.png", "image/png", "hello".getBytes());

        PurchaseItemImageStorageService.StoredFile stored = service.save(file);

        assertTrue(stored.filename().endsWith(".png"));
        assertEquals("/user-uploads/purchase-items/" + stored.filename(), stored.publicUrl());
        Path written = tempDir.resolve(stored.filename());
        assertTrue(Files.exists(written));
        assertEquals("hello", Files.readString(written));
    }

    @Test
    void saveHandlesOriginalFilenameWithNoExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "noext", "image/jpeg", "data".getBytes());

        PurchaseItemImageStorageService.StoredFile stored = service.save(file);

        assertFalse(stored.filename().contains("."), "no original extension means no extension on the stored filename either");
        assertTrue(Files.exists(tempDir.resolve(stored.filename())));
    }

    @Test
    void saveCreatesUploadDirectoryWhenMissing() throws Exception {
        Path nested = tempDir.resolve("nested/sub/dir");
        Field field = PurchaseItemImageStorageService.class.getDeclaredField("uploadDir");
        field.setAccessible(true);
        field.set(service, nested.toString());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        PurchaseItemImageStorageService.StoredFile stored = service.save(file);

        assertTrue(Files.exists(nested.resolve(stored.filename())));
    }

    // ================================================================================
    // deleteBestEffort
    // ================================================================================

    @Test
    void deleteBestEffortRemovesExistingFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());
        PurchaseItemImageStorageService.StoredFile stored = service.save(file);
        assertTrue(Files.exists(tempDir.resolve(stored.filename())));

        service.deleteBestEffort(stored.filename());

        assertFalse(Files.exists(tempDir.resolve(stored.filename())));
    }

    @Test
    void deleteBestEffortIsNoOpForNullBlankOrMissingFilename() {
        service.deleteBestEffort(null);
        service.deleteBestEffort("  ");
        service.deleteBestEffort("does-not-exist.png");
        // No exception thrown is the assertion — deleteBestEffort swallows all failures.
    }
}
