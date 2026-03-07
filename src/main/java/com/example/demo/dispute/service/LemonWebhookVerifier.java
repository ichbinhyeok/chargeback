package com.example.demo.dispute.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LemonWebhookVerifier {

    public boolean isValid(String payload, String signatureHeader, String webhookSecret) {
        if (payload == null || payload.isBlank() || signatureHeader == null || signatureHeader.isBlank()
                || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        String computed = hmacSha256Hex(webhookSecret, payload);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
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
            throw new IllegalStateException("failed to verify lemon webhook signature", ex);
        }
    }
}
