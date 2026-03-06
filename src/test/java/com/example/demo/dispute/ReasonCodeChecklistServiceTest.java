package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.service.PolicyCatalogService;
import com.example.demo.dispute.service.ReasonCodeChecklistService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasonCodeChecklistServiceTest {

    @Test
    void resolveUsesCanonicalReasonAndPlatformMetadata() {
        ReasonCodeChecklistService service = new ReasonCodeChecklistService(
                new PolicyCatalogService("policy/catalog-reason-alias-test.json"),
                "policy/reason-checklists-test.json"
        );

        ReasonCodeChecklistService.ReasonChecklist checklist = service.resolve(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "13.1",
                null,
                List.of(EvidenceType.ORDER_RECEIPT)
        );

        assertTrue(checklist.hasReasonCode());
        assertEquals("PRODUCT_NOT_RECEIVED", checklist.canonicalReasonKey());
        assertEquals("Stripe - Product not received", checklist.reasonLabel());
        assertEquals(List.of("Fulfillment / delivery proof"), checklist.requiredEvidence());
        assertEquals(List.of("Fulfillment / delivery proof"), checklist.missingRequiredEvidence());
        assertTrue(checklist.weakEvidenceWarnings().stream()
                .anyMatch(item -> item.contains("Missing required evidence")));
        assertTrue(checklist.sourceUrls().stream()
                .anyMatch(url -> url.contains("docs.stripe.com/api/disputes/object")));
    }

    @Test
    void resolveFallsBackToDefaultPlatformSourcesWhenMetadataMissing() {
        ReasonCodeChecklistService service = new ReasonCodeChecklistService(
                new PolicyCatalogService("policy/catalog-reason-alias-test.json"),
                "policy/reason-checklists-test.json"
        );

        ReasonCodeChecklistService.ReasonChecklist checklist = service.resolve(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK,
                "unknown_reason",
                null,
                List.of()
        );

        assertEquals("UNKNOWN_REASON", checklist.canonicalReasonKey());
        assertEquals("Unknown Reason", checklist.reasonLabel());
        assertTrue(checklist.sourceUrls().stream()
                .anyMatch(url -> url.contains("help.shopify.com/en/manual/payments/chargebacks/responding")));
    }

    @Test
    void listReasonOptionsProvidesOrderedPlatformPresets() {
        ReasonCodeChecklistService service = new ReasonCodeChecklistService(
                new PolicyCatalogService("policy/catalog-reason-alias-test.json"),
                "policy/reason-checklists-test.json"
        );

        List<ReasonCodeChecklistService.ReasonOption> stripeOptions = service.listReasonOptions(Platform.STRIPE);

        assertEquals(8, stripeOptions.size());
        assertEquals("FRAUDULENT", stripeOptions.get(0).code());
        assertEquals("PRODUCT_NOT_RECEIVED", stripeOptions.get(2).code());
        assertTrue(stripeOptions.get(2).hint().contains("13.1"));
    }
}
