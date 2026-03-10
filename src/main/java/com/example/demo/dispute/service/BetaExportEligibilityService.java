package com.example.demo.dispute.service;

import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.AuditLogRepository;
import com.example.demo.dispute.persistence.ValidationIssueEntity;
import com.example.demo.dispute.persistence.ValidationIssueRepository;
import com.example.demo.dispute.persistence.ValidationRunEntity;
import com.example.demo.dispute.persistence.ValidationRunRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BetaExportEligibilityService {

    private static final String SUPPORT_EMAIL = "shinhyeok22@gmail.com";
    private static final String AUTO_CONVERTED_UPLOAD_MARKER = "autoConverted=true";

    private final ValidationRunRepository validationRunRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final AuditLogRepository auditLogRepository;

    public BetaExportEligibilityService(
            ValidationRunRepository validationRunRepository,
            ValidationIssueRepository validationIssueRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.validationRunRepository = validationRunRepository;
        this.validationIssueRepository = validationIssueRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public BetaExportEligibility evaluate(UUID caseId) {
        List<ValidationRunEntity> runs = validationRunRepository.findTop2ByDisputeCaseIdOrderByRunNoDesc(caseId);
        if (runs.isEmpty()) {
            return new BetaExportEligibility(
                    false,
                    "Run validation first. Beta export opens only after a supported in-app fix flow passes."
            );
        }

        ValidationRunEntity latestRun = runs.getFirst();
        if (!latestRun.isPassed()) {
            return new BetaExportEligibility(
                    false,
                    "Resolve the current blockers first. Beta export only opens after the supported in-app fix flow passes."
            );
        }
        if (hasSupportedUploadNormalization(caseId)) {
            return new BetaExportEligibility(
                    true,
                    "Eligible for beta export after supported upload normalization."
            );
        }
        if (latestRun.getSource() != ValidationSource.AUTO_FIX) {
            return new BetaExportEligibility(
                    false,
                    "Beta export is currently limited to cases that pass after an in-app supported auto-fix run or "
                            + "supported upload normalization. "
                            + "If you resolved the case manually, email " + SUPPORT_EMAIL + "."
            );
        }
        if (runs.size() < 2) {
            return new BetaExportEligibility(
                    false,
                    "This beta export is currently limited to cases with a documented blocker detected before auto-fix. "
                            + "If you still want a manual review, email " + SUPPORT_EMAIL + "."
            );
        }

        ValidationRunEntity previousRun = runs.get(1);
        List<ValidationIssueEntity> actionableIssues = validationIssueRepository
                .findByValidationRunIdOrderByCodeAsc(previousRun.getId())
                .stream()
                .filter(this::isActionable)
                .toList();
        if (actionableIssues.isEmpty()) {
            return new BetaExportEligibility(
                    false,
                    "Beta export is currently limited to documented file blockers that the app fixed in-flow. "
                            + "If your case is more about evidence quality or filing judgment, email " + SUPPORT_EMAIL + "."
            );
        }
        boolean supportedOnly = actionableIssues.stream().allMatch(this::isSupportedBetaIssue);
        if (!supportedOnly) {
            return new BetaExportEligibility(
                    false,
                    "This case still falls outside the current beta export scope. Supported cases are currently limited to "
                            + "PDF/A conversion, portfolio flattening, hidden-link cleanup, duplicate evidence merge, and "
                            + "supported Shopify image compression. Email " + SUPPORT_EMAIL + " if you want manual review."
            );
        }

        return new BetaExportEligibility(true, "Eligible for beta export.");
    }

    private boolean hasSupportedUploadNormalization(UUID caseId) {
        return auditLogRepository.existsByDisputeCaseIdAndActionAndMetadataContaining(
                caseId,
                "FILE_UPLOADED",
                AUTO_CONVERTED_UPLOAD_MARKER
        );
    }

    private boolean isActionable(ValidationIssueEntity issue) {
        return issue.getSeverity() == IssueSeverity.BLOCKED || issue.getSeverity() == IssueSeverity.FIXABLE;
    }

    private boolean isSupportedBetaIssue(ValidationIssueEntity issue) {
        if ("ERR_SHPFY_FILE_TOO_LARGE".equals(issue.getCode())) {
            return true;
        }
        FixStrategy fixStrategy = issue.getFixStrategy();
        return fixStrategy == FixStrategy.MERGE_PER_TYPE
                || fixStrategy == FixStrategy.COMPRESS_SHOPIFY_IMAGE_IF_IMAGE
                || fixStrategy == FixStrategy.CONVERT_PDF_TO_PDFA
                || fixStrategy == FixStrategy.FLATTEN_PDF_PORTFOLIO
                || fixStrategy == FixStrategy.REMOVE_EXTERNAL_LINKS_PDF;
    }

    public record BetaExportEligibility(
            boolean eligible,
            String message
    ) {
    }
}
