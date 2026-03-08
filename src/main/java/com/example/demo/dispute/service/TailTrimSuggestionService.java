package com.example.demo.dispute.service;

import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TailTrimSuggestionService {

    private static final List<String> SIGNAL_KEYWORDS = List.of(
            "event log",
            "tracking",
            "export",
            "carrier",
            "appendix",
            "history",
            "scan",
            "terms",
            "policy",
            "footer",
            "privacy",
            "settings"
    );

    private final EvidenceFileRepository evidenceFileRepository;

    public TailTrimSuggestionService(EvidenceFileRepository evidenceFileRepository) {
        this.evidenceFileRepository = evidenceFileRepository;
    }

    public Optional<TailTrimSuggestion> suggest(UUID caseId, UUID fileId, int overflowPages) {
        if (caseId == null || fileId == null || overflowPages <= 0) {
            return Optional.empty();
        }

        return evidenceFileRepository.findByIdAndDisputeCaseId(fileId, caseId)
                .filter(file -> file.getFileFormat() == FileFormat.PDF)
                .filter(file -> file.getPageCount() > overflowPages)
                .flatMap(file -> inspectTailRange(file, overflowPages));
    }

    private Optional<TailTrimSuggestion> inspectTailRange(EvidenceFileEntity file, int overflowPages) {
        Path sourcePath = Path.of(file.getStoragePath());
        try (PDDocument document = Loader.loadPDF(sourcePath.toFile())) {
            int totalPages = Math.min(file.getPageCount(), document.getNumberOfPages());
            if (totalPages <= overflowPages) {
                return Optional.empty();
            }

            int startPage = totalPages - overflowPages + 1;
            int endPage = totalPages;
            PDFRenderer renderer = new PDFRenderer(document);
            List<PageSignal> pageSignals = new ArrayList<>();
            for (int pageNumber = startPage; pageNumber <= endPage; pageNumber++) {
                pageSignals.add(analyzePage(document, renderer, pageNumber));
            }

            int blankPages = (int) pageSignals.stream().filter(PageSignal::blank).count();
            List<String> similarityKeys = pageSignals.stream()
                    .map(PageSignal::similarityKey)
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
            long distinctPatterns = similarityKeys.stream().distinct().count();
            boolean repeatedPattern = similarityKeys.size() >= 2 && distinctPatterns <= Math.max(1L, similarityKeys.size() / 2L);
            LinkedHashSet<String> matchedKeywords = collectKeywords(pageSignals);
            boolean keywordHeavy = matchedKeywords.size() >= 2;

            if (!repeatedPattern && blankPages == 0 && !keywordHeavy) {
                return Optional.empty();
            }

            String reason = buildReason(blankPages, pageSignals.size(), repeatedPattern, keywordHeavy);
            String signalSummary = buildSignalSummary(blankPages, pageSignals.size(), matchedKeywords);
            return Optional.of(new TailTrimSuggestion(
                    "Optional tail-page candidates",
                    file.getId(),
                    startPage,
                    endPage,
                    defaultFileLabel(file.getOriginalName()),
                    pageRangeLabel(startPage, endPage),
                    reason,
                    signalSummary,
                    "Not auto-trimmed. Review these pages visually before deleting them."
            ));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private PageSignal analyzePage(PDDocument document, PDFRenderer renderer, int pageNumber) throws IOException {
        String extractedText = extractSinglePageText(document, pageNumber);
        String previewText = normalizePreviewText(extractedText);
        boolean blank = previewText.isBlank() && isVisuallyBlank(renderer.renderImageWithDPI(pageNumber - 1, 72, ImageType.GRAY));
        String similarityKey = normalizeSimilarityText(previewText);
        return new PageSignal(blank, previewText, similarityKey);
    }

    private String extractSinglePageText(PDDocument document, int pageNumber) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        String text = stripper.getText(document);
        return text == null ? "" : text;
    }

    private String normalizePreviewText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180).trim();
    }

    private String normalizeSimilarityText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\d+", "#")
                .replaceAll("[^a-z# ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private LinkedHashSet<String> collectKeywords(List<PageSignal> pageSignals) {
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        String combined = pageSignals.stream()
                .map(PageSignal::previewText)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + " " + right);
        for (String keyword : SIGNAL_KEYWORDS) {
            if (combined.contains(keyword)) {
                matches.add(keyword);
            }
            if (matches.size() >= 4) {
                break;
            }
        }
        return matches;
    }

    private String buildReason(int blankPages, int totalPages, boolean repeatedPattern, boolean keywordHeavy) {
        if (blankPages == totalPages) {
            return "Every proposed tail page looks blank or near-blank, so this range is a strong manual-trim candidate.";
        }
        if (repeatedPattern && keywordHeavy) {
            return "These proposed tail pages repeat the same text pattern after page numbers are ignored and also look like export/log tail pages.";
        }
        if (repeatedPattern) {
            return "These proposed tail pages repeat the same text pattern after page numbers are ignored.";
        }
        return "These proposed tail pages carry repeated export/log language, so they are worth reviewing before you trim.";
    }

    private String buildSignalSummary(int blankPages, int totalPages, Set<String> matchedKeywords) {
        List<String> parts = new ArrayList<>();
        if (blankPages > 0) {
            parts.add(blankPages + " of " + totalPages + " page(s) look blank");
        }
        if (!matchedKeywords.isEmpty()) {
            parts.add("Signals: " + String.join(", ", matchedKeywords));
        }
        if (parts.isEmpty()) {
            return "Repeated tail-page pattern detected in the proposed trim range.";
        }
        return String.join(" | ", parts);
    }

    private boolean isVisuallyBlank(BufferedImage image) {
        int nonWhiteSamples = 0;
        int totalSamples = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int gray = image.getRGB(x, y) & 0xFF;
                totalSamples++;
                if (gray < 245) {
                    nonWhiteSamples++;
                }
            }
        }
        return nonWhiteSamples <= Math.max(12, totalSamples / 500);
    }

    private String pageRangeLabel(int startPage, int endPage) {
        if (startPage >= endPage) {
            return "page " + startPage;
        }
        return "pages " + startPage + "-" + endPage;
    }

    private String defaultFileLabel(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "selected PDF";
        }
        return originalName;
    }

    private record PageSignal(
            boolean blank,
            String previewText,
            String similarityKey
    ) {
    }
}
