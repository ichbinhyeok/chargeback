package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.ValidateCaseRequest;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.IssueTargetScope;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.DisputeCase;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    private static final long MB = 1024L * 1024L;
    private static final long LIMIT_4_MB = 4L * MB;
    private static final long LIMIT_4_5_MB = (long) (4.5d * MB);
    private static final long LIMIT_2_MB = 2L * MB;

    private static final Set<FileFormat> DEFAULT_ALLOWED_FORMATS = EnumSet.of(
            FileFormat.PDF,
            FileFormat.JPEG,
            FileFormat.PNG
    );

    private final ValidationIssueContractResolver contractResolver;
    private final PolicyCatalogService policyCatalogService;
    private final ValidationIssueCatalog issueCatalog;

    public ValidationService(
            ValidationIssueContractResolver contractResolver,
            PolicyCatalogService policyCatalogService,
            ValidationIssueCatalog issueCatalog
    ) {
        this.contractResolver = contractResolver;
        this.policyCatalogService = policyCatalogService;
        this.issueCatalog = issueCatalog;
    }

    public ValidateCaseResponse validate(DisputeCase disputeCase, ValidateCaseRequest request) {
        return validate(disputeCase, request.files(), request.earlySubmit());
    }

    public ValidateCaseResponse validate(DisputeCase disputeCase, List<EvidenceFileInput> files, boolean earlySubmit) {
        List<ValidationIssueResponse> issues = new ArrayList<>();
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getReasonCode(),
                disputeCase.getCardNetwork()
        );

        validateAllowedFormats(disputeCase, files, policy, issues);

        if (resolveSingleFilePerEvidenceType(disputeCase, policy)) {
            validateSingleFilePerType(disputeCase.getPlatform(), files, issues);
        }
        if (!resolveExternalLinksAllowed(disputeCase, policy)) {
            validateExternalLinks(disputeCase.getPlatform(), files, issues);
        }

        validateTotalSize(disputeCase, files, policy, issues);
        validateTotalPages(disputeCase, files, policy, issues);
        validatePdfPageLimit(disputeCase, files, policy, issues);
        validatePerFileLimits(disputeCase, files, policy, issues);
        validatePdfCompliance(disputeCase, files, policy, issues);

        if (disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK && earlySubmit) {
            issues.add(issue("WARN_SHPFY_EARLY_SUBMIT"));
        }

        boolean passed = issues.stream()
                .noneMatch(issue -> issue.severity() == IssueSeverity.BLOCKED || issue.severity() == IssueSeverity.FIXABLE);

        return new ValidateCaseResponse(passed, issues);
    }

    private void validateAllowedFormats(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            PolicyCatalogService.ResolvedPolicy policy,
            List<ValidationIssueResponse> issues
    ) {
        Set<FileFormat> allowedFormats = resolveAllowedFormats(policy);
        boolean hasInvalid = files.stream().anyMatch(file -> !allowedFormats.contains(file.format()));
        if (!hasInvalid) {
            return;
        }

        issues.add(issue(disputeCase.getPlatform() == Platform.STRIPE
                ? "ERR_STRIPE_INVALID_FORMAT"
                : "ERR_SHPFY_INVALID_FORMAT"));
    }

    private void validateSingleFilePerType(
            Platform platform,
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues
    ) {
        Map<EvidenceType, Integer> typeCounts = new EnumMap<>(EvidenceType.class);
        for (EvidenceFileInput file : files) {
            typeCounts.merge(file.evidenceType(), 1, Integer::sum);
        }

        EvidenceType targetType = typeCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (targetType == null) {
            return;
        }

        issues.add(issue(
                platform == Platform.STRIPE ? "ERR_STRIPE_MULTI_FILE_PER_TYPE" : "ERR_SHPFY_MULTI_FILE_PER_TYPE",
                IssueTargetScope.EVIDENCE_TYPE,
                targetType
        ));
    }

    private void validateExternalLinks(
            Platform platform,
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues
    ) {
        boolean hasExternalLink = files.stream()
                .filter(file -> file.format() == FileFormat.PDF)
                .anyMatch(EvidenceFileInput::externalLinkDetected);
        if (!hasExternalLink) {
            return;
        }

        issues.add(issue(platform == Platform.STRIPE ? "ERR_STRIPE_LINK_DETECTED" : "ERR_SHPFY_LINK_DETECTED"));
    }

    private void validateTotalSize(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            PolicyCatalogService.ResolvedPolicy policy,
            List<ValidationIssueResponse> issues
    ) {
        long totalSizeBytes = files.stream().mapToLong(EvidenceFileInput::sizeBytes).sum();
        long totalSizeLimit = resolveTotalSizeLimitBytes(disputeCase, policy);
        if (totalSizeLimit <= 0 || totalSizeBytes <= totalSizeLimit) {
            return;
        }

        String code = switch (disputeCase.getProductScope()) {
            case STRIPE_DISPUTE -> "ERR_STRIPE_TOTAL_SIZE";
            case SHOPIFY_CREDIT_DISPUTE -> "ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE";
            case SHOPIFY_PAYMENTS_CHARGEBACK -> "ERR_SHPFY_TOTAL_TOO_LARGE";
        };
        issues.add(issue(code));
    }

    private void validateTotalPages(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            PolicyCatalogService.ResolvedPolicy policy,
            List<ValidationIssueResponse> issues
    ) {
        Integer totalPagesLimit = resolveTotalPagesLimit(disputeCase, policy);
        if (totalPagesLimit == null || totalPagesLimit <= 0) {
            return;
        }

        int totalPages = files.stream().mapToInt(EvidenceFileInput::pageCount).sum();
        if (totalPages <= totalPagesLimit) {
            return;
        }

        String code = disputeCase.getCardNetwork() == CardNetwork.MASTERCARD && totalPagesLimit <= 19
                ? "ERR_STRIPE_MC_19P"
                : "ERR_STRIPE_TOTAL_PAGES";
        issues.add(issue(code));
    }

    private void validatePdfPageLimit(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            PolicyCatalogService.ResolvedPolicy policy,
            List<ValidationIssueResponse> issues
    ) {
        Integer pdfPageLimit = resolvePerPdfPageLimit(disputeCase, policy);
        if (pdfPageLimit == null || pdfPageLimit <= 0) {
            return;
        }

        EvidenceFileInput oversizedPdf = files.stream()
                .filter(file -> file.format() == FileFormat.PDF)
                .filter(file -> file.pageCount() > pdfPageLimit)
                .findFirst()
                .orElse(null);
        if (oversizedPdf == null) {
            return;
        }

        issues.add(issue(
                "ERR_SHPFY_PDF_PAGES_EXCEEDED",
                IssueTargetScope.EVIDENCE_TYPE,
                oversizedPdf.evidenceType()
        ));
    }

    private void validatePerFileLimits(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            PolicyCatalogService.ResolvedPolicy policy,
            List<ValidationIssueResponse> issues
    ) {
        long imageFileLimit = resolveImageFileSizeLimitBytes(disputeCase, policy);
        if (imageFileLimit > 0) {
            EvidenceFileInput oversizedImage = files.stream()
                    .filter(file -> file.format() == FileFormat.JPEG || file.format() == FileFormat.PNG)
                    .filter(file -> file.sizeBytes() > imageFileLimit)
                    .findFirst()
                    .orElse(null);
            if (oversizedImage != null) {
                issues.add(issue(
                        "ERR_SHPFY_FILE_TOO_LARGE",
                        IssueTargetScope.EVIDENCE_TYPE,
                        oversizedImage.evidenceType()
                ));
            }
        }

        long genericFileLimit = resolvePerFileSizeLimitBytes(disputeCase, policy);
        if (genericFileLimit <= 0) {
            return;
        }

        EvidenceFileInput oversizedNonImage = files.stream()
                .filter(file -> file.format() == FileFormat.PDF)
                .filter(file -> file.sizeBytes() > genericFileLimit)
                .findFirst()
                .orElse(null);
        if (oversizedNonImage == null) {
            return;
        }

        issues.add(issue(
                "ERR_SHPFY_FILE_TOO_LARGE",
                IssueTargetScope.EVIDENCE_TYPE,
                oversizedNonImage.evidenceType(),
                null,
                IssueSeverity.BLOCKED,
                FixStrategy.MANUAL
        ));
    }

    private void validatePdfCompliance(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            PolicyCatalogService.ResolvedPolicy policy,
            List<ValidationIssueResponse> issues
    ) {
        if (!resolvePortfolioPdfAllowed(disputeCase, policy)) {
            EvidenceFileInput portfolioPdf = files.stream()
                    .filter(file -> file.format() == FileFormat.PDF)
                    .filter(EvidenceFileInput::pdfPortfolio)
                    .findFirst()
                    .orElse(null);
            if (portfolioPdf != null) {
                issues.add(issue(
                        "ERR_SHPFY_PDF_PORTFOLIO",
                        IssueTargetScope.EVIDENCE_TYPE,
                        portfolioPdf.evidenceType()
                ));
            }
        }

        if (!resolvePdfARequired(disputeCase, policy)) {
            return;
        }

        EvidenceFileInput nonPdfaFile = files.stream()
                .filter(file -> file.format() == FileFormat.PDF)
                .filter(file -> !file.pdfACompliant())
                .findFirst()
                .orElse(null);
        if (nonPdfaFile == null) {
            return;
        }

        issues.add(issue(
                "ERR_SHPFY_PDF_NOT_PDFA",
                IssueTargetScope.EVIDENCE_TYPE,
                nonPdfaFile.evidenceType()
        ));
    }

    private Set<FileFormat> resolveAllowedFormats(PolicyCatalogService.ResolvedPolicy policy) {
        if (policy == null || policy.allowedFormats() == null || policy.allowedFormats().isEmpty()) {
            return DEFAULT_ALLOWED_FORMATS;
        }
        return EnumSet.copyOf(policy.allowedFormats());
    }

    private boolean resolveSingleFilePerEvidenceType(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.singleFilePerEvidenceType() != null) {
            return policy.singleFilePerEvidenceType();
        }
        return disputeCase.getPlatform() == Platform.STRIPE
                || disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK;
    }

    private boolean resolveExternalLinksAllowed(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.externalLinksAllowed() != null) {
            return policy.externalLinksAllowed();
        }
        return !(disputeCase.getPlatform() == Platform.STRIPE
                || disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
    }

    private long resolveTotalSizeLimitBytes(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.totalSizeLimitBytes() != null && policy.totalSizeLimitBytes() > 0) {
            return policy.totalSizeLimitBytes();
        }
        if (disputeCase.getPlatform() == Platform.STRIPE) {
            return LIMIT_4_5_MB;
        }
        return disputeCase.getProductScope() == ProductScope.SHOPIFY_CREDIT_DISPUTE
                ? LIMIT_4_5_MB
                : LIMIT_4_MB;
    }

    private Integer resolveTotalPagesLimit(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.totalPagesLimit() != null && policy.totalPagesLimit() > 0) {
            return policy.totalPagesLimit();
        }
        if (disputeCase.getPlatform() != Platform.STRIPE) {
            return null;
        }
        return disputeCase.getCardNetwork() == CardNetwork.MASTERCARD ? 19 : 49;
    }

    private Integer resolvePerPdfPageLimit(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.perPdfPageLimit() != null && policy.perPdfPageLimit() > 0) {
            return policy.perPdfPageLimit();
        }
        return disputeCase.getPlatform() == Platform.SHOPIFY ? 49 : null;
    }

    private long resolvePerFileSizeLimitBytes(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.perFileSizeLimitBytes() != null && policy.perFileSizeLimitBytes() > 0) {
            return policy.perFileSizeLimitBytes();
        }
        return disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK ? LIMIT_2_MB : 0L;
    }

    private long resolveImageFileSizeLimitBytes(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.perImageFileSizeLimitBytes() != null && policy.perImageFileSizeLimitBytes() > 0) {
            return policy.perImageFileSizeLimitBytes();
        }
        return disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK ? LIMIT_2_MB : 0L;
    }

    private boolean resolvePdfARequired(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.pdfARequired() != null) {
            return policy.pdfARequired();
        }
        return disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK;
    }

    private boolean resolvePortfolioPdfAllowed(
            DisputeCase disputeCase,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        if (policy != null && policy.portfolioPdfAllowed() != null) {
            return policy.portfolioPdfAllowed();
        }
        return disputeCase.getProductScope() != ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK;
    }

    private ValidationIssueResponse issue(String code) {
        return issue(code, null, null, null, null, null);
    }

    private ValidationIssueResponse issue(String code, IssueTargetScope targetScope, EvidenceType targetEvidenceType) {
        return issue(code, targetScope, targetEvidenceType, null, null, null);
    }

    private ValidationIssueResponse issue(
            String code,
            IssueTargetScope targetScope,
            EvidenceType targetEvidenceType,
            String messageOverride,
            IssueSeverity severityOverride,
            FixStrategy fixStrategyOverride
    ) {
        ValidationIssueCatalog.IssueDefinition definition = issueCatalog.require(code);
        return contractResolver.build(
                code,
                definition.ruleId(),
                severityOverride != null ? severityOverride : definition.severity(),
                messageOverride != null ? messageOverride : definition.message(),
                targetScope != null ? targetScope : definition.targetScope(),
                targetEvidenceType,
                null,
                null,
                fixStrategyOverride != null ? fixStrategyOverride : definition.fixStrategy()
        );
    }
}
