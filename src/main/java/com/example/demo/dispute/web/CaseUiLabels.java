package com.example.demo.dispute.web;

import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CaseUiLabels {

    private CaseUiLabels() {
    }

    public static String productScopeLabel(ProductScope scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope) {
            case STRIPE_DISPUTE -> "Stripe dispute upload";
            case SHOPIFY_PAYMENTS_CHARGEBACK -> "Shopify Payments chargeback";
            case SHOPIFY_CREDIT_DISPUTE -> "Shopify credit dispute";
        };
    }

    public static String productScopeDescription(ProductScope scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope) {
            case STRIPE_DISPUTE -> "Uses Stripe dispute evidence rules, duplicate-type limits, and the stricter pack-size checks.";
            case SHOPIFY_PAYMENTS_CHARGEBACK -> "Uses Shopify Payments chargeback rules, including PDF/A and per-file size limits.";
            case SHOPIFY_CREDIT_DISPUTE -> "Uses Shopify credit dispute rules with the lighter 4.5MB total pack limit.";
        };
    }

    public static String platformLabel(Platform platform) {
        if (platform == null) {
            return "";
        }
        return switch (platform) {
            case STRIPE -> "Stripe";
            case SHOPIFY -> "Shopify";
        };
    }

    public static String cardNetworkLabel(CardNetwork network) {
        if (network == null) {
            return "";
        }
        return switch (network) {
            case VISA -> "Visa";
            case MASTERCARD -> "Mastercard";
            case AMEX -> "American Express";
            case DISCOVER -> "Discover";
            case OTHER -> "Other";
        };
    }

    public static String evidenceTypeLabel(EvidenceType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case ORDER_RECEIPT -> "Order receipt / invoice";
            case CUSTOMER_DETAILS -> "Customer details / billing profile";
            case CUSTOMER_COMMUNICATION -> "Customer communication / chat";
            case POLICIES -> "Policy / terms";
            case FULFILLMENT_DELIVERY -> "Delivery proof / tracking";
            case DIGITAL_USAGE_LOGS -> "Usage logs / access records";
            case REFUND_CANCELLATION -> "Refund / cancellation proof";
            case OTHER_SUPPORTING -> "Other supporting evidence";
        };
    }

    public static String evidenceTypeLabel(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        try {
            return evidenceTypeLabel(EvidenceType.valueOf(rawValue));
        } catch (IllegalArgumentException ignored) {
            return titleCase(rawValue);
        }
    }

    public static String joinEvidenceTypeLabels(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return "";
        }
        return rawValues.stream()
                .map(CaseUiLabels::evidenceTypeLabel)
                .collect(Collectors.joining(", "));
    }

    public static String validationSourceLabel(ValidationSource source) {
        if (source == null) {
            return "";
        }
        return switch (source) {
            case REQUEST_FILES -> "this upload attempt";
            case STORED_FILES -> "the current case files";
            case AUTO_FIX -> "the auto-fix output";
        };
    }

    private static String titleCase(String rawValue) {
        String[] parts = rawValue.replace('-', '_').split("_");
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            String normalized = part.toLowerCase(Locale.ROOT);
            text.append(Character.toUpperCase(normalized.charAt(0))).append(normalized.substring(1));
        }
        return text.toString();
    }
}
