package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.IssueTargetScope;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.CaseReportService;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.ValidationHistoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ValidationIssuePersistenceIntegrationTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private ValidationHistoryService validationHistoryService;

    @Autowired
    private CaseReportService caseReportService;

    @Test
    void persistedValidationIssueRetainsTargetAndManualFixStrategy() {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK,
                "fraudulent",
                null,
                null
        ));

        try {
            ValidateCaseResponse response = new ValidateCaseResponse(
                    false,
                    List.of(new ValidationIssueResponse(
                            "ERR_SHPFY_FILE_TOO_LARGE",
                            "SHP_SIZE_001",
                            IssueSeverity.BLOCKED,
                            "Each Shopify Payments evidence file must be 2MB or smaller.",
                            IssueTargetScope.EVIDENCE_TYPE,
                            EvidenceType.ORDER_RECEIPT,
                            null,
                            null,
                            FixStrategy.MANUAL,
                            "evidence-file-too-large-2mb",
                            "Shopify evidence file too large"
                    ))
            );

            validationHistoryService.record(
                    disputeCase,
                    response,
                    ValidationSource.STORED_FILES,
                    false,
                    List.of()
            );

            ValidationIssueResponse persistedIssue = caseReportService.getReport(disputeCase.getId())
                    .latestValidation()
                    .issues()
                    .getFirst();

            assertEquals(IssueTargetScope.EVIDENCE_TYPE, persistedIssue.targetScope());
            assertEquals(EvidenceType.ORDER_RECEIPT, persistedIssue.targetEvidenceType());
            assertEquals(FixStrategy.MANUAL, persistedIssue.fixStrategy());
            assertEquals("evidence-file-too-large-2mb", persistedIssue.guideSlug());
            assertEquals("Shopify evidence file too large", persistedIssue.guideTitle());
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }
}
