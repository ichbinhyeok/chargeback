package com.example.demo.dispute.service;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class DocumentNormalizationService {

    private static final long MB = 1024L * 1024L;
    private static final long STRIPE_MIN_BYTES_PER_PAGE_AFTER_COMPRESSION = 4_000L;
    private static final int STRIPE_MIN_DPI = 115;
    private static final int STRIPE_TEXT_HEAVY_CHAR_THRESHOLD = 800;
    private static final int[] STRIPE_COMPRESSION_DPIS = new int[] {170, 150, 130, 115};
    private static final float[] STRIPE_COMPRESSION_QUALITIES = new float[] {0.9f, 0.82f, 0.74f, 0.65f};
    private static final int[] SHOPIFY_PDFA_DPIS = new int[] {170, 150, 130, 115};
    private static final float[] SHOPIFY_PDFA_QUALITIES = new float[] {0.9f, 0.82f, 0.74f, 0.65f};

    public byte[] compressPdfToSmallerBytes(Path sourcePath, long preferredMaxBytes) {
        int pageCount = readPdfPageCount(sourcePath);
        byte[] bestGuarded = new byte[0];

        for (int dpi : STRIPE_COMPRESSION_DPIS) {
            for (float quality : STRIPE_COMPRESSION_QUALITIES) {
                byte[] candidate = renderPdfAsImagePdf(sourcePath, dpi, quality);
                if (candidate.length == 0) {
                    continue;
                }
                if (!passesStripeCompressionGuard(pageCount, dpi, candidate.length)) {
                    continue;
                }
                if (bestGuarded.length == 0 || candidate.length < bestGuarded.length) {
                    bestGuarded = candidate;
                }
                if (preferredMaxBytes > 0 && candidate.length <= preferredMaxBytes) {
                    return candidate;
                }
            }
        }

        return bestGuarded.length > 0 ? bestGuarded : new byte[0];
    }

    public byte[] normalizePdfToPdfaBytes(Path sourcePath, long preferredMaxBytes, String marker) {
        byte[] best = new byte[0];

        for (int dpi : SHOPIFY_PDFA_DPIS) {
            for (float quality : SHOPIFY_PDFA_QUALITIES) {
                byte[] candidate = renderPdfAsPdfABytes(sourcePath, dpi, quality, marker);
                if (candidate.length == 0) {
                    continue;
                }
                if (best.length == 0 || candidate.length < best.length) {
                    best = candidate;
                }
                if (preferredMaxBytes > 0 && candidate.length <= preferredMaxBytes) {
                    return candidate;
                }
            }
        }

        return best;
    }

    public boolean isTextHeavyPdf(Path sourcePath) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(source);
            if (text == null) {
                return false;
            }
            return text.replaceAll("\\s+", "").length() >= STRIPE_TEXT_HEAVY_CHAR_THRESHOLD;
        } catch (IOException ex) {
            return false;
        }
    }

    public long minimumPdfTargetBytes() {
        return 256L * 1024L;
    }

    public long minimumSuggestedSavingsBytes() {
        return 64L * 1024L;
    }

    public String formatBytesLabel(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        return String.format(java.util.Locale.ROOT, "%.2f MB", bytes / (double) MB);
    }

    private byte[] renderPdfAsImagePdf(Path sourcePath, int dpi, float jpegQuality) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile()); PDDocument compressed = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int index = 0; index < source.getNumberOfPages(); index++) {
                BufferedImage rendered = renderer.renderImageWithDPI(index, dpi, ImageType.RGB);
                appendRenderedPage(compressed, source, index, rendered, dpi, jpegQuality);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            compressed.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private byte[] renderPdfAsPdfABytes(Path sourcePath, int dpi, float jpegQuality, String marker) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile()); PDDocument normalized = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int index = 0; index < source.getNumberOfPages(); index++) {
                BufferedImage rendered = renderer.renderImageWithDPI(index, dpi, ImageType.RGB);
                appendRenderedPage(normalized, source, index, rendered, dpi, jpegQuality);
            }

            applyPdfaProfile(normalized, marker);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            normalized.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private void appendRenderedPage(
            PDDocument target,
            PDDocument source,
            int pageIndex,
            BufferedImage rendered,
            int dpi,
            float jpegQuality
    ) throws IOException {
        PDRectangle sourceBox = source.getPage(pageIndex).getMediaBox();
        float width = sourceBox != null && sourceBox.getWidth() > 0
                ? sourceBox.getWidth()
                : (float) rendered.getWidth() * 72f / dpi;
        float height = sourceBox != null && sourceBox.getHeight() > 0
                ? sourceBox.getHeight()
                : (float) rendered.getHeight() * 72f / dpi;

        PDPage page = new PDPage(new PDRectangle(width, height));
        target.addPage(page);
        PDImageXObject image = JPEGFactory.createFromImage(target, rendered, jpegQuality);
        try (PDPageContentStream content = new PDPageContentStream(target, page)) {
            content.drawImage(image, 0, 0, width, height);
        }
    }

    private void applyPdfaProfile(PDDocument document, String marker) throws IOException {
        String normalizedMarker = marker == null || marker.isBlank()
                ? "normalized"
                : marker.replaceAll("[^a-z0-9_-]", "_");
        String xmp = """
                <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                      xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
                      xmlns:cb="urn:chargeback:normalization"
                      pdfaid:part="1"
                      pdfaid:conformance="B"
                      cb:marker="%s"/>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """.formatted(normalizedMarker);

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
        catalog.setMetadata(metadata);

        ICC_Profile profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        PDOutputIntent outputIntent = new PDOutputIntent(document, new ByteArrayInputStream(profile.getData()));
        outputIntent.setInfo("sRGB IEC61966-2.1");
        outputIntent.setOutputCondition("sRGB IEC61966-2.1");
        outputIntent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
        outputIntent.setRegistryName("https://www.color.org");
        catalog.addOutputIntent(outputIntent);
    }

    private int readPdfPageCount(Path sourcePath) {
        try (PDDocument source = Loader.loadPDF(sourcePath.toFile())) {
            return Math.max(1, source.getNumberOfPages());
        } catch (IOException ex) {
            return 1;
        }
    }

    private boolean passesStripeCompressionGuard(int pageCount, int dpi, long sizeBytes) {
        if (dpi < STRIPE_MIN_DPI) {
            return false;
        }
        long perPageBytes = sizeBytes / Math.max(1, pageCount);
        return perPageBytes >= STRIPE_MIN_BYTES_PER_PAGE_AFTER_COMPRESSION;
    }
}
