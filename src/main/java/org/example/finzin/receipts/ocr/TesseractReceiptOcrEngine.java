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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import javax.imageio.ImageIO;

@Component
public class TesseractReceiptOcrEngine implements ReceiptOcrEngine {
    private static final Logger log = LoggerFactory.getLogger(TesseractReceiptOcrEngine.class);

    /** Screenshots/digital receipts under this width OCR very poorly with Tesseract — upscale them first. */
    private static final int MIN_TARGET_WIDTH_PX = 1800;
    private static final double MAX_UPSCALE_FACTOR = 4.0;

    /** The real eng.traineddata (tessdata_fast) is ~4MB — anything much smaller means a previous
     *  download was interrupted partway through, so treat it as absent and retry. */
    private static final long MIN_VALID_TRAINEDDATA_BYTES = 1_000_000L;
    private static final String TESSDATA_FAST_BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/";

    private final String datapath;
    private final String language;

    /** Set once a LinkageError proves the native library can't load on this machine — the JVM
     *  permanently poisons that class after the first failed static init, so every later attempt
     *  would fail again anyway; this lets isAvailable() short-circuit instead of retrying. */
    private volatile boolean nativeLoadFailed = false;

    public TesseractReceiptOcrEngine(@Value("${tesseract.datapath:}") String datapath,
                                      @Value("${tesseract.language:eng}") String language) {
        this.datapath = datapath;
        this.language = language;
    }

    @PostConstruct
    void checkConfigured() {
        if (!isAvailable()) {
            // build.gradle's downloadTessdata task fetches this at build time, but on hosts that
            // build and run in separate stages/containers (e.g. Railway's Railpack builder) a
            // file written into the project directory during the build never reaches the runtime
            // filesystem. Retry the same fetch here so it also works when that assumption breaks.
            ensureTraineddata();
        }
        if (!isAvailable()) {
            log.warn("Tesseract OCR is not configured (tesseract.datapath='{}', language='{}') — " +
                    "Receipt Scanner will return 503 until a native Tesseract install + tessdata is available.", datapath, language);
        }
    }

    private void ensureTraineddata() {
        if (datapath == null || datapath.isBlank()) return;
        Path dir = Path.of(datapath);
        Path dest = dir.resolve(language + ".traineddata");
        Path tmp = dir.resolve(language + ".traineddata.tmp");
        try {
            Files.createDirectories(dir);
            URLConnection connection = new URL(TESSDATA_FAST_BASE_URL + language + ".traineddata").openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(tmp) < MIN_VALID_TRAINEDDATA_BYTES) {
                throw new IOException("download looked incomplete (" + Files.size(tmp) + " bytes)");
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded Tesseract {}.traineddata into '{}' at startup", language, datapath);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            log.warn("Could not auto-download {}.traineddata into '{}': {}", language, datapath, e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        if (nativeLoadFailed) return false;
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
        } catch (LinkageError e) {
            // UnsatisfiedLinkError (JNA couldn't find the native lib) and NoClassDefFoundError
            // (JNA found it, but a *later* static initializer that needs it, e.g. TessAPI, blew up
            // and the JVM now refuses to re-initialize that class) are siblings under LinkageError,
            // not one a subtype of the other — both mean the same thing here: no usable native
            // Tesseract on this machine. Convert either into the same clean "not configured" error
            // instead of letting it surface as an unhandled 500.
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
