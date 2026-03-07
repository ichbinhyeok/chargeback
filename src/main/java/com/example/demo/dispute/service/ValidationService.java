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

    private static final Set<FileFormat> ALLOWED_FORMATS = EnumSet.of(
            FileFormat.PDF,
            FileFormat.JPEG,
            FileFormat.PNG
    );

    private final ValidationIssueContractResolver contractResolver;
    private final PolicyCatalogService policyCatalogService;

    public ValidationService(
            ValidationIssueContractResolver contractResolver,
            PolicyCatalogService policyCatalogService
    ) {
        this.contractResolver = contractResolver;
        this.policyCatalogService = policyCatalogService;
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

        validateAllowedFormats(disputeCase.getPlatform(), files, issues);

        if (disputeCase.getPlatform() == Platform.STRIPE) {
            validateSingleFilePerType(disputeCase.getPlatform(), files, issues);
            validateExternalLinks(disputeCase.getPlatform(), files, issues);
            validateStripeRules(disputeCase, files, issues, policy);
        } else if (disputeCase.getPlatform() == Platform.SHOPIFY) {
            validateShopifyRules(disputeCase, files, earlySubmit, issues, policy);
        }

        boolean passed = issues.stream()
                .noneMatch(issue -> issue.severity() == IssueSeverity.BLOCKED || issue.severity() == IssueSeverity.FIXABLE);

        return new ValidateCaseResponse(passed, issues);
    }

    private void validateAllowedFormats(
            Platform platform,
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues
    ) {
        boolean hasInvalid = files.stream().anyMatch(file -> !ALLOWED_FORMATS.contains(file.format()));
        if (!hasInvalid) {
            return;
        }

        if (platform == Platform.STRIPE) {
            issues.add(issue(
                    "ERR_STRIPE_INVALID_FORMAT",
                    "STR_FMT_001",
                    IssueSeverity.BLOCKED,
                    "Stripe accepts only PDF, JPEG, PNG."
            ));
        } else {
            issues.add(issue(
                    "ERR_SHPFY_INVALID_FORMAT",
                    "SHP_FMT_001",
                    IssueSeverity.BLOCKED,
                    "Shopify accepts only PDF, JPEG, PNG."
            ));
        }
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

        boolean hasMultiple = typeCounts.values().stream().anyMatch(count -> count > 1);
        if (!hasMultiple) {
            return;
        }
        EvidenceType targetType = typeCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (platform == Platform.STRIPE) {
            issues.add(issue(
                    "ERR_STRIPE_MULTI_FILE_PER_TYPE",
                    "STR_FILE_001",
                    IssueSeverity.FIXABLE,
                    "Stripe requires one file per evidence type.",
                    IssueTargetScope.EVIDENCE_TYPE,
                    targetType,
                    FixStrategy.MERGE_PER_TYPE
            ));
        } else {
            issues.add(issue(
                    "ERR_SHPFY_MULTI_FILE_PER_TYPE",
                    "SHP_FILE_001",
                    IssueSeverity.FIXABLE,
                    "Shopify requires one file per evidence type.",
                    IssueTargetScope.EVIDENCE_TYPE,
                    targetType,
                    FixStrategy.MERGE_PER_TYPE
            ));
        }
    }

    private void validateExternalLinks(
            Platform platform,
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues
    ) {
        boolean hasExternalLink = files.stream().anyMatch(EvidenceFileInput::externalLinkDetected);
        if (!hasExternalLink) {
            return;
        }

        if (platform == Platform.STRIPE) {
            issues.add(issue(
                    "ERR_STRIPE_LINK_DETECTED",
                    "STR_CNT_001",
                    IssueSeverity.FIXABLE,
                    "External links are not allowed in Stripe evidence.",
                    IssueTargetScope.GLOBAL,
                    null,
                    FixStrategy.REMOVE_EXTERNAL_LINKS_PDF
            ));
        } else {
            issues.add(issue(
                    "ERR_SHPFY_LINK_DETECTED",
                    "SHP_CNT_001",
                    IssueSeverity.FIXABLE,
                    "External links are not allowed in Shopify evidence.",
                    IssueTargetScope.GLOBAL,
                    null,
                    FixStrategy.REMOVE_EXTERNAL_LINKS_PDF
            ));
        }
    }

    private void validateStripeRules(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        long totalSizeBytes = files.stream().mapToLong(EvidenceFileInput::sizeBytes).sum();
        int totalPages = files.stream().mapToInt(EvidenceFileInput::pageCount).sum();
        long totalSizeLimit = resolveTotalSizeLimitBytes(policy, LIMIT_4_5_MB);

        if (totalSizeBytes > totalSizeLimit) {
            issues.add(issue(
                    "ERR_STRIPE_TOTAL_SIZE",
                    "STR_SIZE_001",
                    IssueSeverity.BLOCKED,
                    "Total evidence size exceeds Stripe total size limit.",
                    IssueTargetScope.GLOBAL,
                    null,
                    FixStrategy.COMPRESS_STRIPE_PDF
            ));
        }

        if (totalPages >= 50) {
            issues.add(issue(
                    "ERR_STRIPE_TOTAL_PAGES",
                    "STR_PAGE_001",
                    IssueSeverity.BLOCKED,
                    "Total evidence pages must be below 50 for Stripe."
            ));
        }

        if (disputeCase.getCardNetwork() == CardNetwork.MASTERCARD && totalPages > 19) {
            issues.add(issue(
                    "ERR_STRIPE_MC_19P",
                    "STR_PAGE_002",
                    IssueSeverity.BLOCKED,
                    "Mastercard disputes allow up to 19 pages."
            ));
        }
    }

    private void validateShopifyRules(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            boolean earlySubmit,
            List<ValidationIssueResponse> issues,
            PolicyCatalogService.ResolvedPolicy policy
    ) {
        validateShopifyPdfPageLimit(files, issues);

        long totalSizeBytes = files.stream().mapToLong(EvidenceFileInput::sizeBytes).sum();
        long defaultLimit = disputeCase.getProductScope() == ProductScope.SHOPIFY_CREDIT_DISPUTE
                ? LIMIT_4_5_MB
                : LIMIT_4_MB;
        long totalSizeLimit = resolveTotalSizeLimitBytes(policy, defaultLimit);

        if (disputeCase.getProductScope() == ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK) {
            validateSingleFilePerType(disputeCase.getPlatform(), files, issues);
            validateExternalLinks(disputeCase.getPlatform(), files, issues);
            validateShopifyPaymentsFileRules(files, issues);

            if (totalSizeBytes > totalSizeLimit) {
                issues.add(issue(
                        "ERR_SHPFY_TOTAL_TOO_LARGE",
                        "SHP_SIZE_002",
                        IssueSeverity.BLOCKED,
                        "Shopify Payments total evidence size exceeds configured limit."
                ));
            }

            if (earlySubmit) {
                issues.add(issue(
                        "WARN_SHPFY_EARLY_SUBMIT",
                        "SHP_FLOW_001",
                        IssueSeverity.WARNING,
                        "Early submission warning: you cannot edit evidence after submit."
                ));
            }
            return;
        }

        if (disputeCase.getProductScope() == ProductScope.SHOPIFY_CREDIT_DISPUTE) {
            if (totalSizeBytes > totalSizeLimit) {
                issues.add(issue(
                        "ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE",
                        "SHP_SIZE_003",
                        IssueSeverity.BLOCKED,
                        "Shopify Credit total evidence size exceeds configured limit."
                ));
            }
            return;
        }

        if (totalSizeBytes > totalSizeLimit) {
            issues.add(issue(
                    "ERR_SHPFY_TOTAL_TOO_LARGE",
                    "SHP_SIZE_002",
                    IssueSeverity.BLOCKED,
                    "Shopify total evidence size exceeds configured limit."
            ));
        }
    }

    private void validateShopifyPdfPageLimit(
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues
    ) {
        for (EvidenceFileInput file : files) {
            if (file.format() == FileFormat.PDF && file.pageCount() >= 50) {
                issues.add(issue(
                        "ERR_SHPFY_PDF_PAGES_EXCEEDED",
                        "SHP_PAGE_001",
                        IssueSeverity.BLOCKED,
                        "Each Shopify PDF must be below 50 pages."
                ));
                break;
            }
        }
    }

    private void validateShopifyPaymentsFileRules(
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues
    ) {
        EvidenceFileInput oversizedImage = files.stream()
                .filter(file -> file.sizeBytes() > LIMIT_2_MB)
                .filter(file -> file.format() == FileFormat.JPEG || file.format() == FileFormat.PNG)
                .findFirst()
                .orElse(null);
        if (oversizedImage != null) {
            issues.add(issue(
                    "ERR_SHPFY_FILE_TOO_LARGE",
                    "SHP_SIZE_001",
                    IssueSeverity.FIXABLE,
                    "Each Shopify Payments evidence file must be 2MB or smaller.",
                    IssueTargetScope.EVIDENCE_TYPE,
                    oversizedImage.evidenceType(),
                    FixStrategy.COMPRESS_SHOPIFY_IMAGE_IF_IMAGE
            ));
        }

        EvidenceFileInput oversizedNonImage = files.stream()
                .filter(file -> file.sizeBytes() > LIMIT_2_MB)
                .filter(file -> file.format() == FileFormat.PDF)
                .findFirst()
                .orElse(null);
        if (oversizedNonImage != null) {
            issues.add(issue(
                    "ERR_SHPFY_FILE_TOO_LARGE",
                    "SHP_SIZE_001",
                    IssueSeverity.BLOCKED,
                    "Each Shopify Payments evidence file must be 2MB or smaller.",
                    IssueTargetScope.EVIDENCE_TYPE,
                    oversizedNonImage.evidenceType(),
                    FixStrategy.MANUAL
            ));
        }

        for (EvidenceFileInput file : files) {
            if (file.format() != FileFormat.PDF) {
                continue;
            }
            if (file.pdfPortfolio()) {
                issues.add(issue(
                        "ERR_SHPFY_PDF_PORTFOLIO",
                        "SHP_PDF_002",
                        IssueSeverity.BLOCKED,
                        "PDF Portfolio is not accepted by Shopify Payments.",
                        IssueTargetScope.EVIDENCE_TYPE,
                        file.evidenceType(),
                        FixStrategy.FLATTEN_PDF_PORTFOLIO
                ));
                break;
            }
        }

        for (EvidenceFileInput file : files) {
            if (file.format() == FileFormat.PDF && !file.pdfACompliant()) {
                issues.add(issue(
                        "ERR_SHPFY_PDF_NOT_PDFA",
                        "SHP_PDF_001",
                        IssueSeverity.BLOCKED,
                        "Shopify Payments requires PDF/A compliant documents.",
                        IssueTargetScope.EVIDENCE_TYPE,
                        file.evidenceType(),
                        FixStrategy.CONVERT_PDF_TO_PDFA
                ));
                break;
            }
        }
    }

    private ValidationIssueResponse issue(
            String code,
            String ruleId,
            IssueSeverity severity,
            String message
    ) {
        return issue(code, ruleId, severity, message, IssueTargetScope.GLOBAL, null, severity == IssueSeverity.FIXABLE ? FixStrategy.NONE : FixStrategy.MANUAL);
    }

    private ValidationIssueResponse issue(
            String code,
            String ruleId,
            IssueSeverity severity,
            String message,
            IssueTargetScope targetScope,
            EvidenceType targetEvidenceType,
            FixStrategy fixStrategy
    ) {
        return contractResolver.build(
                code,
                ruleId,
                severity,
                message,
                targetScope,
                targetEvidenceType,
                null,
                null,
                fixStrategy
        );
    }

    private long resolveTotalSizeLimitBytes(PolicyCatalogService.ResolvedPolicy policy, long fallbackLimitBytes) {
        if (policy == null || policy.totalSizeLimitBytes() == null || policy.totalSizeLimitBytes() <= 0) {
            return fallbackLimitBytes;
        }
        return policy.totalSizeLimitBytes();
    }
}
