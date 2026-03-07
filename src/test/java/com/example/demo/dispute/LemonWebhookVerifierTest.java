package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.service.LemonWebhookVerifier;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LemonWebhookVerifierTest {

    private final LemonWebhookVerifier verifier = new LemonWebhookVerifier();

    @Test
    void acceptsValidSignature() {
        String secret = "test_secret_123456";
        String payload = "{\"meta\":{\"event_name\":\"order_created\"}}";
        String signature = hmacSha256Hex(secret, payload);

        assertTrue(verifier.isValid(payload, signature, secret));
    }

    @Test
    void rejectsInvalidSignature() {
        String secret = "test_secret_123456";
        String payload = "{\"meta\":{\"event_name\":\"order_created\"}}";

        assertFalse(verifier.isValid(payload, "deadbeef", secret));
    }

    @Test
    void rejectsMissingInputs() {
        assertFalse(verifier.isValid(null, "sig", "secret"));
        assertFalse(verifier.isValid("{}", null, "secret"));
        assertFalse(verifier.isValid("{}", "sig", ""));
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
