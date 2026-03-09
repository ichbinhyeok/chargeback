package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubmissionExportService {
    private static final String UPLOAD_DIR = "upload_to_platform/";
    private static final String REFERENCE_DIR = "reference/";
    private static final String README_NAME = "README_FIRST.txt";
    private static final String MANIFEST_NAME = REFERENCE_DIR + "manifest.json";
    private static final String EXPLANATION_NAME = REFERENCE_DIR + "dispute_explanation_draft.txt";

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
                        UPLOAD_DIR + "%02d_%s%s%s",
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
            writeReadmeEntry(zip, disputeCase, zippedFileRows);
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
        SummaryPdfRenderer.write(report, outputStream, watermark);
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
        root.put("publicCaseRef", PublicCaseReference.from(disputeCase));
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
            List<Map<String, Object>> issueNodes = new ArrayList<>();
            for (ValidationIssueResponse issue : validation.issues()) {
                Map<String, Object> issueNode = new LinkedHashMap<>();
                issueNode.put("code", issue.code());
                issueNode.put("severity", issue.severity().name());
                issueNode.put("ruleId", issue.ruleId());
                issueNode.put("message", issue.message());
                issueNode.put("fixStrategy", issue.fixStrategy().name());
                if (issue.guideSlug() != null && !issue.guideSlug().isBlank()) {
                    issueNode.put("guideSlug", issue.guideSlug());
                    issueNode.put("guideTitle", issue.guideTitle());
                    issueNode.put("guidePath", issueGuidePath(report, issue));
                }
                issueNodes.add(issueNode);
            }
            validationNode.put("issues", issueNodes);
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
        ZipEntry manifest = new ZipEntry(MANIFEST_NAME);
        zip.putNextEntry(manifest);
        zip.write(manifestBytes);
        zip.closeEntry();
    }

    private void writeExplanationEntry(ZipOutputStream zip, CaseReportResponse report) throws IOException {
        DisputeExplanationService.ExplanationDraft draft = disputeExplanationService.buildDraft(report);
        byte[] explanationBytes = draft.text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ZipEntry explanation = new ZipEntry(EXPLANATION_NAME);
        zip.putNextEntry(explanation);
        zip.write(explanationBytes);
        zip.closeEntry();
    }

    private void writeReadmeEntry(
            ZipOutputStream zip,
            DisputeCase disputeCase,
            List<ZippedFileRow> zippedFileRows
    ) throws IOException {
        CaseReportResponse report = caseReportService.getReport(disputeCase.getId());
        StringBuilder readme = new StringBuilder();
        readme.append("Chargeback Submission Pack").append(System.lineSeparator()).append(System.lineSeparator());
        readme.append("Upload only the files inside '").append(UPLOAD_DIR).append("'.").append(System.lineSeparator());
        readme.append("Do not upload files inside '").append(REFERENCE_DIR).append("'.").append(System.lineSeparator()).append(System.lineSeparator());
        readme.append("What to do").append(System.lineSeparator());
        readme.append("1. Extract this ZIP.").append(System.lineSeparator());
        readme.append("2. Open '").append(UPLOAD_DIR).append("' and upload those PDF/JPG/PNG files to your dispute dashboard.")
                .append(System.lineSeparator());
        readme.append("3. Use '").append(EXPLANATION_NAME)
                .append("' only as a writing aid if the platform asks for additional explanation text.").append(System.lineSeparator());
        readme.append("4. Ignore '").append(MANIFEST_NAME).append("' for platform submission.").append(System.lineSeparator()).append(System.lineSeparator());
        readme.append("Prepared upload files").append(System.lineSeparator());
        for (ZippedFileRow row : zippedFileRows) {
            readme.append("- ")
                    .append(row.zipName())
                    .append(" -> ")
                    .append(row.file().getEvidenceType().name())
                    .append(System.lineSeparator());
        }
        List<ValidationIssueResponse> issues = report.latestValidation() == null ? List.of() : report.latestValidation().issues();
        List<ValidationIssueResponse> issueGuides = distinctIssueGuides(issues).stream()
                .limit(3)
                .toList();
        if (!issueGuides.isEmpty()) {
            readme.append(System.lineSeparator());
            readme.append("Issue-specific fix guides").append(System.lineSeparator());
            for (ValidationIssueResponse issue : issueGuides) {
                readme.append("- ")
                        .append(issue.guideTitle() == null || issue.guideTitle().isBlank() ? issue.code() : issue.guideTitle())
                        .append(": ")
                        .append(issueGuidePath(report, issue))
                        .append(System.lineSeparator());
            }
        }
        readme.append(System.lineSeparator());
        readme.append("Case ref: ").append(PublicCaseReference.from(disputeCase)).append(System.lineSeparator());
        readme.append("Disclaimer: This pack helps organize evidence files but does not guarantee platform acceptance or dispute outcomes.")
                .append(System.lineSeparator());

        ZipEntry readmeEntry = new ZipEntry(README_NAME);
        zip.putNextEntry(readmeEntry);
        zip.write(readme.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String extensionFor(FileFormat format) {
        return switch (format) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
        };
    }

    private String issueGuidePath(CaseReportResponse report, ValidationIssueResponse issue) {
        return "/guides/" + report.platform().name().toLowerCase(Locale.ROOT) + "/" + issue.guideSlug();
    }

    private List<ValidationIssueResponse> distinctIssueGuides(List<ValidationIssueResponse> issues) {
        LinkedHashMap<String, ValidationIssueResponse> byGuideSlug = new LinkedHashMap<>();
        for (ValidationIssueResponse issue : issues) {
            if (issue.guideSlug() == null || issue.guideSlug().isBlank()) {
                continue;
            }
            byGuideSlug.putIfAbsent(issue.guideSlug(), issue);
        }
        return List.copyOf(byGuideSlug.values());
    }

    private void ensureExportableState(DisputeCase disputeCase) {
        CaseState state = disputeCase.getState();
        if (state != CaseState.READY && state != CaseState.PAID && state != CaseState.DOWNLOADED) {
            throw new IllegalArgumentException("case is not export-ready: " + state);
        }
    }

    private record ZippedFileRow(String zipName, EvidenceFileEntity file) {
    }
}
