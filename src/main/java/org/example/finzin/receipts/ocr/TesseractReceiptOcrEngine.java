package org.example.finzin.receipts.ocr;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.example.finzin.receipts.ReceiptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;

@Component
public class TesseractReceiptOcrEngine implements ReceiptOcrEngine {
    private static final Logger log = LoggerFactory.getLogger(TesseractReceiptOcrEngine.class);

    /** Screenshots/digital receipts under this width OCR very poorly with Tesseract — upscale them first. */
    private static final int MIN_TARGET_WIDTH_PX = 1800;
    private static final double MAX_UPSCALE_FACTOR = 4.0;

    private final String datapath;
    private final String language;

    public TesseractReceiptOcrEngine(@Value("${tesseract.datapath:}") String datapath,
                                      @Value("${tesseract.language:eng}") String language) {
        this.datapath = datapath;
        this.language = language;
    }

    @PostConstruct
    void checkConfigured() {
        if (!isAvailable()) {
            log.warn("Tesseract OCR is not configured (tesseract.datapath='{}', language='{}') — " +
                    "Receipt Scanner will return 503 until a native Tesseract install + tessdata is available.", datapath, language);
        }
    }

    @Override
    public boolean isAvailable() {
        if (datapath == null || datapath.isBlank()) return false;
        Path traineddata = Path.of(datapath, language + ".traineddata");
        return Files.isRegularFile(traineddata);
    }

    @Override
    public OcrResult extractText(Path imageFile, String mimeType) {
        // A fresh instance per call — Tesseract's native handle is not safe to share across concurrent requests.
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(datapath);
        tesseract.setLanguage(language);
        try {
            String text = preprocess(imageFile)
                    .map(image -> doOcr(tesseract, image))
                    .orElseGet(() -> doOcr(tesseract, imageFile));
            return new OcrResult(text == null ? "" : text.trim());
        } catch (UnsatisfiedLinkError e) {
            log.error("Tesseract native library could not be loaded — is it installed on this machine?", e);
            throw ReceiptException.ocrNotConfigured();
        }
    }

    private String doOcr(Tesseract tesseract, BufferedImage image) {
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed on preprocessed image: {}", e.getMessage());
            throw ReceiptException.ocrFailed();
        }
    }

    private String doOcr(Tesseract tesseract, Path imageFile) {
        try {
            return tesseract.doOCR(imageFile.toFile());
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed for {}: {}", imageFile, e.getMessage());
            throw ReceiptException.ocrFailed();
        }
    }

    /**
     * Upscales small screenshots/digital receipts and converts to grayscale before handing the
     * image to Tesseract — without this, low-resolution input (common for phone screenshots of
     * receipts/emails) produces near-gibberish character-level misreads even on otherwise clean,
     * high-contrast text. Returns empty when the format can't be decoded by ImageIO (e.g. some
     * WEBP variants aren't supported by the JDK's built-in readers) so the caller falls back to
     * handing Tesseract the raw file directly, rather than failing the whole scan.
     */
    private Optional<BufferedImage> preprocess(Path imageFile) {
        try {
            BufferedImage original = ImageIO.read(imageFile.toFile());
            if (original == null) {
                return Optional.empty();
            }

            double scale = 1.0;
            if (original.getWidth() < MIN_TARGET_WIDTH_PX) {
                scale = Math.min(MAX_UPSCALE_FACTOR, (double) MIN_TARGET_WIDTH_PX / original.getWidth());
            }
            int targetWidth = Math.max(1, Math.round((float) (original.getWidth() * scale)));
            int targetHeight = Math.max(1, Math.round((float) (original.getHeight() * scale)));

            BufferedImage gray = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = gray.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            return Optional.of(gray);
        } catch (IOException | RuntimeException e) {
            log.warn("Image preprocessing failed for {}, falling back to raw file OCR: {}", imageFile, e.getMessage());
            return Optional.empty();
        }
    }
}
