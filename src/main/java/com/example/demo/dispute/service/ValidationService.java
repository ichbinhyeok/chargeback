package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.ValidateCaseRequest;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.IssueSeverity;
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

    public ValidateCaseResponse validate(DisputeCase disputeCase, ValidateCaseRequest request) {
        return validate(disputeCase, request.files(), request.earlySubmit());
    }

    public ValidateCaseResponse validate(DisputeCase disputeCase, List<EvidenceFileInput> files, boolean earlySubmit) {
        List<ValidationIssueResponse> issues = new ArrayList<>();

        validateAllowedFormats(disputeCase.getPlatform(), files, issues);
        validateSingleFilePerType(disputeCase.getPlatform(), files, issues);
        validateExternalLinks(disputeCase.getPlatform(), files, issues);

        if (disputeCase.getPlatform() == Platform.STRIPE) {
            validateStripeRules(disputeCase, files, issues);
        } else if (disputeCase.getPlatform() == Platform.SHOPIFY) {
            validateShopifyRules(disputeCase, files, earlySubmit, issues);
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

        if (platform == Platform.STRIPE) {
            issues.add(issue(
                    "ERR_STRIPE_MULTI_FILE_PER_TYPE",
                    "STR_FILE_001",
                    IssueSeverity.FIXABLE,
                    "Stripe requires one file per evidence type."
            ));
        } else {
            issues.add(issue(
                    "ERR_SHPFY_MULTI_FILE_PER_TYPE",
                    "SHP_FILE_001",
                    IssueSeverity.FIXABLE,
                    "Shopify requires one file per evidence type."
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
                    IssueSeverity.BLOCKED,
                    "External links are not allowed in Stripe evidence."
            ));
        } else {
            issues.add(issue(
                    "ERR_SHPFY_LINK_DETECTED",
                    "SHP_CNT_001",
                    IssueSeverity.BLOCKED,
                    "External links are not allowed in Shopify evidence."
            ));
        }
    }

    private void validateStripeRules(
            DisputeCase disputeCase,
            List<EvidenceFileInput> files,
            List<ValidationIssueResponse> issues
    ) {
        long totalSizeBytes = files.stream().mapToLong(EvidenceFileInput::sizeBytes).sum();
        int totalPages = files.stream().mapToInt(EvidenceFileInput::pageCount).sum();

        if (totalSizeBytes > LIMIT_4_5_MB) {
            issues.add(issue(
                    "ERR_STRIPE_TOTAL_SIZE",
                    "STR_SIZE_001",
                    IssueSeverity.BLOCKED,
                    "Total evidence size exceeds Stripe 4.5MB limit."
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
            List<ValidationIssueResponse> issues
    ) {
        long totalSizeBytes = files.stream().mapToLong(EvidenceFileInput::sizeBytes).sum();

        boolean hasLargeSingleFile = files.stream().anyMatch(file -> file.sizeBytes() > LIMIT_2_MB);
        if (hasLargeSingleFile) {
            issues.add(issue(
                    "ERR_SHPFY_FILE_TOO_LARGE",
                    "SHP_SIZE_001",
                    IssueSeverity.FIXABLE,
                    "Each Shopify evidence file must be 2MB or smaller."
            ));
        }

        for (EvidenceFileInput file : files) {
            if (file.format() != FileFormat.PDF) {
                continue;
            }

            if (file.pageCount() >= 50) {
                issues.add(issue(
                        "ERR_SHPFY_PDF_PAGES_EXCEEDED",
                        "SHP_PAGE_001",
                        IssueSeverity.BLOCKED,
                        "Each Shopify PDF must be below 50 pages."
                ));
                break;
            }
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
                        "PDF Portfolio is not accepted by Shopify."
                ));
                break;
            }
        }

        for (EvidenceFileInput file : files) {
            if (file.format() == FileFormat.PDF && !file.pdfACompliant()) {
                issues.add(issue(
                        "ERR_SHPFY_PDF_NOT_PDFA",
                        "SHP_PDF_001",
                        IssueSeverity.FIXABLE,
                        "Shopify requires PDF/A compliant documents."
                ));
                break;
            }
        }

        if (disputeCase.getProductScope() == ProductScope.SHOPIFY_CREDIT_DISPUTE) {
            if (totalSizeBytes > LIMIT_4_5_MB) {
                issues.add(issue(
                        "ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE",
                        "SHP_SIZE_003",
                        IssueSeverity.FIXABLE,
                        "Shopify Credit total evidence size must be 4.5MB or less."
                ));
            }
        } else {
            if (totalSizeBytes > LIMIT_4_MB) {
                issues.add(issue(
                        "ERR_SHPFY_TOTAL_TOO_LARGE",
                        "SHP_SIZE_002",
                        IssueSeverity.FIXABLE,
                        "Shopify Payments total evidence size must be 4MB or less."
                ));
            }
        }

        if (earlySubmit) {
            issues.add(issue(
                    "WARN_SHPFY_EARLY_SUBMIT",
                    "SHP_FLOW_001",
                    IssueSeverity.WARNING,
                    "Early submission warning: you cannot edit evidence after submit."
            ));
        }
    }

    private ValidationIssueResponse issue(
            String code,
            String ruleId,
            IssueSeverity severity,
            String message
    ) {
        return new ValidationIssueResponse(code, ruleId, severity, message);
    }
}
