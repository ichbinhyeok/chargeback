package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.api.ValidationRunReportResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.IssueTargetScope;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.service.EvidenceAliasCatalogService;
import com.example.demo.dispute.service.EvidenceFactsService;
import com.example.demo.dispute.service.EvidenceTextExtractionService;
import com.example.demo.dispute.service.PolicyCatalogService;
import com.example.demo.dispute.service.ReadinessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadinessServiceTest {

    private final ReadinessService service = new ReadinessService(
            new PolicyCatalogService("policy/catalog-v1.json"),
            new EvidenceFactsService(null, new EvidenceTextExtractionService(), new EvidenceAliasCatalogService())
    );

    @Test
    void stripeMissingTypesArePolicyAwareNotFullEnum() {
        CaseReportResponse report = report(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                List.of(file(EvidenceType.ORDER_RECEIPT)),
                null
        );

        ReadinessService.ReadinessSummary summary = service.summarize(report);

        assertFalse(summary.missingEvidenceTypes().contains(EvidenceType.OTHER_SUPPORTING.name()));
        assertTrue(summary.missingEvidenceTypes().contains(EvidenceType.CUSTOMER_DETAILS.name()));
        assertTrue(summary.missingEvidenceTypes().contains(EvidenceType.CUSTOMER_COMMUNICATION.name()));
    }

    @Test
    void shopifyCreditMissingCountFollowsScopePolicy() {
        CaseReportResponse report = report(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_CREDIT_DISPUTE,
                List.of(),
                null
        );

        ReadinessService.ReadinessSummary summary = service.summarize(report);
        assertEquals(5, summary.missingEvidenceTypes().size());
    }

    @Test
    void blockedIssuesLowerScoreAndLabel() {
        ValidationIssueResponse blockedIssue = new ValidationIssueResponse(
                "ERR_TEST_BLOCKED",
                "TST_001",
                IssueSeverity.BLOCKED,
                "blocked issue",
                IssueTargetScope.GLOBAL,
                null,
                null,
                null,
                FixStrategy.MANUAL,
                null,
                null
        );
        ValidationRunReportResponse validation = new ValidationRunReportResponse(
                UUID.randomUUID(),
                1,
                false,
                ValidationSource.STORED_FILES,
                false,
                Instant.now(),
                List.of(blockedIssue)
        );
        CaseReportResponse report = report(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                List.of(file(EvidenceType.ORDER_RECEIPT), file(EvidenceType.CUSTOMER_DETAILS)),
                validation
        );

        ReadinessService.ReadinessSummary summary = service.summarize(report);

        assertTrue(summary.score() < 70);
        assertEquals("Critical", summary.label());
    }

    @Test
    void minimumCoreCoverageRequiresTwoReasonSpecificRequiredTypes() {
        CaseReportResponse thinReport = report(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "PRODUCT_NOT_RECEIVED",
                List.of(file(EvidenceType.ORDER_RECEIPT)),
                null
        );

        assertEquals(2, service.minimumCoreEvidenceTargetCount(thinReport));
        assertEquals(1, service.coreRequiredEvidenceReadyCount(thinReport));
        assertFalse(service.hasMinimumCoreEvidenceCoverage(thinReport));

        CaseReportResponse coveredReport = report(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "PRODUCT_NOT_RECEIVED",
                List.of(file(EvidenceType.ORDER_RECEIPT), file(EvidenceType.CUSTOMER_DETAILS)),
                null
        );

        assertEquals(2, service.minimumCoreEvidenceTargetCount(coveredReport));
        assertEquals(2, service.coreRequiredEvidenceReadyCount(coveredReport));
        assertTrue(service.hasMinimumCoreEvidenceCoverage(coveredReport));
    }

    @Test
    void minimumCoreCoverageDoesNotApplyWithoutReasonSpecificThreeTypePolicy() {
        CaseReportResponse report = report(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                null,
                List.of(file(EvidenceType.ORDER_RECEIPT)),
                null
        );

        assertEquals(0, service.minimumCoreEvidenceTargetCount(report));
        assertTrue(service.hasMinimumCoreEvidenceCoverage(report));
    }

    @Test
    void sharedAnchorsImproveCoherenceScore() {
        CaseReportResponse report = report(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "PRODUCT_NOT_RECEIVED",
                List.of(
                        file(EvidenceType.ORDER_RECEIPT, "invoice_order_A-2042.pdf"),
                        file(EvidenceType.CUSTOMER_COMMUNICATION, "support_order_A-2042_email_buyer@example.com.pdf"),
                        file(EvidenceType.FULFILLMENT_DELIVERY, "tracking_1Z999AA10123456784_order_A-2042.pdf")
                ),
                null
        );

        ReadinessService.ReadinessSummary summary = service.summarize(report);

        assertTrue(summary.coherenceScore() >= 70);
        assertTrue(summary.coherenceHighlights().stream().anyMatch(line -> line.contains("A-2042")));
    }

    private CaseReportResponse report(
            Platform platform,
            ProductScope productScope,
            String reasonCode,
            List<EvidenceFileReportResponse> files,
            ValidationRunReportResponse validation
    ) {
        return new CaseReportResponse(
                UUID.randomUUID(),
                "case_test",
                platform,
                productScope,
                reasonCode,
                null,
                CaseState.READY,
                Instant.now(),
                validation,
                files
        );
    }

    private CaseReportResponse report(
            Platform platform,
            ProductScope productScope,
            List<EvidenceFileReportResponse> files,
            ValidationRunReportResponse validation
    ) {
        return report(platform, productScope, null, files, validation);
    }

    private EvidenceFileReportResponse file(EvidenceType evidenceType) {
        return file(evidenceType, evidenceType.name().toLowerCase() + ".pdf");
    }

    private EvidenceFileReportResponse file(EvidenceType evidenceType, String originalName) {
        return new EvidenceFileReportResponse(
                UUID.randomUUID(),
                evidenceType,
                originalName,
                FileFormat.PDF,
                1200,
                1,
                false,
                true,
                false,
                Instant.now()
        );
    }
}
