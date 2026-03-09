package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.service.PolicyCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyCatalogServiceTest {

    @Test
    void resolveAppliesPrecedenceGlobalToNetwork() {
        PolicyCatalogService service = new PolicyCatalogService("policy/catalog-test.json");

        PolicyCatalogService.ResolvedPolicy policy = service.resolve(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "rc_1",
                CardNetwork.VISA
        );

        assertEquals("test.v1", policy.policyVersion());
        assertEquals("RC_1", policy.canonicalReasonKey());
        assertEquals(1, policy.requiredEvidenceTypes().size());
        assertEquals(EvidenceType.FULFILLMENT_DELIVERY, policy.requiredEvidenceTypes().get(0));
        assertEquals(5000L, policy.totalSizeLimitBytes());
        assertEquals(List.of(FileFormat.PDF), policy.allowedFormats());
        assertTrue(policy.singleFilePerEvidenceType());
        assertFalse(policy.externalLinksAllowed());
        assertEquals(10, policy.perPdfPageLimit());
        assertEquals(49, policy.totalPagesLimit());
        assertEquals(
                "platform=STRIPE|scope=STRIPE_DISPUTE|reason=RC_1|network=VISA",
                policy.contextKey()
        );
    }

    @Test
    void resolveNormalizesAliasesAndAppliesPlatformReasonOverride() {
        PolicyCatalogService service = new PolicyCatalogService("policy/catalog-reason-alias-test.json");

        PolicyCatalogService.ResolvedPolicy stripePolicy = service.resolve(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                "13.1",
                null
        );
        PolicyCatalogService.ResolvedPolicy shopifyPolicy = service.resolve(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK,
                "13.1",
                null
        );

        assertEquals("PRODUCT_NOT_RECEIVED", stripePolicy.canonicalReasonKey());
        assertEquals("PRODUCT_NOT_RECEIVED", shopifyPolicy.canonicalReasonKey());
        assertEquals(List.of(EvidenceType.FULFILLMENT_DELIVERY), stripePolicy.requiredEvidenceTypes());
        assertEquals(List.of(EvidenceType.ORDER_RECEIPT), shopifyPolicy.requiredEvidenceTypes());
        assertEquals(
                "platform=STRIPE|scope=STRIPE_DISPUTE|reason=PRODUCT_NOT_RECEIVED|network=-",
                stripePolicy.contextKey()
        );
    }
}
