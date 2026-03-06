package com.example.demo.dispute.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
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

        ParsedStripeSignature signature = parseSignatureHeader(signatureHeader);
        String timestamp = signature.timestamp();
        if (timestamp == null || signature.v1Signatures().isEmpty()) {
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
        byte[] computedBytes = computed.getBytes(StandardCharsets.UTF_8);
        for (String candidate : signature.v1Signatures()) {
            if (MessageDigest.isEqual(computedBytes, candidate.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private ParsedStripeSignature parseSignatureHeader(String signatureHeader) {
        Map<String, String> values = new HashMap<>();
        List<String> v1Signatures = new ArrayList<>();
        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();
                values.put(key, value);
                if ("v1".equals(key) && !value.isBlank()) {
                    v1Signatures.add(value);
                }
            }
        }
        return new ParsedStripeSignature(values.get("t"), List.copyOf(v1Signatures));
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

    private record ParsedStripeSignature(String timestamp, List<String> v1Signatures) {
    }
}
