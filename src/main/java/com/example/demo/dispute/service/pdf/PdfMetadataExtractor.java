package com.example.demo.dispute.service.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PdfMetadataExtractor {

    private static final Pattern PAGE_PATTERN = Pattern.compile("/Type\\s*/Page\\b");
    private static final Pattern EXTERNAL_LINK_PATTERN = Pattern.compile("/URI\\s*\\(|https?://");
    private static final Pattern PDFA_PATTERN = Pattern.compile("pdfaid:part", Pattern.CASE_INSENSITIVE);
    private static final Pattern PORTFOLIO_PATTERN = Pattern.compile("/Collection\\b");

    public PdfMetadata extract(Path path) {
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(path);
            String content = new String(raw, StandardCharsets.ISO_8859_1);

            int pageCount = countPages(content);
            boolean externalLinkDetected = EXTERNAL_LINK_PATTERN.matcher(content).find();
            boolean pdfACompliant = PDFA_PATTERN.matcher(content).find();
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
}
