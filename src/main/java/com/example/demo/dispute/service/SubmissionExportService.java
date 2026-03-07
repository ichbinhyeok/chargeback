package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.api.ValidationRunReportResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationFreshness;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubmissionExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CaseService caseService;
    private final CaseReportService caseReportService;
    private final EvidenceFileRepository evidenceFileRepository;
    private final ValidationFreshnessService validationFreshnessService;
    private final PolicyCatalogService policyCatalogService;
    private final DisputeExplanationService disputeExplanationService;
    private final ObjectMapper objectMapper;

    public SubmissionExportService(
            CaseService caseService,
            CaseReportService caseReportService,
            EvidenceFileRepository evidenceFileRepository,
            ValidationFreshnessService validationFreshnessService,
            PolicyCatalogService policyCatalogService,
            DisputeExplanationService disputeExplanationService
    ) {
        this.caseService = caseService;
        this.caseReportService = caseReportService;
        this.evidenceFileRepository = evidenceFileRepository;
        this.validationFreshnessService = validationFreshnessService;
        this.policyCatalogService = policyCatalogService;
        this.disputeExplanationService = disputeExplanationService;
        this.objectMapper = new ObjectMapper();
    }

    public void writeSubmissionZip(String caseToken, OutputStream outputStream) {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        ensureExportableState(disputeCase);
        ensureFreshValidationForZip(disputeCase);
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());
        List<EvidenceFileEntity> files = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("no uploaded files found for export");
        }

        List<EvidenceFileEntity> selectedFiles = selectFilesForZip(disputeCase, files);
        Map<EvidenceType, Integer> totalByType = new EnumMap<>(EvidenceType.class);
        for (EvidenceFileEntity file : selectedFiles) {
            totalByType.merge(file.getEvidenceType(), 1, Integer::sum);
        }

        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            Map<EvidenceType, Integer> sequenceByType = new EnumMap<>(EvidenceType.class);
            List<ZippedFileRow> zippedFileRows = new ArrayList<>();
            int order = 1;
            for (EvidenceFileEntity file : selectedFiles) {
                int typeSequence = sequenceByType.merge(file.getEvidenceType(), 1, Integer::sum);
                boolean multipleForType = totalByType.getOrDefault(file.getEvidenceType(), 0) > 1;
                String extension = extensionFor(file.getFileFormat());
                String repeatedTypeSuffix = multipleForType
                        ? String.format(Locale.ROOT, "_%02d", typeSequence)
                        : "";
                String zipName = String.format(
                        Locale.ROOT,
                        "%02d_%s%s%s",
                        order,
                        file.getEvidenceType().name(),
                        repeatedTypeSuffix,
                        extension
                );
                order++;

                ZipEntry entry = new ZipEntry(zipName);
                zip.putNextEntry(entry);
                Files.copy(Path.of(file.getStoragePath()), zip);
                zip.closeEntry();
                zippedFileRows.add(new ZippedFileRow(zipName, file));
            }
            writeExplanationEntry(zip, report);
            writeManifestEntry(zip, disputeCase, report, zippedFileRows);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to generate submission zip: " + ex.getMessage(), ex);
        }
    }

    public void writeSummaryPdf(String caseToken, OutputStream outputStream) {
        writeSummaryPdf(caseToken, outputStream, false);
    }

    public void writeSummaryPdf(String caseToken, OutputStream outputStream, boolean watermark) {
        DisputeCase disputeCase = caseService.getCaseByToken(caseToken);
        ensureExportableState(disputeCase);
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            List<String> lines = buildSummaryLines(report);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(14);
                content.newLineAtOffset(48, 740);
                for (String line : lines) {
                    content.showText(sanitize(line));
                    content.newLine();
                }
                content.endText();
            }
            if (watermark) {
                drawWatermark(document, page);
            }

            document.save(outputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to generate summary pdf: " + ex.getMessage(), ex);
        }
    }

    private List<EvidenceFileEntity> selectFilesForZip(DisputeCase disputeCase, List<EvidenceFileEntity> files) {
        if (disputeCase.getProductScope() == ProductScope.SHOPIFY_CREDIT_DISPUTE) {
            return sortDeterministic(files);
        }

        Map<EvidenceType, EvidenceFileEntity> latestByType = keepLatestFileByEvidenceType(files);
        List<EvidenceFileEntity> ordered = new ArrayList<>();
        for (EvidenceType type : EvidenceType.values()) {
            EvidenceFileEntity file = latestByType.get(type);
            if (file != null) {
                ordered.add(file);
            }
        }
        return ordered;
    }

    private Map<EvidenceType, EvidenceFileEntity> keepLatestFileByEvidenceType(List<EvidenceFileEntity> files) {
        Map<EvidenceType, EvidenceFileEntity> latestByType = new EnumMap<>(EvidenceType.class);
        for (EvidenceFileEntity file : files) {
            EvidenceFileEntity existing = latestByType.get(file.getEvidenceType());
            if (existing == null || compareByRecency(file, existing) > 0) {
                latestByType.put(file.getEvidenceType(), file);
            }
        }
        return latestByType;
    }

    private List<EvidenceFileEntity> sortDeterministic(List<EvidenceFileEntity> files) {
        List<EvidenceFileEntity> sorted = new ArrayList<>(files);
        sorted.sort(
                Comparator
                        .comparing(EvidenceFileEntity::getEvidenceType)
                        .thenComparing(EvidenceFileEntity::getCreatedAt)
                        .thenComparing(EvidenceFileEntity::getId)
        );
        return sorted;
    }

    private int compareByRecency(EvidenceFileEntity left, EvidenceFileEntity right) {
        return Comparator
                .comparing(EvidenceFileEntity::getCreatedAt)
                .thenComparing(EvidenceFileEntity::getId)
                .compare(left, right);
    }

    private List<String> buildSummaryLines(CaseReportResponse report) {
        int blockedCount = 0;
        int fixableCount = 0;
        int warningCount = 0;
        List<ValidationIssueResponse> issues = report.latestValidation() == null
                ? List.of()
                : report.latestValidation().issues();

        for (ValidationIssueResponse issue : issues) {
            if (issue.severity() == IssueSeverity.BLOCKED) {
                blockedCount++;
            } else if (issue.severity() == IssueSeverity.FIXABLE) {
                fixableCount++;
            } else if (issue.severity() == IssueSeverity.WARNING) {
                warningCount++;
            }
        }

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("Chargeback Submission Guide");
        lines.add("");
        lines.add("Platform: " + report.platform());
        lines.add("Scope: " + report.productScope());
        lines.add("State: " + report.state());
        lines.add("Created at: " + DATE_FORMAT.format(report.createdAt()));
        lines.add("");
        lines.add("Uploaded files:");
        if (report.files().isEmpty()) {
            lines.add("- No files uploaded");
        } else {
            for (EvidenceFileReportResponse file : report.files()) {
                lines.add(String.format(
                        Locale.ROOT,
                        "- %s | %s | %d bytes | pages=%d | externalLink=%s",
                        file.evidenceType(),
                        file.fileFormat(),
                        file.sizeBytes(),
                        file.pageCount(),
                        file.externalLinkDetected()
                ));
            }
        }

        lines.add("");
        lines.add("Validation summary:");
        lines.add(String.format(Locale.ROOT, "- BLOCKED: %d", blockedCount));
        lines.add(String.format(Locale.ROOT, "- FIXABLE: %d", fixableCount));
        lines.add(String.format(Locale.ROOT, "- WARNING: %d", warningCount));

        if (!issues.isEmpty()) {
            lines.add("- Top issues:");
            int limit = Math.min(3, issues.size());
            for (int i = 0; i < limit; i++) {
                ValidationIssueResponse issue = issues.get(i);
                lines.add(String.format(Locale.ROOT, "  %d) %s - %s", i + 1, issue.severity(), issue.message()));
            }
        }

        lines.add("");
        lines.add("Checklist:");
        lines.add("- Keep one file per evidence type.");
        lines.add("- Remove external links from PDFs.");
        lines.add("- Confirm platform file size/page limits before submission.");
        lines.add("- Use the generated dispute explanation draft and tailor it with transaction-specific facts.");
        lines.add("");
        lines.add("Disclaimer: This tool helps formatting and organization only, and does not guarantee dispute outcomes.");
        return lines;
    }

    private void ensureFreshValidationForZip(DisputeCase disputeCase) {
        if (!validationFreshnessService.hasFreshPassedValidation(disputeCase.getId())) {
            throw new IllegalArgumentException("validation is stale or failed; run validation before export");
        }
    }

    private void writeManifestEntry(
            ZipOutputStream zip,
            DisputeCase disputeCase,
            CaseReportResponse report,
            List<ZippedFileRow> zippedFileRows
    ) throws IOException {
        ValidationRunReportResponse validation = report.latestValidation();
        ValidationFreshness freshness = validationFreshnessService.freshness(disputeCase.getId());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("packVersion", "2.1");
        root.put("publicCaseRef", toPublicCaseRef(disputeCase));
        root.put("platform", disputeCase.getPlatform().name());
        root.put("productScope", disputeCase.getProductScope().name());
        root.put("state", disputeCase.getState().name());
        root.put("generatedAt", Instant.now().toString());
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getReasonCode(),
                disputeCase.getCardNetwork()
        );
        root.put("policyVersion", policy.policyVersion());
        root.put("policyContextKey", policy.contextKey());
        root.put("canonicalReasonKey", policy.canonicalReasonKey());

        Map<String, Object> validationNode = new LinkedHashMap<>();
        validationNode.put("freshness", freshness.name());
        validationNode.put("exists", validation != null);
        if (validation != null) {
            validationNode.put("runNo", validation.runNo());
            validationNode.put("passed", validation.passed());
            validationNode.put("source", validation.source().name());
            validationNode.put("validatedAt", validation.createdAt().toString());
        }
        root.put("validation", validationNode);

        List<Map<String, Object>> fileNodes = new ArrayList<>();
        for (ZippedFileRow row : zippedFileRows) {
            Map<String, Object> fileNode = new LinkedHashMap<>();
            fileNode.put("zipName", row.zipName());
            fileNode.put("evidenceType", row.file().getEvidenceType().name());
            fileNode.put("format", row.file().getFileFormat().name());
            fileNode.put("sizeBytes", row.file().getSizeBytes());
            fileNode.put("pageCount", row.file().getPageCount());
            fileNodes.add(fileNode);
        }
        root.put("files", fileNodes);

        byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        ZipEntry manifest = new ZipEntry("manifest.json");
        zip.putNextEntry(manifest);
        zip.write(manifestBytes);
        zip.closeEntry();
    }

    private void writeExplanationEntry(ZipOutputStream zip, CaseReportResponse report) throws IOException {
        DisputeExplanationService.ExplanationDraft draft = disputeExplanationService.buildDraft(report);
        byte[] explanationBytes = draft.text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ZipEntry explanation = new ZipEntry("dispute_explanation_draft.txt");
        zip.putNextEntry(explanation);
        zip.write(explanationBytes);
        zip.closeEntry();
    }

    private String toPublicCaseRef(DisputeCase disputeCase) {
        String date = DateTimeFormatter.BASIC_ISO_DATE
                .withZone(ZoneId.systemDefault())
                .format(disputeCase.getCreatedAt());
        String idSuffix = disputeCase.getId().toString().replace("-", "").substring(0, 6);
        return "cb_" + date + "_" + idSuffix;
    }

    private String extensionFor(FileFormat format) {
        return switch (format) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
        };
    }

    private void ensureExportableState(DisputeCase disputeCase) {
        CaseState state = disputeCase.getState();
        if (state != CaseState.READY && state != CaseState.PAID && state != CaseState.DOWNLOADED) {
            throw new IllegalArgumentException("case is not export-ready: " + state);
        }
    }

    private String sanitize(String line) {
        return line.replace('\t', ' ');
    }

    private void drawWatermark(PDDocument document, PDPage page) throws IOException {
        try (PDPageContentStream watermarkLayer = new PDPageContentStream(document, page, AppendMode.APPEND, true)) {
            watermarkLayer.beginText();
            watermarkLayer.setNonStrokingColor(0.84f, 0.84f, 0.84f);
            watermarkLayer.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 54);
            watermarkLayer.setTextMatrix(Matrix.getRotateInstance(
                    Math.toRadians(35),
                    page.getMediaBox().getWidth() * 0.14f,
                    page.getMediaBox().getHeight() * 0.34f
            ));
            watermarkLayer.showText("FREE VERSION");
            watermarkLayer.endText();

            watermarkLayer.beginText();
            watermarkLayer.setNonStrokingColor(0.88f, 0.88f, 0.88f);
            watermarkLayer.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 42);
            watermarkLayer.setTextMatrix(Matrix.getRotateInstance(
                    Math.toRadians(35),
                    page.getMediaBox().getWidth() * 0.22f,
                    page.getMediaBox().getHeight() * 0.27f
            ));
            watermarkLayer.showText("WATERMARKED");
            watermarkLayer.endText();
        }
    }

    private record ZippedFileRow(String zipName, EvidenceFileEntity file) {
    }
}
