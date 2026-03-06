package com.example.demo.dispute.service;

import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReasonCodeChecklistService {

    private static final List<String> REASON_ORDER = List.of(
            "FRAUDULENT",
            "UNRECOGNIZED",
            "PRODUCT_NOT_RECEIVED",
            "PRODUCT_UNACCEPTABLE",
            "CREDIT_NOT_PROCESSED",
            "SUBSCRIPTION_CANCELED",
            "DUPLICATE",
            "GENERAL"
    );

    private final PolicyCatalogService policyCatalogService;
    private final ChecklistCatalog checklistCatalog;

    public ReasonCodeChecklistService(
            PolicyCatalogService policyCatalogService,
            @Value("${app.policy.reason-checklist-path:policy/reason-checklists-v1.json}") String checklistPath
    ) {
        this.policyCatalogService = policyCatalogService;
        this.checklistCatalog = loadCatalog(new ObjectMapper(), checklistPath);
    }

    public ReasonChecklist resolve(
            Platform platform,
            ProductScope productScope,
            String reasonCode,
            CardNetwork cardNetwork,
            List<EvidenceType> presentEvidenceTypes
    ) {
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                platform,
                productScope,
                reasonCode,
                cardNetwork
        );
        String canonicalReasonKey = policy.canonicalReasonKey();
        ChecklistNode metadata = resolveMetadata(platform, canonicalReasonKey);

        EnumSet<EvidenceType> present = EnumSet.noneOf(EvidenceType.class);
        if (presentEvidenceTypes != null) {
            present.addAll(presentEvidenceTypes);
        }

        List<String> requiredEvidence = evidenceLabels(policy.requiredEvidenceTypes());
        List<String> recommendedEvidence = evidenceLabels(policy.recommendedEvidenceTypes());
        List<String> missingRequired = missingEvidence(policy.requiredEvidenceTypes(), present);
        List<String> missingRecommended = missingEvidence(policy.recommendedEvidenceTypes(), present);

        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (!missingRequired.isEmpty()) {
            warnings.add("Missing required evidence: " + String.join(", ", missingRequired) + ".");
        }
        if (!missingRecommended.isEmpty()) {
            warnings.add("Missing recommended evidence: " + String.join(", ", missingRecommended) + ".");
        }
        if (metadata != null && metadata.weakEvidenceWarnings() != null) {
            warnings.addAll(metadata.weakEvidenceWarnings());
        }

        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (metadata != null && metadata.priorityActions() != null) {
            actions.addAll(metadata.priorityActions());
        }

        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (metadata != null && metadata.sourceUrls() != null) {
            sources.addAll(metadata.sourceUrls());
        }
        if (sources.isEmpty()) {
            sources.addAll(defaultSources(platform));
        }

        String reasonInput = reasonCode == null ? null : reasonCode.trim();
        return new ReasonChecklist(
                reasonInput == null || reasonInput.isBlank() ? null : reasonInput,
                canonicalReasonKey,
                metadata != null && metadata.label() != null && !metadata.label().isBlank()
                        ? metadata.label()
                        : toReasonLabel(canonicalReasonKey),
                requiredEvidence,
                recommendedEvidence,
                missingRequired,
                missingRecommended,
                List.copyOf(warnings),
                List.copyOf(actions),
                List.copyOf(sources)
        );
    }

    public List<ReasonOption> listReasonOptions(Platform platform) {
        if (platform == null) {
            return List.of();
        }

        List<ReasonOption> options = new ArrayList<>();
        for (String canonicalReasonKey : REASON_ORDER) {
            ChecklistNode metadata = resolveMetadata(platform, canonicalReasonKey);
            String label = metadata != null && metadata.label() != null && !metadata.label().isBlank()
                    ? metadata.label()
                    : toReasonLabel(canonicalReasonKey);
            options.add(new ReasonOption(
                    canonicalReasonKey,
                    label,
                    reasonHint(platform, canonicalReasonKey)
            ));
        }
        return List.copyOf(options);
    }

    private ChecklistCatalog loadCatalog(ObjectMapper objectMapper, String checklistPath) {
        ClassPathResource resource = new ClassPathResource(checklistPath);
        try (InputStream in = resource.getInputStream()) {
            ChecklistCatalog parsed = objectMapper.readValue(in, ChecklistCatalog.class);
            if (parsed.version() == null || parsed.version().isBlank()) {
                throw new IllegalStateException("reason checklist version is missing");
            }
            return parsed;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load reason checklist catalog: " + checklistPath, ex);
        }
    }

    private ChecklistNode resolveMetadata(Platform platform, String canonicalReasonKey) {
        if (checklistCatalog.reasons() == null || checklistCatalog.reasons().isEmpty()) {
            return null;
        }
        String platformReasonKey = platformReasonKey(platform, canonicalReasonKey);
        if (platformReasonKey != null) {
            ChecklistNode platformNode = checklistCatalog.reasons().get(platformReasonKey);
            if (platformNode != null) {
                return platformNode;
            }
        }
        if (canonicalReasonKey == null || canonicalReasonKey.isBlank()) {
            return null;
        }
        return checklistCatalog.reasons().get(canonicalReasonKey);
    }

    private String platformReasonKey(Platform platform, String canonicalReasonKey) {
        if (platform == null || canonicalReasonKey == null || canonicalReasonKey.isBlank()) {
            return null;
        }
        return platform.name() + ":" + canonicalReasonKey;
    }

    private List<String> evidenceLabels(List<EvidenceType> evidenceTypes) {
        if (evidenceTypes == null || evidenceTypes.isEmpty()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(evidenceTypes.size());
        for (EvidenceType type : evidenceTypes) {
            labels.add(evidenceLabel(type));
        }
        return List.copyOf(labels);
    }

    private List<String> missingEvidence(List<EvidenceType> evidenceTypes, Set<EvidenceType> present) {
        if (evidenceTypes == null || evidenceTypes.isEmpty()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (EvidenceType type : evidenceTypes) {
            if (!present.contains(type)) {
                labels.add(evidenceLabel(type));
            }
        }
        return List.copyOf(labels);
    }

    private String evidenceLabel(EvidenceType type) {
        return switch (type) {
            case ORDER_RECEIPT -> "Order receipt / invoice";
            case CUSTOMER_DETAILS -> "Customer details";
            case CUSTOMER_COMMUNICATION -> "Customer communication";
            case POLICIES -> "Policy / terms evidence";
            case FULFILLMENT_DELIVERY -> "Fulfillment / delivery proof";
            case DIGITAL_USAGE_LOGS -> "Digital usage or access logs";
            case REFUND_CANCELLATION -> "Refund / cancellation records";
            case OTHER_SUPPORTING -> "Other supporting evidence";
        };
    }

    private String toReasonLabel(String canonicalReasonKey) {
        if (canonicalReasonKey == null || canonicalReasonKey.isBlank()) {
            return "Reason code not set";
        }
        String[] parts = canonicalReasonKey.toLowerCase().split("_");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                label.append(part.substring(1));
            }
        }
        return label.toString();
    }

    private List<String> defaultSources(Platform platform) {
        if (platform == Platform.STRIPE) {
            return List.of(
                    "https://docs.stripe.com/disputes/reason-codes-defense-requirements",
                    "https://docs.stripe.com/api/disputes/object"
            );
        }
        if (platform == Platform.SHOPIFY) {
            return List.of(
                    "https://help.shopify.com/en/manual/payments/chargebacks/responding",
                    "https://help.shopify.com/en/manual/payments/chargebacks/resolve-chargeback"
            );
        }
        return List.of();
    }

    private String reasonHint(Platform platform, String canonicalReasonKey) {
        if (platform == Platform.STRIPE) {
            return switch (canonicalReasonKey) {
                case "PRODUCT_NOT_RECEIVED" -> "Common aliases: 13.1, 4855";
                case "PRODUCT_UNACCEPTABLE" -> "Common aliases: 13.3, 4853";
                case "CREDIT_NOT_PROCESSED" -> "Common aliases: 13.6, 4860";
                case "SUBSCRIPTION_CANCELED" -> "Common aliases: 13.7, 4841";
                case "DUPLICATE" -> "Common aliases: 12.6.x, 4834";
                case "FRAUDULENT" -> "Common aliases: 10.3, 10.4, 4837";
                default -> "Canonical reason key";
            };
        }
        if (platform == Platform.SHOPIFY) {
            return switch (canonicalReasonKey) {
                case "PRODUCT_NOT_RECEIVED" -> "Also used as item_not_received";
                case "PRODUCT_UNACCEPTABLE" -> "Also used as not_as_described";
                case "SUBSCRIPTION_CANCELED" -> "Also used as canceled_order";
                default -> "Canonical reason key";
            };
        }
        return "Canonical reason key";
    }

    private record ChecklistCatalog(
            String version,
            Map<String, ChecklistNode> reasons
    ) {
    }

    private record ChecklistNode(
            String label,
            List<String> weakEvidenceWarnings,
            List<String> priorityActions,
            List<String> sourceUrls
    ) {
    }

    public record ReasonChecklist(
            String reasonInput,
            String canonicalReasonKey,
            String reasonLabel,
            List<String> requiredEvidence,
            List<String> recommendedEvidence,
            List<String> missingRequiredEvidence,
            List<String> missingRecommendedEvidence,
            List<String> weakEvidenceWarnings,
            List<String> priorityActions,
            List<String> sourceUrls
    ) {
        public boolean hasReasonCode() {
            return canonicalReasonKey != null && !canonicalReasonKey.isBlank();
        }
    }

    public record ReasonOption(
            String code,
            String label,
            String hint
    ) {
    }
}
