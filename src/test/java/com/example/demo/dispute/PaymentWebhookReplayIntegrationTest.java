package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.PaymentStatus;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.AuditLogRepository;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.PaymentEntity;
import com.example.demo.dispute.persistence.PaymentRepository;
import com.example.demo.dispute.persistence.WebhookEventReceiptRepository;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.billing.stripe.webhook-secret=whsec_replay_test",
        "app.billing.lemonsqueezy.webhook-secret=lemon_replay_test"
})
class PaymentWebhookReplayIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WebhookEventReceiptRepository webhookEventReceiptRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void stripeDuplicateWebhookIsProcessedOnlyOnce() throws Exception {
        DisputeCase disputeCase = createReadyCase("stripe_webhook_replay");
        try {
            PaymentEntity payment = createPayment(disputeCase, "stripe", "cs_test_replay_01");
            String payload = stripePayload("evt_replay_01", payment.getCheckoutSessionId(), "pi_test_replay_01", "merchant@example.com");
            String signature = stripeSignature("whsec_replay_test", payload);

            paymentService.processStripeWebhook(payload, signature);
            paymentService.processStripeWebhook(payload, signature);

            PaymentEntity saved = paymentRepository.findById(payment.getId()).orElseThrow();
            assertEquals(PaymentStatus.PAID, saved.getStatus());
            assertNotNull(saved.getPaidAt());
            assertEquals(CaseState.PAID, caseService.getCase(disputeCase.getId()).getState());
            assertEquals(
                    1L,
                    webhookEventReceiptRepository.countByProviderAndEventTypeAndEventId(
                            "stripe",
                            "checkout.session.completed",
                            "evt_replay_01"
                    )
            );
            assertEquals(
                    1L,
                    auditLogRepository.countByDisputeCaseIdAndAction(disputeCase.getId(), "PAYMENT_COMPLETED")
            );
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void stripeUnknownSessionDoesNotConsumeReplayReceipt() throws Exception {
        String payload = stripePayload("evt_unknown_01", "cs_test_unknown_01", "pi_test_unknown_01", "merchant@example.com");
        String signature = stripeSignature("whsec_replay_test", payload);

        paymentService.processStripeWebhook(payload, signature);

        assertEquals(
                0L,
                webhookEventReceiptRepository.countByProviderAndEventTypeAndEventId(
                        "stripe",
                        "checkout.session.completed",
                        "evt_unknown_01"
                )
        );

        DisputeCase disputeCase = createReadyCase("stripe_webhook_unknown_session");
        try {
            PaymentEntity payment = createPayment(disputeCase, "stripe", "cs_test_unknown_01");

            paymentService.processStripeWebhook(payload, signature);

            PaymentEntity saved = paymentRepository.findById(payment.getId()).orElseThrow();
            assertEquals(PaymentStatus.PAID, saved.getStatus());
            assertEquals(
                    1L,
                    webhookEventReceiptRepository.countByProviderAndEventTypeAndEventId(
                            "stripe",
                            "checkout.session.completed",
                            "evt_unknown_01"
                    )
            );
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    @Test
    void lemonDuplicateWebhookIsProcessedOnlyOnce() throws Exception {
        DisputeCase disputeCase = createReadyCase("lemon_webhook_replay");
        try {
            PaymentEntity payment = createPayment(disputeCase, "lemonsqueezy", "lemon_checkout_replay_01");
            String payload = lemonPayload(disputeCase.getCaseToken(), "order_12345", "merchant@example.com");
            String signature = hmacSha256Hex("lemon_replay_test", payload);

            paymentService.processLemonWebhook(payload, signature);
            paymentService.processLemonWebhook(payload, signature);

            PaymentEntity saved = paymentRepository.findById(payment.getId()).orElseThrow();
            assertEquals(PaymentStatus.PAID, saved.getStatus());
            assertNotNull(saved.getPaidAt());
            assertEquals(CaseState.PAID, caseService.getCase(disputeCase.getId()).getState());
            assertEquals(
                    1L,
                    webhookEventReceiptRepository.countByProviderAndEventTypeAndEventId(
                            "lemonsqueezy",
                            "order_created",
                            "order_12345"
                    )
            );
            assertEquals(
                    1L,
                    auditLogRepository.countByDisputeCaseIdAndAction(disputeCase.getId(), "PAYMENT_COMPLETED")
            );
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }

    private DisputeCase createReadyCase(String reasonCode) {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.STRIPE,
                ProductScope.STRIPE_DISPUTE,
                reasonCode,
                null,
                null
        ));
        caseService.transitionState(disputeCase, CaseState.UPLOADING);
        caseService.transitionState(disputeCase, CaseState.VALIDATING);
        return caseService.transitionState(disputeCase, CaseState.READY);
    }

    private PaymentEntity createPayment(DisputeCase disputeCase, String provider, String checkoutSessionId) {
        PaymentEntity payment = new PaymentEntity();
        payment.setDisputeCase(disputeCase);
        payment.setProvider(provider);
        payment.setCheckoutSessionId(checkoutSessionId);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setAmountCents(1900L);
        payment.setCurrency("usd");
        return paymentRepository.save(payment);
    }

    private String stripePayload(String eventId, String sessionId, String paymentIntentId, String email) {
        return """
                {
                  "id": "%s",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "%s",
                      "payment_intent": "%s",
                      "customer_details": {
                        "email": "%s"
                      }
                    }
                  }
                }
                """.formatted(eventId, sessionId, paymentIntentId, email);
    }

    private String stripeSignature(String secret, String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String digest = hmacSha256Hex(secret, timestamp + "." + payload);
        return "t=" + timestamp + ",v1=" + digest;
    }

    private String lemonPayload(String caseToken, String orderId, String email) {
        return """
                {
                  "meta": {
                    "event_name": "order_created",
                    "custom_data": {
                      "case_token": "%s"
                    }
                  },
                  "data": {
                    "id": "%s",
                    "attributes": {
                      "user_email": "%s"
                    }
                  }
                }
                """.formatted(caseToken, orderId, email);
    }

    private String hmacSha256Hex(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
