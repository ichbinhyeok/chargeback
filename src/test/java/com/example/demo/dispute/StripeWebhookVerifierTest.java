package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.service.StripeWebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class StripeWebhookVerifierTest {

    private final StripeWebhookVerifier verifier = new StripeWebhookVerifier();

    @Test
    void acceptsValidSignature() throws Exception {
        String secret = "whsec_test_secret";
        String payload = "{\"type\":\"checkout.session.completed\"}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = hmac(secret, timestamp + "." + payload);
        String header = "t=" + timestamp + ",v1=" + signature;

        assertTrue(verifier.isValid(payload, header, secret));
    }

    @Test
    void rejectsInvalidSignature() {
        String secret = "whsec_test_secret";
        String payload = "{\"type\":\"checkout.session.completed\"}";
        long timestamp = Instant.now().getEpochSecond();
        String header = "t=" + timestamp + ",v1=invalid";

        assertFalse(verifier.isValid(payload, header, secret));
    }

    private String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
