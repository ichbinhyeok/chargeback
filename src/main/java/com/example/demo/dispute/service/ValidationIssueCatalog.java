package com.example.demo.dispute.service;

import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.IssueTargetScope;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ValidationIssueCatalog {

    private static final Map<String, IssueDefinition> DEFINITIONS = Map.ofEntries(
            Map.entry(
                    "ERR_STRIPE_INVALID_FORMAT",
                    new IssueDefinition(
                            "STR_FMT_001",
                            IssueSeverity.BLOCKED,
                            "Stripe accepts only PDF, JPEG, PNG.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.MANUAL,
                            "invalid-file-format-pdf-jpg-png-only",
                            "Stripe invalid file format"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_INVALID_FORMAT",
                    new IssueDefinition(
                            "SHP_FMT_001",
                            IssueSeverity.BLOCKED,
                            "Shopify accepts only PDF, JPEG, PNG.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.MANUAL,
                            "invalid-file-format-pdf-jpg-png-only",
                            "Shopify invalid file format"
                    )
            ),
            Map.entry(
                    "ERR_STRIPE_MULTI_FILE_PER_TYPE",
                    new IssueDefinition(
                            "STR_FILE_001",
                            IssueSeverity.FIXABLE,
                            "Stripe requires one file per evidence type.",
                            IssueTargetScope.EVIDENCE_TYPE,
                            FixStrategy.MERGE_PER_TYPE,
                            "duplicate-evidence-type-file-error",
                            "Stripe duplicate evidence type"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_MULTI_FILE_PER_TYPE",
                    new IssueDefinition(
                            "SHP_FILE_001",
                            IssueSeverity.FIXABLE,
                            "Shopify requires one file per evidence type.",
                            IssueTargetScope.EVIDENCE_TYPE,
                            FixStrategy.MERGE_PER_TYPE,
                            "duplicate-evidence-type-file-error",
                            "Shopify duplicate evidence type"
                    )
            ),
            Map.entry(
                    "ERR_STRIPE_LINK_DETECTED",
                    new IssueDefinition(
                            "STR_CNT_001",
                            IssueSeverity.FIXABLE,
                            "External links are not allowed in Stripe evidence.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.REMOVE_EXTERNAL_LINKS_PDF,
                            "upload-failed-no-external-links",
                            "Stripe external links not allowed"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_LINK_DETECTED",
                    new IssueDefinition(
                            "SHP_CNT_001",
                            IssueSeverity.FIXABLE,
                            "External links are not allowed in Shopify evidence.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.REMOVE_EXTERNAL_LINKS_PDF,
                            "external-links-not-allowed-error",
                            "Shopify external links not allowed"
                    )
            ),
            Map.entry(
                    "ERR_STRIPE_TOTAL_SIZE",
                    new IssueDefinition(
                            "STR_SIZE_001",
                            IssueSeverity.BLOCKED,
                            "Total evidence size exceeds Stripe total size limit.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.REDUCE_TOTAL_SIZE,
                            "evidence-file-size-limit-4-5mb",
                            "Stripe evidence size limit"
                    )
            ),
            Map.entry(
                    "ERR_STRIPE_TOTAL_PAGES",
                    new IssueDefinition(
                            "STR_PAGE_001",
                            IssueSeverity.BLOCKED,
                            "Total evidence pages must be below 50 for Stripe.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.REDUCE_TOTAL_PAGES,
                            "total-pages-over-limit",
                            "Stripe total pages over limit"
                    )
            ),
            Map.entry(
                    "ERR_STRIPE_MC_19P",
                    new IssueDefinition(
                            "STR_PAGE_002",
                            IssueSeverity.BLOCKED,
                            "Mastercard disputes allow up to 19 pages.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.REDUCE_TOTAL_PAGES,
                            "mastercard-19-page-limit",
                            "Stripe Mastercard 19 page limit"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_TOTAL_TOO_LARGE",
                    new IssueDefinition(
                            "SHP_SIZE_002",
                            IssueSeverity.BLOCKED,
                            "Shopify Payments total evidence size exceeds configured limit.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.REDUCE_TOTAL_SIZE,
                            "shopify-payments-total-size-over-4mb",
                            "Shopify Payments total size over limit"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE",
                    new IssueDefinition(
                            "SHP_SIZE_003",
                            IssueSeverity.BLOCKED,
                            "Shopify Credit total evidence size exceeds configured limit.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.REDUCE_TOTAL_SIZE,
                            null,
                            "Shopify Credit total size over limit"
                    )
            ),
            Map.entry(
                    "WARN_SHPFY_EARLY_SUBMIT",
                    new IssueDefinition(
                            "SHP_FLOW_001",
                            IssueSeverity.WARNING,
                            "Early submission warning: you cannot edit evidence after submit.",
                            IssueTargetScope.GLOBAL,
                            FixStrategy.MANUAL,
                            null,
                            "Shopify early submit warning"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_PDF_PAGES_EXCEEDED",
                    new IssueDefinition(
                            "SHP_PAGE_001",
                            IssueSeverity.BLOCKED,
                            "Each Shopify PDF must be below 50 pages.",
                            IssueTargetScope.EVIDENCE_TYPE,
                            FixStrategy.REDUCE_TOTAL_PAGES,
                            "pdf-pages-over-50-error",
                            "Shopify PDF pages over limit"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_FILE_TOO_LARGE",
                    new IssueDefinition(
                            "SHP_SIZE_001",
                            IssueSeverity.FIXABLE,
                            "Each Shopify Payments evidence file must be 2MB or smaller.",
                            IssueTargetScope.EVIDENCE_TYPE,
                            FixStrategy.COMPRESS_SHOPIFY_IMAGE_IF_IMAGE,
                            "evidence-file-too-large-2mb",
                            "Shopify evidence file too large"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_PDF_PORTFOLIO",
                    new IssueDefinition(
                            "SHP_PDF_002",
                            IssueSeverity.BLOCKED,
                            "PDF Portfolio is not accepted by Shopify Payments.",
                            IssueTargetScope.EVIDENCE_TYPE,
                            FixStrategy.FLATTEN_PDF_PORTFOLIO,
                            "pdf-portfolio-not-accepted",
                            "Shopify PDF portfolio not accepted"
                    )
            ),
            Map.entry(
                    "ERR_SHPFY_PDF_NOT_PDFA",
                    new IssueDefinition(
                            "SHP_PDF_001",
                            IssueSeverity.BLOCKED,
                            "Shopify Payments requires PDF/A compliant documents.",
                            IssueTargetScope.EVIDENCE_TYPE,
                            FixStrategy.CONVERT_PDF_TO_PDFA,
                            "pdf-a-format-required-error",
                            "Shopify PDF/A required"
                    )
            )
    );

    public IssueDefinition require(String code) {
        IssueDefinition definition = DEFINITIONS.get(code);
        if (definition == null) {
            throw new IllegalArgumentException("unknown validation issue code: " + code);
        }
        return definition;
    }

    public IssueDefinition find(String code) {
        return DEFINITIONS.get(code);
    }

    public record IssueDefinition(
            String ruleId,
            IssueSeverity severity,
            String message,
            IssueTargetScope targetScope,
            FixStrategy fixStrategy,
            String guideSlug,
            String title
    ) {
    }
}
