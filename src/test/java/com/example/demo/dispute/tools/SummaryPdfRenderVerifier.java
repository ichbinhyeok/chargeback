package com.example.demo.dispute.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

public final class SummaryPdfRenderVerifier {

    private SummaryPdfRenderVerifier() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        List<Path> pdfPaths = resolvePdfPaths(config.pdfPath());
        if (pdfPaths.isEmpty()) {
            throw new IllegalArgumentException("no PDF files found to verify");
        }

        Files.createDirectories(config.outputDir());
        if (config.baselineDir() != null) {
            Files.createDirectories(config.baselineDir());
        }
        for (Path pdfPath : pdfPaths) {
            verifySinglePdf(pdfPath, config);
        }
    }

    private static List<Path> resolvePdfPaths(Path pdfPath) throws IOException {
        if (pdfPath != null) {
            if (Files.isDirectory(pdfPath)) {
                try (var stream = Files.list(pdfPath)) {
                    return stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                            .sorted(Comparator.naturalOrder())
                            .toList();
                }
            }
            return List.of(pdfPath);
        }

        Path defaultDir = Path.of(".tmp", "beta-manual");
        if (!Files.exists(defaultDir)) {
            return List.of();
        }
        try (var stream = Files.list(defaultDir)) {
            return stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private static void verifySinglePdf(Path pdfPath, Config config) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            PDPage firstPage = document.getPage(0);
            String contentStream = readContentStream(firstPage);

            assertCondition(text.contains("Chargeback Submission Guide"),
                    "missing guide title in " + pdfPath.getFileName());
            assertCondition(contentStream.contains("FREE VERSION"),
                    "missing FREE VERSION marker in " + pdfPath.getFileName());
            assertCondition(contentStream.contains("WATERMARKED"),
                    "missing WATERMARKED marker in " + pdfPath.getFileName());

            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage rendered = renderer.renderImageWithDPI(0, 144, ImageType.RGB);
            assertCondition(rendered.getWidth() > 0 && rendered.getHeight() > 0,
                    "rendered page is empty for " + pdfPath.getFileName());

            Path pngPath = config.outputDir().resolve(stripExtension(pdfPath.getFileName().toString()) + "-page-1.png");
            ImageIO.write(rendered, "png", pngPath.toFile());
            assertCondition(Files.size(pngPath) > 0, "rendered PNG is empty for " + pdfPath.getFileName());

            System.out.println("PDF=" + pdfPath);
            System.out.println("PNG=" + pngPath);
            System.out.println("PAGES=" + document.getNumberOfPages());
            System.out.println("HAS_GUIDE=true");
            System.out.println("HAS_FREE=true");
            System.out.println("HAS_WATERMARKED=true");

            if (config.baselineDir() != null) {
                verifyBaseline(pngPath, config);
            }
        }
    }

    private static void verifyBaseline(Path pngPath, Config config) throws IOException {
        Path baselinePath = config.baselineDir().resolve(pngPath.getFileName().toString());
        if (config.updateBaselines() || !Files.exists(baselinePath)) {
            Files.copy(pngPath, baselinePath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("BASELINE=" + baselinePath);
            System.out.println("BASELINE_UPDATED=true");
            return;
        }

        BufferedImage actual = ImageIO.read(pngPath.toFile());
        BufferedImage baseline = ImageIO.read(baselinePath.toFile());
        assertCondition(actual != null, "failed to read rendered PNG " + pngPath.getFileName());
        assertCondition(baseline != null, "failed to read baseline PNG " + baselinePath.getFileName());
        assertCondition(actual.getWidth() == baseline.getWidth() && actual.getHeight() == baseline.getHeight(),
                "baseline size mismatch for " + pngPath.getFileName());

        int differingPixels = 0;
        BufferedImage diff = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                int actualRgb = actual.getRGB(x, y);
                int baselineRgb = baseline.getRGB(x, y);
                if (actualRgb == baselineRgb) {
                    diff.setRGB(x, y, 0xFFFFFF);
                    continue;
                }
                differingPixels++;
                diff.setRGB(x, y, 0xFF0000);
            }
        }

        if (differingPixels > 0) {
            Path diffPath = config.outputDir().resolve(stripExtension(pngPath.getFileName().toString()) + "-diff.png");
            ImageIO.write(diff, "png", diffPath.toFile());
            throw new IllegalStateException(
                    "summary PDF render changed for " + pngPath.getFileName()
                            + " (differingPixels=" + differingPixels + ", diff=" + diffPath + ")"
            );
        }

        System.out.println("BASELINE=" + baselinePath);
        System.out.println("BASELINE_MATCH=true");
    }

    private static String readContentStream(PDPage page) throws IOException {
        try (InputStream in = page.getContents()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : filename;
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Config(Path pdfPath, Path outputDir, Path baselineDir, boolean updateBaselines) {
        private static Config parse(String[] args) {
            Path pdfPath = null;
            Path outputDir = Path.of("output", "pdf", "render-checks");
            Path baselineDir = null;
            boolean updateBaselines = false;
            List<String> unknown = new ArrayList<>();

            for (String arg : args) {
                if (arg.startsWith("--pdf=")) {
                    pdfPath = Path.of(arg.substring("--pdf=".length()));
                    continue;
                }
                if (arg.startsWith("--outputDir=")) {
                    outputDir = Path.of(arg.substring("--outputDir=".length()));
                    continue;
                }
                if (arg.startsWith("--baselineDir=")) {
                    baselineDir = Path.of(arg.substring("--baselineDir=".length()));
                    continue;
                }
                if ("--updateBaselines".equals(arg)) {
                    updateBaselines = true;
                    continue;
                }
                unknown.add(arg);
            }

            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("unknown arguments: " + unknown);
            }
            return new Config(pdfPath, outputDir, baselineDir, updateBaselines);
        }
    }
}
