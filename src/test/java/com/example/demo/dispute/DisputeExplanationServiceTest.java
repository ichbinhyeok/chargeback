package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.service.DisputeExplanationService;
import com.example.demo.dispute.service.EvidenceAliasCatalogService;
import com.example.demo.dispute.service.EvidenceFactsService;
import com.example.demo.dispute.service.EvidenceTextExtractionService;
import com.example.demo.dispute.service.PolicyCatalogService;
import com.example.demo.dispute.service.ReasonCodeChecklistService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisputeExplanationServiceTest {

    private final DisputeExplanationService service = new DisputeExplanationService(
            new ReasonCodeChecklistService(
                    new PolicyCatalogService("policy/catalog-v1.json"),
                    "policy/reason-checklists-v1.json"
            ),
            new EvidenceFactsService(null, new EvidenceTextExtractionService(), new EvidenceAliasCatalogService())
    );

    @Test
    void buildDraftContainsReasonAwareSummaryAndEvidenceIndex() {
        CaseReportResponse report = new CaseReportResponse(
                UUID.randomUUID(),
                "case_test_explanation_1",
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "13.1",
                null,
                CaseState.READY,
                Instant.now(),
                null,
                List.of(
                        file(EvidenceType.ORDER_RECEIPT, FileFormat.PDF, 1200, 1),
                        file(EvidenceType.FULFILLMENT_DELIVERY, FileFormat.PDF, 2000, 2),
                        file(EvidenceType.CUSTOMER_DETAILS, FileFormat.PNG, 1000, 1)
                )
        );

        DisputeExplanationService.ExplanationDraft draft = service.buildDraft(report);

        assertTrue(draft.reasonLabel().toLowerCase().contains("product"));
        assertTrue(draft.canonicalReasonKey().equals("PRODUCT_NOT_RECEIVED"));
        assertTrue(draft.text().contains("Evidence Index"));
        assertTrue(draft.text().contains("FULFILLMENT_DELIVERY"));
        assertTrue(draft.text().contains("Disclaimer: This draft is a submission-writing aid only."));
    }

    @Test
    void buildDraftIncludesMissingRequiredNotesWhenEvidenceGapsExist() {
        CaseReportResponse report = new CaseReportResponse(
                UUID.randomUUID(),
                "case_test_explanation_2",
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK,
                "fraudulent",
                null,
                CaseState.BLOCKED,
                Instant.now(),
                null,
                List.of(
                        file(EvidenceType.ORDER_RECEIPT, FileFormat.PDF, 1200, 1)
                )
        );

        DisputeExplanationService.ExplanationDraft draft = service.buildDraft(report);

        assertFalse(draft.readinessNotes().isEmpty());
        assertTrue(draft.readinessNotes().stream().anyMatch(line -> line.contains("Missing required evidence")));
        assertTrue(draft.text().contains("Checklist Gaps and Actions"));
    }

    @Test
    void buildDraftIncludesEvidenceAnchorReviewAndNarrativeSpine() {
        CaseReportResponse report = new CaseReportResponse(
                UUID.randomUUID(),
                "case_test_explanation_3",
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "13.1",
                null,
                CaseState.READY,
                Instant.now(),
                null,
                List.of(
                        file(EvidenceType.ORDER_RECEIPT, "invoice_order_A-2042.pdf", FileFormat.PDF, 1200, 1),
                        file(EvidenceType.CUSTOMER_COMMUNICATION, "support_order_A-2042_email_buyer@example.com.pdf", FileFormat.PDF, 1800, 1),
                        file(EvidenceType.FULFILLMENT_DELIVERY, "tracking_1Z999AA10123456784_order_A-2042.pdf", FileFormat.PDF, 2000, 1)
                )
        );

        DisputeExplanationService.ExplanationDraft draft = service.buildDraft(report);

        assertFalse(draft.evidenceAnchorReview().isEmpty());
        assertFalse(draft.narrativeSpine().isEmpty());
        assertTrue(draft.text().contains("Evidence Anchor Review"));
        assertTrue(draft.text().contains("Suggested Narrative Spine"));
    }

    private EvidenceFileReportResponse file(EvidenceType type, FileFormat format, long sizeBytes, int pageCount) {
        return file(type, type.name().toLowerCase() + "." + format.name().toLowerCase(), format, sizeBytes, pageCount);
    }

    private EvidenceFileReportResponse file(
            EvidenceType type,
            String originalName,
            FileFormat format,
            long sizeBytes,
            int pageCount
    ) {
        return new EvidenceFileReportResponse(
                UUID.randomUUID(),
                type,
                originalName,
                format,
                sizeBytes,
                pageCount,
                false,
                true,
                false,
                Instant.now()
        );
    }
}
