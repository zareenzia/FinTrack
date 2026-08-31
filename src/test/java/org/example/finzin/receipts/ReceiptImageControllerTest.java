package org.example.finzin.receipts;

import org.example.finzin.entity.ReceiptEntity;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link ReceiptImageController}. Streams binary bytes rather than JSON, so happy-path
 * assertions cover status/content-type/disposition headers only, backed by a real temp file on disk
 * (the controller itself calls {@link java.nio.file.Files#isRegularFile} directly, so that check can't
 * be mocked away — only {@link ReceiptStorageService#resolve} is mocked, pointed at a real temp file). */
@WebMvcTest(controllers = ReceiptImageController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class ReceiptImageControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private ReceiptService receiptService;
    @MockitoBean private ReceiptStorageService storageService;

    @TempDir Path tempDir;

    private ReceiptEntity receipt(Long id, String imagePath, String mimeType) {
        ReceiptEntity e = new ReceiptEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setImagePath(imagePath);
        e.setMimeType(mimeType);
        return e;
    }

    @Test
    void getImage_returnsNotFound_whenReceiptNotOwned() throws Exception {
        given(receiptService.findOwned(USER_ID, 1L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/receipts/1/image").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Receipt not found"));
    }

    @Test
    void getImage_returnsNotFound_whenFileMissingOnDisk() throws Exception {
        given(receiptService.findOwned(USER_ID, 1L)).willReturn(Optional.of(receipt(1L, "missing.jpg", "image/jpeg")));
        given(storageService.resolve("missing.jpg")).willReturn(tempDir.resolve("missing.jpg"));

        mockMvc.perform(get("/api/receipts/1/image").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Receipt image not found"));
    }

    @Test
    void getImage_streamsFile_onSuccess() throws Exception {
        Path file = tempDir.resolve("receipt.jpg");
        Files.write(file, "fake-image-bytes".getBytes(StandardCharsets.UTF_8));
        given(receiptService.findOwned(USER_ID, 1L)).willReturn(Optional.of(receipt(1L, "receipt.jpg", "image/jpeg")));
        given(storageService.resolve("receipt.jpg")).willReturn(file);

        mockMvc.perform(get("/api/receipts/1/image").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"receipt-1\""));
    }

    @Test
    void getImage_defaultsToUserOne_whenUnauthenticated() throws Exception {
        given(receiptService.findOwned(1L, 1L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/receipts/1/image"))
                .andExpect(status().isNotFound());
    }
}
