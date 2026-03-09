package com.example.demo.dispute.service;

import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyCatalogService {

    private static final Map<String, String> REASON_ALIASES = Map.ofEntries(
            Map.entry("PRODUCT_NOT_RECEIVED", "PRODUCT_NOT_RECEIVED"),
            Map.entry("ITEM_NOT_RECEIVED", "PRODUCT_NOT_RECEIVED"),
            Map.entry("SERVICE_NOT_RENDERED", "PRODUCT_NOT_RECEIVED"),
            Map.entry("13_1", "PRODUCT_NOT_RECEIVED"),
            Map.entry("4855", "PRODUCT_NOT_RECEIVED"),
            Map.entry("C08", "PRODUCT_NOT_RECEIVED"),
            Map.entry("PRODUCT_UNACCEPTABLE", "PRODUCT_UNACCEPTABLE"),
            Map.entry("NOT_AS_DESCRIBED", "PRODUCT_UNACCEPTABLE"),
            Map.entry("ITEM_NOT_AS_DESCRIBED", "PRODUCT_UNACCEPTABLE"),
            Map.entry("NOT_ACCEPTABLE", "PRODUCT_UNACCEPTABLE"),
            Map.entry("13_3", "PRODUCT_UNACCEPTABLE"),
            Map.entry("4853", "PRODUCT_UNACCEPTABLE"),
            Map.entry("C31", "PRODUCT_UNACCEPTABLE"),
            Map.entry("CREDIT_NOT_PROCESSED", "CREDIT_NOT_PROCESSED"),
            Map.entry("13_6", "CREDIT_NOT_PROCESSED"),
            Map.entry("4860", "CREDIT_NOT_PROCESSED"),
            Map.entry("C02", "CREDIT_NOT_PROCESSED"),
            Map.entry("SUBSCRIPTION_CANCELED", "SUBSCRIPTION_CANCELED"),
            Map.entry("SUBSCRIPTION_CANCELLED", "SUBSCRIPTION_CANCELED"),
            Map.entry("CANCELED_ORDER", "SUBSCRIPTION_CANCELED"),
            Map.entry("CANCELLED_ORDER", "SUBSCRIPTION_CANCELED"),
            Map.entry("13_7", "SUBSCRIPTION_CANCELED"),
            Map.entry("4841", "SUBSCRIPTION_CANCELED"),
            Map.entry("C05", "SUBSCRIPTION_CANCELED"),
            Map.entry("DUPLICATE", "DUPLICATE"),
            Map.entry("DUPLICATE_TRANSACTION", "DUPLICATE"),
            Map.entry("DUPLICATE_PROCESSING", "DUPLICATE"),
            Map.entry("12_6_1", "DUPLICATE"),
            Map.entry("12_6_2", "DUPLICATE"),
            Map.entry("12_6_3", "DUPLICATE"),
            Map.entry("4834", "DUPLICATE"),
            Map.entry("P08", "DUPLICATE"),
            Map.entry("C14", "DUPLICATE"),
            Map.entry("FRAUDULENT", "FRAUDULENT"),
            Map.entry("FRAUD", "FRAUDULENT"),
            Map.entry("UNAUTHORIZED", "FRAUDULENT"),
            Map.entry("10_3", "FRAUDULENT"),
            Map.entry("10_4", "FRAUDULENT"),
            Map.entry("4837", "FRAUDULENT"),
            Map.entry("F10", "FRAUDULENT"),
            Map.entry("UNRECOGNIZED", "UNRECOGNIZED"),
            Map.entry("UNRECOGNISED", "UNRECOGNIZED"),
            Map.entry("GENERAL", "GENERAL")
    );

    private final Catalog catalog;

    public PolicyCatalogService(@Value("${app.policy.catalog-path:policy/catalog-v1.json}") String catalogPath) {
        this.catalog = loadCatalog(new ObjectMapper(), catalogPath);
    }

    public ResolvedPolicy resolve(
            Platform platform,
            ProductScope productScope,
            String reasonCode,
            CardNetwork cardNetwork
    ) {
        String canonicalReasonKey = normalizeReasonKey(reasonCode);
        PolicyNode merged = emptyNode();
        merged = merge(merged, catalog.defaults());
        merged = merge(merged, find(catalog.platform(), nameOrNull(platform)));
        merged = merge(merged, find(catalog.scope(), nameOrNull(productScope)));
        merged = merge(merged, find(catalog.reason(), canonicalReasonKey));
        merged = merge(merged, find(catalog.reason(), buildPlatformReasonKey(platform, canonicalReasonKey)));
        merged = merge(merged, find(catalog.network(), nameOrNull(cardNetwork)));

        CoverageNode coverage = merged.coverage() == null
                ? new CoverageNode(List.of(), List.of())
                : merged.coverage();
        RulesNode rules = merged.rules() == null
                ? RulesNode.empty()
                : merged.rules();

        return new ResolvedPolicy(
                catalog.version(),
                buildContextKey(platform, productScope, canonicalReasonKey, cardNetwork),
                canonicalReasonKey,
                coverage.required() == null ? List.of() : List.copyOf(coverage.required()),
                coverage.recommended() == null ? List.of() : List.copyOf(coverage.recommended()),
                new ResolvedRules(
                        rules.allowedFormats() == null ? List.of() : List.copyOf(rules.allowedFormats()),
                        rules.singleFilePerEvidenceType(),
                        rules.externalLinksAllowed(),
                        rules.totalSizeLimitBytes(),
                        rules.perFileSizeLimitBytes(),
                        rules.perImageFileSizeLimitBytes(),
                        rules.totalPagesLimit(),
                        rules.perPdfPageLimit(),
                        rules.pdfARequired(),
                        rules.portfolioPdfAllowed()
                )
        );
    }

    private Catalog loadCatalog(ObjectMapper objectMapper, String catalogPath) {
        ClassPathResource resource = new ClassPathResource(catalogPath);
        try (InputStream in = resource.getInputStream()) {
            Catalog parsed = objectMapper.readValue(in, Catalog.class);
            if (parsed.version() == null || parsed.version().isBlank()) {
                throw new IllegalStateException("policy catalog version is missing");
            }
            return parsed;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load policy catalog: " + catalogPath, ex);
        }
    }

    private PolicyNode emptyNode() {
        return new PolicyNode(new CoverageNode(List.of(), List.of()), RulesNode.empty());
    }

    private PolicyNode merge(PolicyNode base, PolicyNode override) {
        if (override == null) {
            return base;
        }
        CoverageNode mergedCoverage = mergeCoverage(base.coverage(), override.coverage());
        RulesNode mergedRules = mergeRules(base.rules(), override.rules());
        return new PolicyNode(mergedCoverage, mergedRules);
    }

    private CoverageNode mergeCoverage(CoverageNode base, CoverageNode override) {
        if (base == null && override == null) {
            return null;
        }
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }
        return new CoverageNode(
                override.required() != null ? override.required() : base.required(),
                override.recommended() != null ? override.recommended() : base.recommended()
        );
    }

    private RulesNode mergeRules(RulesNode base, RulesNode override) {
        if (base == null && override == null) {
            return null;
        }
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }
        return new RulesNode(
                override.allowedFormats() != null ? override.allowedFormats() : base.allowedFormats(),
                override.singleFilePerEvidenceType() != null
                        ? override.singleFilePerEvidenceType()
                        : base.singleFilePerEvidenceType(),
                override.externalLinksAllowed() != null
                        ? override.externalLinksAllowed()
                        : base.externalLinksAllowed(),
                override.totalSizeLimitBytes() != null
                        ? override.totalSizeLimitBytes()
                        : base.totalSizeLimitBytes(),
                override.perFileSizeLimitBytes() != null
                        ? override.perFileSizeLimitBytes()
                        : base.perFileSizeLimitBytes(),
                override.perImageFileSizeLimitBytes() != null
                        ? override.perImageFileSizeLimitBytes()
                        : base.perImageFileSizeLimitBytes(),
                override.totalPagesLimit() != null
                        ? override.totalPagesLimit()
                        : base.totalPagesLimit(),
                override.perPdfPageLimit() != null
                        ? override.perPdfPageLimit()
                        : base.perPdfPageLimit(),
                override.pdfARequired() != null
                        ? override.pdfARequired()
                        : base.pdfARequired(),
                override.portfolioPdfAllowed() != null
                        ? override.portfolioPdfAllowed()
                        : base.portfolioPdfAllowed()
        );
    }

    private PolicyNode find(Map<String, PolicyNode> source, String key) {
        if (source == null || key == null || key.isBlank()) {
            return null;
        }
        return source.get(key);
    }

    private String buildContextKey(Platform platform, ProductScope scope, String canonicalReasonKey, CardNetwork network) {
        return "platform=" + normalizeContextValue(nameOrNull(platform))
                + "|scope=" + normalizeContextValue(nameOrNull(scope))
                + "|reason=" + normalizeContextValue(canonicalReasonKey)
                + "|network=" + normalizeContextValue(nameOrNull(network));
    }

    private String normalizeContextValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String normalizeReasonKey(String reasonCode) {
        String normalized = normalizeToken(reasonCode);
        if (normalized == null) {
            return null;
        }
        return REASON_ALIASES.getOrDefault(normalized, normalized);
    }

    private String normalizeToken(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        StringBuilder normalized = new StringBuilder();
        boolean previousUnderscore = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                normalized.append(Character.toUpperCase(ch));
                previousUnderscore = false;
                continue;
            }
            if (!previousUnderscore) {
                normalized.append('_');
                previousUnderscore = true;
            }
        }
        while (!normalized.isEmpty() && normalized.charAt(0) == '_') {
            normalized.deleteCharAt(0);
        }
        while (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) == '_') {
            normalized.deleteCharAt(normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toString();
    }

    private String buildPlatformReasonKey(Platform platform, String canonicalReasonKey) {
        if (platform == null || canonicalReasonKey == null || canonicalReasonKey.isBlank()) {
            return null;
        }
        return platform.name() + ":" + canonicalReasonKey;
    }

    private record Catalog(
            String version,
            PolicyNode defaults,
            Map<String, PolicyNode> platform,
            Map<String, PolicyNode> scope,
            Map<String, PolicyNode> reason,
            Map<String, PolicyNode> network
    ) {
    }

    private record PolicyNode(CoverageNode coverage, RulesNode rules) {
    }

    private record CoverageNode(List<EvidenceType> required, List<EvidenceType> recommended) {
    }

    private record RulesNode(
            List<FileFormat> allowedFormats,
            Boolean singleFilePerEvidenceType,
            Boolean externalLinksAllowed,
            Long totalSizeLimitBytes,
            Long perFileSizeLimitBytes,
            Long perImageFileSizeLimitBytes,
            Integer totalPagesLimit,
            Integer perPdfPageLimit,
            Boolean pdfARequired,
            Boolean portfolioPdfAllowed
    ) {
        private static RulesNode empty() {
            return new RulesNode(null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record ResolvedRules(
            List<FileFormat> allowedFormats,
            Boolean singleFilePerEvidenceType,
            Boolean externalLinksAllowed,
            Long totalSizeLimitBytes,
            Long perFileSizeLimitBytes,
            Long perImageFileSizeLimitBytes,
            Integer totalPagesLimit,
            Integer perPdfPageLimit,
            Boolean pdfARequired,
            Boolean portfolioPdfAllowed
    ) {
    }

    public record ResolvedPolicy(
            String policyVersion,
            String contextKey,
            String canonicalReasonKey,
            List<EvidenceType> requiredEvidenceTypes,
            List<EvidenceType> recommendedEvidenceTypes,
            ResolvedRules rules
    ) {
        public List<FileFormat> allowedFormats() {
            return rules == null || rules.allowedFormats() == null ? List.of() : rules.allowedFormats();
        }

        public Boolean singleFilePerEvidenceType() {
            return rules == null ? null : rules.singleFilePerEvidenceType();
        }

        public Boolean externalLinksAllowed() {
            return rules == null ? null : rules.externalLinksAllowed();
        }

        public Long totalSizeLimitBytes() {
            return rules == null ? null : rules.totalSizeLimitBytes();
        }

        public Long perFileSizeLimitBytes() {
            return rules == null ? null : rules.perFileSizeLimitBytes();
        }

        public Long perImageFileSizeLimitBytes() {
            return rules == null ? null : rules.perImageFileSizeLimitBytes();
        }

        public Integer totalPagesLimit() {
            return rules == null ? null : rules.totalPagesLimit();
        }

        public Integer perPdfPageLimit() {
            return rules == null ? null : rules.perPdfPageLimit();
        }

        public Boolean pdfARequired() {
            return rules == null ? null : rules.pdfARequired();
        }

        public Boolean portfolioPdfAllowed() {
            return rules == null ? null : rules.portfolioPdfAllowed();
        }
    }
}
