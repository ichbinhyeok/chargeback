package com.example.demo.dispute.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EvidenceAliasCatalogService {

    private static final List<String> ORDER_LABEL_ALIASES = List.of(
            "shop order",
            "merchant order",
            "store order",
            "web order",
            "order ref",
            "order reference",
            "merchant ref",
            "merchant reference",
            "booking",
            "reservation",
            "confirmation",
            "confirmation code",
            "invoice",
            "receipt",
            "purchase",
            "ord",
            "order"
    );

    private static final List<String> TRANSACTION_LABEL_ALIASES = List.of(
            "transaction",
            "transaction ref",
            "transaction reference",
            "txn",
            "payment",
            "payment ref",
            "charge"
    );

    private static final List<String> TRACKING_LABEL_ALIASES = List.of(
            "carrier tracking",
            "tracking number",
            "tracking code",
            "tracking ref",
            "tracking reference",
            "tracking",
            "awb",
            "shipment id",
            "shipment",
            "parcel",
            "consignment",
            "waybill"
    );

    private static final List<String> REFUND_LABEL_ALIASES = List.of(
            "refund",
            "refund ref",
            "refund reference",
            "credit",
            "cancellation",
            "cancelled",
            "canceled",
            "void"
    );

    private static final Map<String, List<String>> TRACKING_CARRIER_HINTS = carrierHints();

    public String orderLabelPattern() {
        return aliasPattern(ORDER_LABEL_ALIASES);
    }

    public String transactionLabelPattern() {
        return aliasPattern(TRANSACTION_LABEL_ALIASES);
    }

    public String trackingLabelPattern() {
        return aliasPattern(TRACKING_LABEL_ALIASES);
    }

    public String refundLabelPattern() {
        return aliasPattern(REFUND_LABEL_ALIASES);
    }

    public String inferTrackingCarrier(String signalText, String compactTracking) {
        if (compactTracking == null || compactTracking.isBlank()) {
            return null;
        }
        String normalizedSignal = signalText == null ? "" : signalText.toLowerCase(Locale.ROOT);
        if (compactTracking.startsWith("1Z") && compactTracking.length() == 18) {
            return "UPS";
        }
        if (compactTracking.matches("^(94|93|92|95)\\d{20,24}$")) {
            return "USPS";
        }
        if (compactTracking.matches("^JD\\d{16,20}$")) {
            return "DHL";
        }
        if (compactTracking.matches("^\\d{12}$|^\\d{15}$|^\\d{20}$|^\\d{22}$")
                && containsCarrierHint(normalizedSignal, "FEDEX")) {
            return "FEDEX";
        }
        if (compactTracking.matches("^\\d{20,24}$") && containsCarrierHint(normalizedSignal, "USPS")) {
            return "USPS";
        }
        return null;
    }

    private boolean containsCarrierHint(String normalizedSignal, String carrierCode) {
        return TRACKING_CARRIER_HINTS.getOrDefault(carrierCode, List.of()).stream()
                .anyMatch(normalizedSignal::contains);
    }

    private String aliasPattern(List<String> aliases) {
        return aliases.stream()
                .map(this::toAliasRegex)
                .collect(Collectors.joining("|", "(?:", ")"));
    }

    private String toAliasRegex(String alias) {
        return Arrays.stream(alias.trim().split("\\s+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s*"));
    }

    private static Map<String, List<String>> carrierHints() {
        Map<String, List<String>> hints = new LinkedHashMap<>();
        hints.put("UPS", List.of("ups", "united parcel", "ups ground"));
        hints.put("USPS", List.of("usps", "postal service", "united states postal", "priority mail"));
        hints.put("DHL", List.of("dhl", "dhl express"));
        hints.put("FEDEX", List.of("fedex", "federal express"));
        return Map.copyOf(hints);
    }
}
