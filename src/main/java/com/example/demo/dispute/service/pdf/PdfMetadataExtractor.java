package com.example.demo.dispute.service.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.springframework.stereotype.Component;

@Component
public class PdfMetadataExtractor {

    private static final Pattern PAGE_PATTERN = Pattern.compile("/Type\\s*/Page\\b");
    private static final Pattern EXTERNAL_LINK_PATTERN = Pattern.compile("/URI\\s*\\(");
    private static final Pattern PORTFOLIO_PATTERN = Pattern.compile("/Collection\\b");
    private final PdfAComplianceService pdfAComplianceService;

    public PdfMetadataExtractor(PdfAComplianceService pdfAComplianceService) {
        this.pdfAComplianceService = pdfAComplianceService;
    }

    public PdfMetadata extract(Path path) {
        try {
            return extractWithPdfBox(path);
        } catch (RuntimeException ignored) {
            // Fall back to regex-based extraction for malformed/minimal PDFs.
        }

        return extractWithRegex(path);
    }

    private PdfMetadata extractWithPdfBox(Path path) {
        try {
            try (PDDocument document = Loader.loadPDF(path.toFile())) {
                int pageCount = Math.max(document.getNumberOfPages(), 1);
                boolean externalLinkDetected = hasExternalLink(document) || hasExternalLinkRaw(path);
                boolean pdfACompliant = pdfAComplianceService.isPdfACompliant(path);
                boolean pdfPortfolio = isPortfolio(document);
                return new PdfMetadata(pageCount, externalLinkDetected, pdfACompliant, pdfPortfolio);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read pdf metadata: " + e.getMessage(), e);
        }
    }

    private PdfMetadata extractWithRegex(Path path) {
        try {
            byte[] raw = Files.readAllBytes(path);
            String content = new String(raw, StandardCharsets.ISO_8859_1);

            int pageCount = countPages(content);
            boolean externalLinkDetected = EXTERNAL_LINK_PATTERN.matcher(content).find();
            boolean pdfACompliant = pdfAComplianceService.isPdfACompliant(path);
            boolean pdfPortfolio = PORTFOLIO_PATTERN.matcher(content).find();
            return new PdfMetadata(pageCount, externalLinkDetected, pdfACompliant, pdfPortfolio);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read pdf metadata: " + e.getMessage(), e);
        }
    }

    private int countPages(String content) {
        int count = 0;
        Matcher matcher = PAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            count++;
        }
        return Math.max(count, 1);
    }

    private boolean hasExternalLink(PDDocument document) throws IOException {
        for (PDPage page : document.getPages()) {
            for (PDAnnotation annotation : page.getAnnotations()) {
                if (annotation.getCOSObject().toString().contains("/URI")) {
                    return true;
                }
            }
        }

        return document.getDocumentCatalog() != null
                && document.getDocumentCatalog().getCOSObject().toString().contains("/URI");
    }

    private boolean hasExternalLinkRaw(Path path) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        return EXTERNAL_LINK_PATTERN.matcher(content).find();
    }

    private boolean isPortfolio(PDDocument document) {
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        return catalog != null && catalog.getCOSObject().containsKey(COSName.COLLECTION);
    }
}
