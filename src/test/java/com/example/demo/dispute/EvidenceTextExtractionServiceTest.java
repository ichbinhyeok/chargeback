package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.service.EvidenceTextExtractionService;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class EvidenceTextExtractionServiceTest {

    @Test
    void forcedNoneProviderDisablesImageOcr() {
        EvidenceTextExtractionService service = new EvidenceTextExtractionService("none", "tesseract");
        assertFalse(service.shouldAttemptImageOcr(800, 800));
    }

    @Test
    void forcedTesseractProviderCanRunWithoutWindowsRuntime() throws Exception {
        EvidenceTextExtractionService service = new EvidenceTextExtractionService("tesseract", "mock-tesseract") {
            @Override
            protected boolean isTesseractAvailable() {
                return true;
            }

            @Override
            protected String runTesseractOcr(Path path, int maxChars) {
                return "ocr preview text";
            }
        };

        Path image = Files.createTempFile("chargeback-ocr-test-", ".png");
        try {
            BufferedImage bufferedImage = new BufferedImage(640, 960, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(bufferedImage, "png", image.toFile());

            assertTrue(service.shouldAttemptImageOcr(640, 960));
            assertEquals("ocr preview text", service.extractImageOcrText(image, 200));
        } finally {
            Files.deleteIfExists(image);
        }
    }
}
