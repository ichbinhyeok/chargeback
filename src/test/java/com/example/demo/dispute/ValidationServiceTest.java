package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.ValidateCaseRequest;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.IssueTargetScope;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.PolicyCatalogService;
import com.example.demo.dispute.service.ValidationIssueContractResolver;
import com.example.demo.dispute.service.ValidationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationServiceTest {

    private final ValidationService validationService = new ValidationService(
            new ValidationIssueContractResolver(),
            new PolicyCatalogService("policy/catalog-v1.json")
    );

    @Test
    void stripePassesWhenRulesSatisfied() {
        DisputeCase disputeCase = newStripeCase(CardNetwork.VISA);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(0.8), 2, false, true, false),
                        jpeg(EvidenceType.FULFILLMENT_DELIVERY, mb(1.1))
                ),
                false
        );

        assertTrue(response.passed());
        assertTrue(response.issues().isEmpty());
    }

    @Test
    void stripeFailsWhenTotalSizeExceeds4_5Mb() {
        DisputeCase disputeCase = newStripeCase(CardNetwork.VISA);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(2.3), 2, false, true, false),
                        pdf(EvidenceType.CUSTOMER_COMMUNICATION, mb(2.3), 2, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_STRIPE_TOTAL_SIZE");
    }

    @Test
    void stripeUsesReasonSpecificTotalLimitFromPolicyCatalog() {
        ValidationService customPolicyService = new ValidationService(
                new ValidationIssueContractResolver(),
                new PolicyCatalogService("policy/catalog-validation-test.json")
        );
        DisputeCase disputeCase = newStripeCase(CardNetwork.OTHER);
        disputeCase.setReasonCode("RC_TIGHT");

        ValidateCaseResponse response = customPolicyService.validate(
                disputeCase,
                List.of(pdf(EvidenceType.ORDER_RECEIPT, 1500, 1, false, true, false)),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_STRIPE_TOTAL_SIZE");
    }

    @Test
    void stripeNetworkRuleOverridesReasonLimitByPrecedence() {
        ValidationService customPolicyService = new ValidationService(
                new ValidationIssueContractResolver(),
                new PolicyCatalogService("policy/catalog-validation-test.json")
        );
        DisputeCase disputeCase = newStripeCase(CardNetwork.VISA);
        disputeCase.setReasonCode("RC_TIGHT");

        ValidateCaseResponse response = customPolicyService.validate(
                disputeCase,
                List.of(pdf(EvidenceType.ORDER_RECEIPT, 1500, 1, false, true, false)),
                false
        );

        assertTrue(response.passed());
    }

    @Test
    void stripeFailsWhenTotalPagesReach50() {
        DisputeCase disputeCase = newStripeCase(CardNetwork.VISA);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(1.0), 30, false, true, false),
                        pdf(EvidenceType.CUSTOMER_COMMUNICATION, mb(1.0), 20, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_STRIPE_TOTAL_PAGES");
    }

    @Test
    void stripeMastercardFailsWhenPagesExceed19() {
        DisputeCase disputeCase = newStripeCase(CardNetwork.MASTERCARD);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(1.0), 10, false, true, false),
                        pdf(EvidenceType.CUSTOMER_COMMUNICATION, mb(1.0), 10, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_STRIPE_MC_19P");
    }

    @Test
    void stripeBlocksExternalLinks() {
        DisputeCase disputeCase = newStripeCase(CardNetwork.VISA);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.POLICIES, mb(0.5), 1, true, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_STRIPE_LINK_DETECTED");
        ValidationIssueResponse issue = response.issues().stream()
                .filter(item -> "ERR_STRIPE_LINK_DETECTED".equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(IssueSeverity.FIXABLE, issue.severity());
        assertEquals(FixStrategy.REMOVE_EXTERNAL_LINKS_PDF, issue.fixStrategy());
    }

    @Test
    void stripeMarksMultipleFilesPerTypeAsFixable() {
        DisputeCase disputeCase = newStripeCase(CardNetwork.VISA);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(0.3), 1, false, true, false),
                        pdf(EvidenceType.ORDER_RECEIPT, mb(0.3), 1, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_STRIPE_MULTI_FILE_PER_TYPE");
        assertContainsSeverity(response, IssueSeverity.FIXABLE);
    }

    @Test
    void shopifyPaymentsFailsWhenTotalExceeds4Mb() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(1.5), 1, false, true, false),
                        pdf(EvidenceType.CUSTOMER_COMMUNICATION, mb(1.5), 1, false, true, false),
                        pdf(EvidenceType.FULFILLMENT_DELIVERY, mb(1.1), 1, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_TOTAL_TOO_LARGE");
    }

    @Test
    void shopifyCreditAllowsTotalAbove4MbAndBelow4_5Mb() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_CREDIT_DISPUTE);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(1.5), 1, false, true, false),
                        pdf(EvidenceType.CUSTOMER_COMMUNICATION, mb(1.5), 1, false, true, false),
                        pdf(EvidenceType.FULFILLMENT_DELIVERY, mb(1.1), 1, false, true, false)
                ),
                false
        );

        assertTrue(response.passed());
    }

    @Test
    void shopifyCreditFailsWhenTotalExceeds4_5Mb() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_CREDIT_DISPUTE);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(1.5), 1, false, true, false),
                        pdf(EvidenceType.CUSTOMER_COMMUNICATION, mb(1.5), 1, false, true, false),
                        pdf(EvidenceType.FULFILLMENT_DELIVERY, mb(1.51), 1, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_CREDIT_TOTAL_TOO_LARGE");
    }

    @Test
    void shopifyCreditDoesNotApplyPaymentsOnlyFileRules() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_CREDIT_DISPUTE);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(2.3), 10, true, false, true),
                        pdf(EvidenceType.ORDER_RECEIPT, mb(0.5), 5, false, false, false)
                ),
                true
        );

        assertTrue(response.passed());
        assertTrue(response.issues().isEmpty());
    }

    @Test
    void shopifyCreditStillBlocksPdfWith50Pages() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_CREDIT_DISPUTE);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(1.0), 50, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_PDF_PAGES_EXCEEDED");
    }

    @Test
    void shopifyFailsWhenSingleFileExceeds2Mb() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(pdf(EvidenceType.ORDER_RECEIPT, mb(2.1), 1, false, true, false)),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_FILE_TOO_LARGE");
    }

    @Test
    void shopifyFailsWhenPdfPageCountReaches50() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(pdf(EvidenceType.ORDER_RECEIPT, mb(0.5), 50, false, true, false)),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_PDF_PAGES_EXCEEDED");
    }

    @Test
    void shopifyBlocksPdfPortfolio() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(pdf(EvidenceType.ORDER_RECEIPT, mb(0.5), 2, false, true, true)),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_PDF_PORTFOLIO");
    }

    @Test
    void shopifyRequiresPdfACompliance() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(pdf(EvidenceType.ORDER_RECEIPT, mb(0.5), 2, false, false, false)),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_PDF_NOT_PDFA");
        assertContainsSeverity(response, IssueSeverity.BLOCKED);
    }

    @Test
    void shopifyAddsWarningForEarlySubmitWithoutBlocking() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(pdf(EvidenceType.ORDER_RECEIPT, mb(0.5), 1, false, true, false)),
                true
        );

        assertTrue(response.passed());
        assertEquals(1, response.issues().size());
        assertContainsCode(response, "WARN_SHPFY_EARLY_SUBMIT");
        assertContainsSeverity(response, IssueSeverity.WARNING);
    }

    @Test
    void shopifyMultipleFilesPerTypeIsFixable() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        pdf(EvidenceType.ORDER_RECEIPT, mb(0.2), 1, false, true, false),
                        pdf(EvidenceType.ORDER_RECEIPT, mb(0.2), 1, false, true, false)
                ),
                false
        );

        assertFalse(response.passed());
        assertContainsCode(response, "ERR_SHPFY_MULTI_FILE_PER_TYPE");
        ValidationIssueResponse issue = response.issues().stream()
                .filter(item -> "ERR_SHPFY_MULTI_FILE_PER_TYPE".equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(IssueTargetScope.EVIDENCE_TYPE, issue.targetScope());
        assertEquals(FixStrategy.MERGE_PER_TYPE, issue.fixStrategy());
    }

    @Test
    void jpegAndPngBypassPdfSpecificChecks() {
        DisputeCase disputeCase = newShopifyCase(ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK);
        ValidateCaseResponse response = validate(
                disputeCase,
                List.of(
                        jpeg(EvidenceType.ORDER_RECEIPT, mb(0.9)),
                        png(EvidenceType.FULFILLMENT_DELIVERY, mb(0.9))
                ),
                false
        );

        assertTrue(response.passed());
    }

    private DisputeCase newShopifyCase(ProductScope productScope) {
        DisputeCase disputeCase = new DisputeCase();
        disputeCase.setPlatform(Platform.SHOPIFY);
        disputeCase.setProductScope(productScope);
        disputeCase.setState(CaseState.CASE_CREATED);
        return disputeCase;
    }

    private DisputeCase newStripeCase(CardNetwork cardNetwork) {
        DisputeCase disputeCase = new DisputeCase();
        disputeCase.setPlatform(Platform.STRIPE);
        disputeCase.setProductScope(ProductScope.STRIPE_DISPUTE);
        disputeCase.setCardNetwork(cardNetwork);
        disputeCase.setState(CaseState.CASE_CREATED);
        return disputeCase;
    }

    private ValidateCaseResponse validate(DisputeCase disputeCase, List<EvidenceFileInput> files, boolean earlySubmit) {
        return validationService.validate(disputeCase, new ValidateCaseRequest(files, earlySubmit));
    }

    private EvidenceFileInput pdf(
            EvidenceType type,
            long sizeBytes,
            int pages,
            boolean externalLinkDetected,
            boolean pdfACompliant,
            boolean pdfPortfolio
    ) {
        return new EvidenceFileInput(
                type,
                FileFormat.PDF,
                sizeBytes,
                pages,
                externalLinkDetected,
                pdfACompliant,
                pdfPortfolio
        );
    }

    private EvidenceFileInput jpeg(EvidenceType type, long sizeBytes) {
        return new EvidenceFileInput(type, FileFormat.JPEG, sizeBytes, 1, false, true, false);
    }

    private EvidenceFileInput png(EvidenceType type, long sizeBytes) {
        return new EvidenceFileInput(type, FileFormat.PNG, sizeBytes, 1, false, true, false);
    }

    private void assertContainsCode(ValidateCaseResponse response, String code) {
        assertTrue(response.issues().stream().map(ValidationIssueResponse::code).anyMatch(code::equals));
    }

    private void assertContainsSeverity(ValidateCaseResponse response, IssueSeverity severity) {
        assertTrue(response.issues().stream().map(ValidationIssueResponse::severity).anyMatch(severity::equals));
    }

    private long mb(double value) {
        return (long) (value * 1024 * 1024);
    }
}
