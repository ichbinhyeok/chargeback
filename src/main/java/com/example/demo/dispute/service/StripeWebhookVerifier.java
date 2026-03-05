package com.example.demo.dispute.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class StripeWebhookVerifier {

    private static final long DEFAULT_TOLERANCE_SECONDS = 300L;

    public boolean isValid(String payload, String signatureHeader, String webhookSecret) {
        if (payload == null || signatureHeader == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        Map<String, String> parts = parseSignatureHeader(signatureHeader);
        String timestamp = parts.get("t");
        String expectedSignature = parts.get("v1");
        if (timestamp == null || expectedSignature == null) {
            return false;
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return false;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestampSeconds) > DEFAULT_TOLERANCE_SECONDS) {
            return false;
        }

        String signedPayload = timestamp + "." + payload;
        String computed = hmacSha256Hex(webhookSecret, signedPayload);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Map<String, String> parseSignatureHeader(String signatureHeader) {
        Map<String, String> values = new HashMap<>();
        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                values.put(kv[0].trim(), kv[1].trim());
            }
        }
        return values;
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
            throw new IllegalStateException("failed to verify stripe signature", ex);
        }
    }
}
