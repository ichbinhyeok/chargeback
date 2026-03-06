package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeoGuideCatalogQualityTest {

    private static final Set<String> ALLOWED_PLATFORMS = Set.of("stripe", "shopify");
    private static final Set<String> ALLOWED_TYPES = Set.of("reason", "error");
    private static final List<String> CLAIM_GUARDRAILS = List.of(
            "guarantee",
            "guaranteed",
            "win rate",
            "increase approval rate",
            "legal advice"
    );
    private static final List<String> ALLOWED_SOURCE_HOSTS = List.of(
            "https://docs.stripe.com/",
            "https://stripe.com/",
            "https://help.shopify.com/"
    );

    @Test
    void seoGuideCatalogPassesQualityGate() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("seo/guides-v1.json")) {
            assertTrue(in != null, "seo/guides-v1.json must exist");
            GuideCatalog catalog = objectMapper.readValue(in, GuideCatalog.class);
            assertTrue(catalog != null, "catalog must parse");
            assertTrue(catalog.guides() != null && !catalog.guides().isEmpty(), "catalog must contain guides");
            assertTrue(catalog.guides().size() >= 40, "catalog should keep production floor volume");

            Set<String> slugKeys = new HashSet<>();
            Set<String> titles = new HashSet<>();
            Set<String> metaDescriptions = new HashSet<>();

            for (GuideEntry guide : catalog.guides()) {
                assertTrue(hasText(guide.platformSlug()), "platformSlug required");
                assertTrue(hasText(guide.slug()), "slug required");
                assertTrue(hasText(guide.guideType()), "guideType required");
                assertTrue(hasText(guide.title()), "title required");
                assertTrue(hasText(guide.metaDescription()), "metaDescription required");
                assertTrue(hasText(guide.reasonCodeLabel()), "reasonCodeLabel required");

                String platform = guide.platformSlug().trim().toLowerCase(Locale.ROOT);
                String type = guide.guideType().trim().toLowerCase(Locale.ROOT);
                String slugKey = platform + "/" + guide.slug().trim().toLowerCase(Locale.ROOT);
                assertTrue(ALLOWED_PLATFORMS.contains(platform), "unsupported platform: " + platform);
                assertTrue(ALLOWED_TYPES.contains(type), "unsupported guideType: " + type);
                assertTrue(slugKeys.add(slugKey), "duplicate slug key: " + slugKey);

                String title = guide.title().trim();
                assertTrue(title.length() >= 35 && title.length() <= 90, "title length out of target range: " + title);
                assertTrue(titles.add(title), "duplicate title: " + title);

                String meta = guide.metaDescription().trim();
                assertTrue(meta.length() >= 90 && meta.length() <= 190, "meta description length out of target range: " + meta);
                assertTrue(metaDescriptions.add(meta), "duplicate meta description: " + meta);

                assertTrue(guide.targetSearchQueries() != null && guide.targetSearchQueries().size() >= 4, "targetSearchQueries must have >= 4");
                assertTrue(guide.keyChecks() != null && guide.keyChecks().size() >= 5, "keyChecks must have >= 5");
                assertTrue(guide.commonErrors() != null && guide.commonErrors().size() >= 5, "commonErrors must have >= 5");
                assertTrue(guide.nextSteps() != null && guide.nextSteps().size() >= 5, "nextSteps must have >= 5");
                assertTrue(guide.explanationPreviewLines() != null && guide.explanationPreviewLines().size() >= 5, "explanationPreviewLines must have >= 5");
                assertTrue(guide.faq() != null && guide.faq().size() >= 4, "faq must have >= 4");

                assertTrue(guide.sourceUrls() != null && !guide.sourceUrls().isEmpty(), "sourceUrls required");
                for (String sourceUrl : guide.sourceUrls()) {
                    assertTrue(hasText(sourceUrl), "sourceUrl cannot be blank");
                    boolean allowedHost = ALLOWED_SOURCE_HOSTS.stream().anyMatch(sourceUrl::startsWith);
                    assertTrue(allowedHost, "sourceUrl host must be Stripe/Shopify official docs: " + sourceUrl);
                }

                for (String blockedPhrase : CLAIM_GUARDRAILS) {
                    String haystack = (title + " " + meta).toLowerCase(Locale.ROOT);
                    assertFalse(haystack.contains(blockedPhrase), "disallowed claim phrase found: " + blockedPhrase);
                }
            }

            long stripeCount = catalog.guides().stream().filter(g -> "stripe".equalsIgnoreCase(g.platformSlug())).count();
            long shopifyCount = catalog.guides().stream().filter(g -> "shopify".equalsIgnoreCase(g.platformSlug())).count();
            assertTrue(stripeCount >= 3, "stripe coverage too small");
            assertTrue(shopifyCount >= 3, "shopify coverage too small");

            long errorCount = catalog.guides().stream().filter(g -> "error".equalsIgnoreCase(g.guideType())).count();
            long reasonCount = catalog.guides().stream().filter(g -> "reason".equalsIgnoreCase(g.guideType())).count();
            assertTrue(errorCount >= 20, "error-guide coverage too small");
            assertTrue(reasonCount >= 20, "reason-guide coverage too small");

            assertEquals(catalog.guides().size(), slugKeys.size(), "slug cardinality mismatch");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record GuideCatalog(
            String version,
            List<GuideEntry> guides
    ) {
    }

    private record GuideEntry(
            String platformSlug,
            String slug,
            String platformLabel,
            String reasonCodeLabel,
            String guideType,
            String title,
            String metaDescription,
            List<String> targetSearchQueries,
            List<String> keyChecks,
            List<String> commonErrors,
            List<String> nextSteps,
            List<String> explanationPreviewLines,
            List<String> sourceUrls,
            List<GuideFaqEntry> faq
    ) {
    }

    private record GuideFaqEntry(
            String question,
            String answer
    ) {
    }
}
