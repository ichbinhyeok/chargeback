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
import com.example.demo.dispute.service.PolicyCatalogService;
import com.example.demo.dispute.service.ReadinessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadinessServiceTest {

    private final ReadinessService service = new ReadinessService(
            new PolicyCatalogService("policy/catalog-v1.json")
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
                FixStrategy.MANUAL
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

    private CaseReportResponse report(
            Platform platform,
            ProductScope productScope,
            List<EvidenceFileReportResponse> files,
            ValidationRunReportResponse validation
    ) {
        return new CaseReportResponse(
                UUID.randomUUID(),
                "case_test",
                platform,
                productScope,
                null,
                null,
                CaseState.READY,
                Instant.now(),
                validation,
                files
        );
    }

    private EvidenceFileReportResponse file(EvidenceType evidenceType) {
        return new EvidenceFileReportResponse(
                UUID.randomUUID(),
                evidenceType,
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
