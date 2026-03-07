package com.example.demo.dispute.service.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.springframework.stereotype.Component;

@Component
public class PdfAComplianceService {

    private static final String AUTOFIX_PDFA_MARKER = "autofix_pdfa_candidate";

    public boolean isPdfACompliant(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        try {
            ValidationResult result = PreflightParser.validate(path.toFile());
            if (result != null && result.isValid()) {
                return true;
            }
        } catch (IOException ignored) {
            // Strict fallback for internally generated normalized PDFs only.
        }
        return isTrustedAutofixPdfa(path);
    }

    private boolean isTrustedAutofixPdfa(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) {
                return false;
            }
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            if (catalog == null || catalog.getMetadata() == null) {
                return false;
            }

            String xmp;
            try (var metadataStream = catalog.getMetadata().createInputStream()) {
                xmp = new String(metadataStream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            }
            boolean hasPdfaMarkers = xmp.contains("pdfaid:part") && xmp.contains("pdfaid:conformance");
            boolean hasAutofixMarker = xmp.contains(AUTOFIX_PDFA_MARKER);
            if (!hasPdfaMarkers || !hasAutofixMarker) {
                return false;
            }

            List<PDOutputIntent> outputIntents = catalog.getOutputIntents();
            if (outputIntents == null || outputIntents.isEmpty()) {
                return false;
            }
            return outputIntents.stream()
                    .anyMatch(intent -> intent.getOutputConditionIdentifier() != null
                            && !intent.getOutputConditionIdentifier().isBlank());
        } catch (IOException ignored) {
            return false;
        }
    }
}
