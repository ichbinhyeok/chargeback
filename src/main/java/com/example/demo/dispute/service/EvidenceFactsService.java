package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EvidenceFactsService {

    private static final String OPTIONAL_REFERENCE_SUFFIX = "(?:\\s*(?:id|number|no\\.?|#|ref(?:erence)?|code))?";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UPS_TRACKING_PATTERN = Pattern.compile("\\b1Z[0-9A-Z]{16}\\b");
    private static final Pattern USPS_TRACKING_PATTERN = Pattern.compile("\\b(?:94|93|92|95)\\d{20,24}\\b");
    private static final Pattern DHL_TRACKING_PATTERN = Pattern.compile("\\bJD\\d{16,20}\\b");
    private static final Pattern ALPHA_NUMERIC_WITH_OPTIONAL_SEPARATOR = Pattern.compile("^([A-Z]{1,6})[- ]?(\\d{2,})$");
    private static final int PDF_PREVIEW_PAGES = 2;
    private static final int PDF_PREVIEW_CHARS = 1800;
    private static final int OCR_PREVIEW_CHARS = 900;
    private static final EnumSet<EvidenceType> CORE_EVIDENCE_TYPES = EnumSet.of(
            EvidenceType.ORDER_RECEIPT,
            EvidenceType.CUSTOMER_DETAILS,
            EvidenceType.CUSTOMER_COMMUNICATION,
            EvidenceType.FULFILLMENT_DELIVERY,
            EvidenceType.REFUND_CANCELLATION
    );

    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceTextExtractionService evidenceTextExtractionService;
    private final EvidenceAliasCatalogService evidenceAliasCatalogService;
    private final Pattern orderPattern;
    private final Pattern orderSpacedPattern;
    private final Pattern transactionPattern;
    private final Pattern transactionSpacedPattern;
    private final Pattern trackingPattern;
    private final Pattern trackingSpacedPattern;
    private final Pattern refundPattern;
    private final Pattern refundSpacedPattern;

    public EvidenceFactsService(
            EvidenceFileRepository evidenceFileRepository,
            EvidenceTextExtractionService evidenceTextExtractionService,
            EvidenceAliasCatalogService evidenceAliasCatalogService
    ) {
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceTextExtractionService = evidenceTextExtractionService;
        this.evidenceAliasCatalogService = evidenceAliasCatalogService;
        this.orderPattern = patternWithLabels(evidenceAliasCatalogService.orderLabelPattern(), 2);
        this.orderSpacedPattern = spacedPatternWithLabels(evidenceAliasCatalogService.orderLabelPattern());
        this.transactionPattern = patternWithLabels(evidenceAliasCatalogService.transactionLabelPattern(), 3);
        this.transactionSpacedPattern = spacedPatternWithLabels(evidenceAliasCatalogService.transactionLabelPattern());
        this.trackingPattern = Pattern.compile(
                evidenceAliasCatalogService.trackingLabelPattern()
                        + OPTIONAL_REFERENCE_SUFFIX
                        + "\\s*[:#-]?\\s*([A-Z0-9-]{6,26})",
                Pattern.CASE_INSENSITIVE
        );
        this.trackingSpacedPattern = Pattern.compile(
                evidenceAliasCatalogService.trackingLabelPattern()
                        + OPTIONAL_REFERENCE_SUFFIX
                        + "\\s*[:#-]?\\s*([A-Z0-9]*\\d[A-Z0-9]*(?:[\\s-]+[A-Z0-9]*\\d[A-Z0-9]*){1,6})",
                Pattern.CASE_INSENSITIVE
        );
        this.refundPattern = patternWithLabels(evidenceAliasCatalogService.refundLabelPattern(), 2);
        this.refundSpacedPattern = spacedPatternWithLabels(evidenceAliasCatalogService.refundLabelPattern());
    }

    public CaseEvidenceFacts analyze(UUID caseId, List<EvidenceFileReportResponse> files) {
        if (files == null || files.isEmpty()) {
            return new CaseEvidenceFacts(List.of(), List.of(), List.of(), List.of(), 0);
        }

        Map<UUID, EvidenceFileEntity> storedFiles = loadStoredFiles(caseId);
        List<FileEvidenceFacts> fileFacts = files.stream()
                .map(file -> analyzeFile(file, storedFiles.get(file.fileId())))
                .toList();

        List<AnchorCoverage> sharedAnchors = sharedAnchors(fileFacts);
        List<String> coherenceHighlights = buildCoherenceHighlights(fileFacts, sharedAnchors);
        List<String> narrativeSpine = buildNarrativeSpine(fileFacts, sharedAnchors);
        int coherenceScore = computeCoherenceScore(fileFacts, sharedAnchors);

        return new CaseEvidenceFacts(
                fileFacts,
                sharedAnchors,
                coherenceHighlights,
                narrativeSpine,
                coherenceScore
        );
    }

    private Map<UUID, EvidenceFileEntity> loadStoredFiles(UUID caseId) {
        if (caseId == null || evidenceFileRepository == null) {
            return Map.of();
        }
        return evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .collect(Collectors.toMap(EvidenceFileEntity::getId, entity -> entity));
    }

    private FileEvidenceFacts analyzeFile(
            EvidenceFileReportResponse file,
            EvidenceFileEntity storedFile
    ) {
        StringBuilder signalText = new StringBuilder();
        signalText.append(defaultString(file.originalName()));
        String previewText = extractPreviewText(storedFile, file.fileFormat());
        if (!previewText.isBlank()) {
            signalText.append('\n').append(previewText);
        }

        String normalizedSignalText = normalizeSignalText(signalText.toString());
        List<String> orderRefs = extractValues("order", normalizedSignalText, orderPattern, orderSpacedPattern);
        List<String> transactionRefs = extractValues(
                "transaction",
                normalizedSignalText,
                transactionPattern,
                transactionSpacedPattern
        );
        List<String> trackingRefs = extractTrackingValues(normalizedSignalText);
        List<String> refundRefs = extractValues("refund", normalizedSignalText, refundPattern, refundSpacedPattern);
        List<String> customerEmails = extractValues("email", normalizedSignalText, EMAIL_PATTERN);
        boolean hasAnyAnchor = !orderRefs.isEmpty()
                || !transactionRefs.isEmpty()
                || !trackingRefs.isEmpty()
                || !refundRefs.isEmpty()
                || !customerEmails.isEmpty();

        return new FileEvidenceFacts(
                file.fileId(),
                file.evidenceType(),
                defaultString(file.originalName()),
                file.fileFormat(),
                orderRefs,
                transactionRefs,
                trackingRefs,
                refundRefs,
                customerEmails,
                hasAnyAnchor
        );
    }

    private String extractPreviewText(EvidenceFileEntity storedFile, FileFormat format) {
        if (storedFile == null) {
            return "";
        }
        Path path = Path.of(storedFile.getStoragePath());
        if (!Files.exists(path)) {
            return "";
        }
        if (format == FileFormat.PDF) {
            return defaultString(evidenceTextExtractionService.extractPdfPreview(path, PDF_PREVIEW_PAGES, PDF_PREVIEW_CHARS));
        }
        if (format == FileFormat.JPEG || format == FileFormat.PNG) {
            return defaultString(evidenceTextExtractionService.extractImageOcrText(path, OCR_PREVIEW_CHARS));
        }
        return "";
    }

    private List<AnchorCoverage> sharedAnchors(List<FileEvidenceFacts> fileFacts) {
        Map<String, AnchorAccumulator> accumulators = new LinkedHashMap<>();
        for (FileEvidenceFacts fileFact : fileFacts) {
            registerAnchors(accumulators, "order", fileFact.orderRefs(), fileFact);
            registerAnchors(accumulators, "transaction", fileFact.transactionRefs(), fileFact);
            registerAnchors(accumulators, "tracking", fileFact.trackingRefs(), fileFact);
            registerAnchors(accumulators, "refund", fileFact.refundRefs(), fileFact);
            registerAnchors(accumulators, "email", fileFact.customerEmails(), fileFact);
        }
        return accumulators.values().stream()
                .filter(acc -> acc.evidenceTypes().size() >= 2)
                .sorted(Comparator
                        .comparingInt((AnchorAccumulator acc) -> acc.evidenceTypes().size()).reversed()
                        .thenComparing(AnchorAccumulator::kind)
                        .thenComparing(AnchorAccumulator::value))
                .map(acc -> new AnchorCoverage(
                        acc.kind(),
                        acc.value(),
                        List.copyOf(acc.evidenceTypes())
                ))
                .toList();
    }

    private void registerAnchors(
            Map<String, AnchorAccumulator> accumulators,
            String kind,
            List<String> values,
            FileEvidenceFacts fileFact
    ) {
        for (String value : values) {
            String key = kind + ":" + value;
            AnchorAccumulator accumulator = accumulators.computeIfAbsent(
                    key,
                    ignored -> new AnchorAccumulator(kind, value)
            );
            accumulator.evidenceTypes().add(fileFact.evidenceType());
        }
    }

    private List<String> buildCoherenceHighlights(
            List<FileEvidenceFacts> fileFacts,
            List<AnchorCoverage> sharedAnchors
    ) {
        List<String> highlights = new ArrayList<>();
        if (!sharedAnchors.isEmpty()) {
            sharedAnchors.stream()
                    .limit(3)
                    .forEach(anchor -> highlights.add(formatSharedAnchor(anchor)));
        } else {
            highlights.add("No repeated order, tracking, refund, or customer-email anchor was found across the current files.");
        }

        Set<String> distinctCommercialRefs = new LinkedHashSet<>();
        fileFacts.forEach(file -> {
            distinctCommercialRefs.addAll(file.orderRefs());
            distinctCommercialRefs.addAll(file.transactionRefs());
        });
        if (distinctCommercialRefs.size() > 1 && sharedAnchors.stream().noneMatch(this::isCommercialAnchor)) {
            highlights.add("Multiple order or transaction references appear with little overlap: "
                    + String.join(", ", distinctCommercialRefs.stream().limit(3).toList()) + ".");
        }

        List<String> anchorMissingCoreTypes = fileFacts.stream()
                .filter(file -> CORE_EVIDENCE_TYPES.contains(file.evidenceType()))
                .filter(file -> !file.hasAnyAnchor())
                .map(file -> file.evidenceType().name())
                .distinct()
                .toList();
        if (!anchorMissingCoreTypes.isEmpty()) {
            highlights.add("Core files without visible anchors: " + String.join(", ", anchorMissingCoreTypes) + ".");
        }

        return List.copyOf(highlights);
    }

    private List<String> buildNarrativeSpine(
            List<FileEvidenceFacts> fileFacts,
            List<AnchorCoverage> sharedAnchors
    ) {
        List<String> lines = new ArrayList<>();
        fileFacts.stream()
                .filter(file -> file.evidenceType() == EvidenceType.ORDER_RECEIPT)
                .findFirst()
                .ifPresent(file -> lines.add(narrativeLine(
                        "Start with the transaction record",
                        primaryAnchor(file.orderRefs(), file.transactionRefs(), file.customerEmails())
                )));
        fileFacts.stream()
                .filter(file -> file.evidenceType() == EvidenceType.FULFILLMENT_DELIVERY)
                .findFirst()
                .ifPresent(file -> lines.add(narrativeLine(
                        "Then tie fulfillment evidence to the same record",
                        primaryAnchor(file.trackingRefs(), file.orderRefs(), file.transactionRefs())
                )));
        fileFacts.stream()
                .filter(file -> file.evidenceType() == EvidenceType.CUSTOMER_COMMUNICATION)
                .findFirst()
                .ifPresent(file -> lines.add(narrativeLine(
                        "Use customer communication to reinforce identity and timeline",
                        primaryAnchor(file.customerEmails(), file.orderRefs(), file.transactionRefs())
                )));
        fileFacts.stream()
                .filter(file -> file.evidenceType() == EvidenceType.REFUND_CANCELLATION)
                .findFirst()
                .ifPresent(file -> lines.add(narrativeLine(
                        "Close with refund or cancellation handling",
                        primaryAnchor(file.refundRefs(), file.orderRefs(), file.transactionRefs())
                )));
        if (lines.isEmpty() && !sharedAnchors.isEmpty()) {
            sharedAnchors.stream()
                    .limit(2)
                    .forEach(anchor -> lines.add("Anchor the explanation around " + anchor.kind() + " " + anchor.value()
                            + " because it appears across multiple evidence types."));
        }
        if (lines.isEmpty()) {
            lines.add("Build the narrative around a single order, customer, or tracking identifier and repeat it consistently in each paragraph.");
        }
        return List.copyOf(lines);
    }

    private int computeCoherenceScore(
            List<FileEvidenceFacts> fileFacts,
            List<AnchorCoverage> sharedAnchors
    ) {
        int score = 50;
        score += Math.min(18, sharedAnchors.size() * 6);

        boolean hasSharedCommercialAnchor = sharedAnchors.stream().anyMatch(this::isCommercialAnchor);
        if (hasSharedCommercialAnchor) {
            score += 10;
        }
        boolean hasTrackingInFulfillment = fileFacts.stream()
                .anyMatch(file -> file.evidenceType() == EvidenceType.FULFILLMENT_DELIVERY && !file.trackingRefs().isEmpty());
        if (hasTrackingInFulfillment) {
            score += 6;
        }
        boolean hasIdentityAnchor = fileFacts.stream()
                .anyMatch(file -> (file.evidenceType() == EvidenceType.CUSTOMER_DETAILS
                        || file.evidenceType() == EvidenceType.CUSTOMER_COMMUNICATION)
                        && !file.customerEmails().isEmpty());
        if (hasIdentityAnchor) {
            score += 4;
        }
        boolean hasRefundAnchor = fileFacts.stream()
                .anyMatch(file -> file.evidenceType() == EvidenceType.REFUND_CANCELLATION && !file.refundRefs().isEmpty());
        if (hasRefundAnchor) {
            score += 4;
        }

        long anchorlessCoreFiles = fileFacts.stream()
                .filter(file -> CORE_EVIDENCE_TYPES.contains(file.evidenceType()))
                .filter(file -> !file.hasAnyAnchor())
                .count();
        if (anchorlessCoreFiles > 0) {
            score -= Math.min(12, (int) anchorlessCoreFiles * 4);
        }

        Set<String> distinctCommercialRefs = new LinkedHashSet<>();
        fileFacts.forEach(file -> {
            distinctCommercialRefs.addAll(file.orderRefs());
            distinctCommercialRefs.addAll(file.transactionRefs());
        });
        if (distinctCommercialRefs.size() > 1 && !hasSharedCommercialAnchor) {
            score -= 10;
        }
        if (fileFacts.size() >= 2 && sharedAnchors.isEmpty()) {
            score -= 8;
        }
        return Math.max(0, Math.min(100, score));
    }

    private boolean isCommercialAnchor(AnchorCoverage anchor) {
        return "order".equals(anchor.kind()) || "transaction".equals(anchor.kind());
    }

    private String formatSharedAnchor(AnchorCoverage anchor) {
        String evidenceTypes = anchor.evidenceTypes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return "Shared " + anchor.kind() + " anchor " + displayAnchor(anchor.kind(), anchor.value())
                + " appears across " + evidenceTypes + ".";
    }

    private String narrativeLine(String prefix, String anchor) {
        if (anchor == null || anchor.isBlank()) {
            return prefix + ".";
        }
        return prefix + " using anchor " + displayAnchor("anchor", anchor) + ".";
    }

    private String primaryAnchor(List<String>... anchorLists) {
        for (List<String> anchorList : anchorLists) {
            if (anchorList != null && !anchorList.isEmpty()) {
                return anchorList.getFirst();
            }
        }
        return null;
    }

    private List<String> extractTrackingValues(String normalizedText) {
        LinkedHashSet<String> values = new LinkedHashSet<>(extractValues(
                "tracking",
                normalizedText,
                trackingPattern,
                trackingSpacedPattern
        ));
        values.addAll(extractValues("tracking", normalizedText, UPS_TRACKING_PATTERN));
        values.addAll(extractValues("tracking", normalizedText, USPS_TRACKING_PATTERN));
        values.addAll(extractValues("tracking", normalizedText, DHL_TRACKING_PATTERN));
        return List.copyOf(values);
    }

    private List<String> extractValues(String kind, String text, Pattern... patterns) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String candidate = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
                String normalized = normalizeValue(kind, candidate, text);
                if (normalized != null) {
                    values.add(normalized);
                }
            }
        }
        return List.copyOf(values);
    }

    private String normalizeSignalText(String value) {
        return defaultString(value)
                .replace('_', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeValue(String kind, String value, String signalText) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("[,.;)\\]]+$", "");
        if (normalized.length() < 3) {
            return null;
        }
        if ("email".equals(kind)) {
            return normalized.toLowerCase(Locale.ROOT);
        }

        String standardized = normalized
                .toUpperCase(Locale.ROOT)
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        String compact = standardized.replaceAll("[^A-Z0-9]", "");
        if (compact.length() < 3) {
            return null;
        }

        if ("tracking".equals(kind)) {
            String carrier = evidenceAliasCatalogService.inferTrackingCarrier(signalText, compact);
            if (carrier != null) {
                return carrier + ":" + compact;
            }
        }
        Matcher alphaNumeric = ALPHA_NUMERIC_WITH_OPTIONAL_SEPARATOR.matcher(compact);
        if (alphaNumeric.matches()) {
            return alphaNumeric.group(1) + "-" + alphaNumeric.group(2);
        }
        if (compact.matches("^\\d{8,}$")) {
            return compact;
        }
        String hyphenated = standardized
                .replace(' ', '-')
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (!hyphenated.isBlank() && hyphenated.length() >= 3) {
            return hyphenated;
        }
        return compact;
    }

    private Pattern patternWithLabels(String labelPattern, int minimumLength) {
        return Pattern.compile(
                labelPattern + OPTIONAL_REFERENCE_SUFFIX + "\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9-]{" + minimumLength + ",})",
                Pattern.CASE_INSENSITIVE
        );
    }

    private Pattern spacedPatternWithLabels(String labelPattern) {
        return Pattern.compile(
                labelPattern + OPTIONAL_REFERENCE_SUFFIX + "\\s*[:#-]?\\s*([A-Z]{1,6}\\s+\\d{2,})",
                Pattern.CASE_INSENSITIVE
        );
    }

    private String displayAnchor(String kind, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (("tracking".equals(kind) || "anchor".equals(kind)) && value.contains(":")) {
            int separator = value.indexOf(':');
            String left = value.substring(0, separator);
            String right = value.substring(separator + 1);
            if (!left.isBlank() && !right.isBlank()) {
                return left + " " + right;
            }
        }
        return value;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record CaseEvidenceFacts(
            List<FileEvidenceFacts> fileFacts,
            List<AnchorCoverage> sharedAnchors,
            List<String> coherenceHighlights,
            List<String> narrativeSpine,
            int coherenceScore
    ) {
    }

    public record FileEvidenceFacts(
            UUID fileId,
            EvidenceType evidenceType,
            String originalName,
            FileFormat fileFormat,
            List<String> orderRefs,
            List<String> transactionRefs,
            List<String> trackingRefs,
            List<String> refundRefs,
            List<String> customerEmails,
            boolean hasAnyAnchor
    ) {
    }

    public record AnchorCoverage(
            String kind,
            String value,
            List<EvidenceType> evidenceTypes
    ) {
    }

    private static final class AnchorAccumulator {
        private final String kind;
        private final String value;
        private final Set<EvidenceType> evidenceTypes = new LinkedHashSet<>();

        private AnchorAccumulator(String kind, String value) {
            this.kind = kind;
            this.value = value;
        }

        private String kind() {
            return kind;
        }

        private String value() {
            return value;
        }

        private Set<EvidenceType> evidenceTypes() {
            return evidenceTypes;
        }
    }
}
