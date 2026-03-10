package com.example.demo.dispute.web;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.service.PolicyCatalogService;
import com.example.demo.dispute.service.ReasonCodeChecklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.dispute.service.SeoAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SeoController {

    private static final Map<String, FailureSnapshot> TOP_ERROR_FAILURE_SNAPSHOTS = createTopErrorFailureSnapshots();
    private static final Map<String, GuideProductProof> TOP_ERROR_PRODUCT_PROOF = createTopErrorProductProof();
    private static final Map<String, GuideHeroCopy> TOP_ERROR_HERO_COPY = createTopErrorHeroCopy();
    private static final Map<String, IssueSupportCopy> TOP_ERROR_ISSUE_SUPPORT = createTopErrorIssueSupport();
    private static final Set<String> RELATED_TOKEN_STOPWORDS = Set.of(
            "stripe",
            "shopify",
            "guide",
            "guides",
            "chargeback",
            "dispute",
            "evidence",
            "upload",
            "error",
            "fix",
            "workflow",
            "response",
            "required",
            "checklist",
            "manual",
            "process",
            "case",
            "cases"
    );

    private final String publicBaseUrl;
    private final SeoAnalyticsService seoAnalyticsService;
    private final ReasonCodeChecklistService reasonCodeChecklistService;
    private final PolicyCatalogService policyCatalogService;
    private final List<GuidePageView> guides;
    private final Map<String, GuidePageView> guideBySlug;
    private final Map<String, List<GuidePageView>> guidesByPlatform;

    public SeoController(
            SeoAnalyticsService seoAnalyticsService,
            ReasonCodeChecklistService reasonCodeChecklistService,
            PolicyCatalogService policyCatalogService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${app.seo.guides-path:seo/guides-v1.json}") String guidesPath
    ) {
        this.seoAnalyticsService = seoAnalyticsService;
        this.reasonCodeChecklistService = reasonCodeChecklistService;
        this.policyCatalogService = policyCatalogService;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.guides = List.copyOf(loadGuides(new ObjectMapper(), guidesPath));

        Map<String, GuidePageView> bySlug = new LinkedHashMap<>();
        Map<String, List<GuidePageView>> byPlatform = new LinkedHashMap<>();
        for (GuidePageView guide : guides) {
            if (bySlug.putIfAbsent(guide.slugKey(), guide) != null) {
                throw new IllegalStateException("duplicate guide slug detected: " + guide.slugKey());
            }
            byPlatform.computeIfAbsent(guide.platformSlug(), ignored -> new ArrayList<>()).add(guide);
        }

        this.guideBySlug = Map.copyOf(bySlug);
        this.guidesByPlatform = byPlatform.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    @GetMapping("/guides")
    public String guideIndex(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "router", required = false) String routerStatus,
            @RequestParam(value = "platform", required = false) String platformFilter,
            Model model
    ) {
        String normalizedPlatform = normalizePlatform(platformFilter);
        String trimmedQuery = query != null ? query.trim() : "";
        String routerStartFixUrl = null;
        if ("nomatch".equalsIgnoreCase(routerStatus) && !trimmedQuery.isBlank()) {
            StringBuilder directFix = new StringBuilder("/new?src=guide_router_nomatch&guide=router_nomatch&q=")
                    .append(encode(trimmedQuery));
            if (normalizedPlatform != null) {
                directFix.append("&platform=").append(encode(normalizedPlatform));
            }
            routerStartFixUrl = directFix.toString();
        }
        model.addAttribute("guides", guides);
        model.addAttribute("platforms", guidesByPlatform.keySet());
        model.addAttribute("routerQuery", trimmedQuery);
        model.addAttribute("routerPlatform", normalizedPlatform);
        model.addAttribute("routerPlatformStripe", "stripe".equals(normalizedPlatform));
        model.addAttribute("routerPlatformShopify", "shopify".equals(normalizedPlatform));
        model.addAttribute("routerNoMatch", "nomatch".equalsIgnoreCase(routerStatus));
        model.addAttribute("routerStartFixUrl", routerStartFixUrl);
        model.addAttribute("canonicalUrl", publicBaseUrl + "/guides");
        model.addAttribute(
                "metaDescription",
                "Fix Stripe and Shopify dispute upload errors with reason-code checklists, file format fixes, and evidence packaging guides."
        );
        return "guidesIndex";
    }

    @GetMapping("/guides/router")
    public String routeGuideQuery(
            @RequestParam(value = "q", required = false) String rawQuery,
            @RequestParam(value = "platform", required = false) String platformFilter,
            HttpServletRequest request
    ) {
        String normalizedQuery = normalizeSearchText(rawQuery);
        String normalizedPlatform = normalizePlatform(platformFilter);

        if (normalizedQuery == null) {
            return "redirect:/guides";
        }

        GuideMatch best = guides.stream()
                .filter(guide -> normalizedPlatform == null || normalizedPlatform.equals(guide.platformSlug()))
                .map(guide -> new GuideMatch(guide, scoreGuideMatch(guide, normalizedQuery)))
                .max(Comparator.comparingInt(GuideMatch::score))
                .orElse(null);

        if (best == null || best.score() < 10) {
            String inferredPlatform = normalizedPlatform != null ? normalizedPlatform : inferPlatformFromQuery(normalizedQuery);
            seoAnalyticsService.trackGuideRouterDecision(
                    rawQuery,
                    inferredPlatform,
                    null,
                    null,
                    false,
                    request.getHeader("User-Agent")
            );
            if (inferredPlatform != null) {
                String redirect = "/new?src=guide_router_nomatch&guide=router_nomatch"
                        + "&platform=" + encode(inferredPlatform)
                        + "&q=" + encode(rawQuery == null ? "" : rawQuery.trim());
                return "redirect:" + redirect;
            }
            String redirect = "/guides?router=nomatch&q=" + encode(rawQuery == null ? "" : rawQuery.trim());
            return "redirect:" + redirect;
        }

        GuidePageView guide = best.guide();
        String matchedTarget;
        if (guide.isErrorGuide()) {
            matchedTarget = "/new?src=guide&platform=" + encode(guide.platformSlug()) + "&guide=" + encode(guide.reasonCodeSlug());
            seoAnalyticsService.trackGuideRouterDecision(
                    rawQuery,
                    guide.platformSlug(),
                    guide.reasonCodeSlug(),
                    matchedTarget,
                    true,
                    request.getHeader("User-Agent")
            );
            return "redirect:" + matchedTarget;
        }
        matchedTarget = guide.path();
        seoAnalyticsService.trackGuideRouterDecision(
                rawQuery,
                guide.platformSlug(),
                guide.reasonCodeSlug(),
                matchedTarget,
                true,
                request.getHeader("User-Agent")
        );
        return "redirect:" + guide.path();
    }

    @GetMapping("/guides/{platform}")
    public String guidePlatform(@PathVariable String platform, Model model) {
        String key = platform.toLowerCase(Locale.ROOT);
        List<GuidePageView> platformGuides = guidesByPlatform.get(key);
        if (platformGuides == null || platformGuides.isEmpty()) {
            String legacyRedirect = resolveLegacyGuideRedirect(key);
            if (legacyRedirect != null) {
                return "redirect:" + legacyRedirect;
            }
            throw new ResponseStatusException(NOT_FOUND, "guide platform not found");
        }

        String platformLabel = platformGuides.get(0).platformLabel();
        List<GuidePageView> errorGuides = platformGuides.stream()
                .filter(GuidePageView::isErrorGuide)
                .toList();
        List<GuidePageView> reasonGuides = platformGuides.stream()
                .filter(guide -> !guide.isErrorGuide())
                .toList();

        model.addAttribute("platform", key);
        model.addAttribute("platformLabel", platformLabel);
        model.addAttribute("guides", platformGuides);
        model.addAttribute("errorGuides", errorGuides);
        model.addAttribute("reasonGuides", reasonGuides);
        model.addAttribute("canonicalUrl", publicBaseUrl + "/guides/" + key);
        model.addAttribute(
                "metaDescription",
                platformLabel + " upload-error fixes and reason-code evidence checklists for chargeback submissions."
        );
        return "guidesPlatform";
    }

    @GetMapping("/guides/{platform}/{reasonCode}")
    public String guideDetail(
            @PathVariable String platform,
            @PathVariable String reasonCode,
            Model model
    ) {
        String key = platform.toLowerCase(Locale.ROOT) + "/" + reasonCode.toLowerCase(Locale.ROOT);
        GuidePageView guide = guideBySlug.get(key);
        if (guide == null) {
            throw new ResponseStatusException(NOT_FOUND, "guide not found");
        }

        GuideHeroCopy heroCopy = TOP_ERROR_HERO_COPY.get(guide.slugKey());
        String heroTitle = heroCopy != null ? heroCopy.title() : guide.title();
        String heroDescription = heroCopy != null ? heroCopy.metaDescription() : guide.metaDescription();
        List<GuidePageView> sameTypeRelatedGuides = findRelatedGuides(guide, 8, guide.guideType(), false);
        List<GuidePageView> crossTypeRelatedGuides = findRelatedGuides(
                guide,
                8,
                guide.isErrorGuide() ? "reason" : "error",
                false
        );
        List<GuidePageView> keywordRelatedGuides = findRelatedGuides(guide, 8, null, true);
        GuideReasonInsights reasonInsights = guide.isErrorGuide() ? null : buildReasonInsights(guide);
        GuideProductProof productProof = TOP_ERROR_PRODUCT_PROOF.get(guide.slugKey());

        model.addAttribute("guide", guide);
        model.addAttribute("sameTypeRelatedGuides", sameTypeRelatedGuides);
        model.addAttribute("crossTypeRelatedGuides", crossTypeRelatedGuides);
        model.addAttribute("keywordRelatedGuides", keywordRelatedGuides);
        model.addAttribute("reasonInsights", reasonInsights);
        model.addAttribute("productProof", productProof);
        model.addAttribute("officialSources", reasonInsights != null ? reasonInsights.officialSources() : guide.sourceUrls());
        model.addAttribute(
                "relatedGuides",
                mergeRelatedGuides(10, sameTypeRelatedGuides, crossTypeRelatedGuides, keywordRelatedGuides)
        );
        model.addAttribute("canonicalUrl", publicBaseUrl + guide.path());
        model.addAttribute("metaDescription", heroDescription);
        model.addAttribute("heroTitle", heroTitle);
        model.addAttribute("heroDescription", heroDescription);
        FailureSnapshot failureSnapshot = TOP_ERROR_FAILURE_SNAPSHOTS.get(guide.slugKey());
        model.addAttribute("failureBefore", failureSnapshot != null ? failureSnapshot.beforeFix() : null);
        model.addAttribute("failureAfter", failureSnapshot != null ? failureSnapshot.afterFix() : null);
        model.addAttribute("failureResult", failureSnapshot != null ? failureSnapshot.expectedResult() : null);
        IssueSupportCopy issueSupportCopy = TOP_ERROR_ISSUE_SUPPORT.get(guide.slugKey());
        model.addAttribute("issueSupportValidation", issueSupportCopy != null ? issueSupportCopy.validationScope() : null);
        model.addAttribute("issueSupportAutoFix", issueSupportCopy != null ? issueSupportCopy.autoFixScope() : null);
        model.addAttribute("issueSupportManual", issueSupportCopy != null ? issueSupportCopy.manualWork() : null);
        return "guideDetail";
    }

    private GuideReasonInsights buildReasonInsights(GuidePageView guide) {
        Platform platform = platformForGuide(guide);
        ProductScope workflowScope = workflowScopeForGuide(guide);
        if (platform == null || workflowScope == null) {
            return null;
        }

        ReasonCodeChecklistService.ReasonChecklist checklist = reasonCodeChecklistService.resolve(
                platform,
                workflowScope,
                guide.reasonCodeSlug(),
                null,
                List.of()
        );
        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                platform,
                workflowScope,
                guide.reasonCodeSlug(),
                null
        );

        return new GuideReasonInsights(
                checklist.requiredEvidence(),
                checklist.recommendedEvidence(),
                checklist.weakEvidenceWarnings(),
                checklist.priorityActions(),
                buildWorkflowGuardrails(policy),
                mergeSources(guide.sourceUrls(), checklist.sourceUrls())
        );
    }

    private String resolveLegacyGuideRedirect(String pathSegment) {
        if (pathSegment == null || pathSegment.isBlank()) {
            return null;
        }
        for (String platformSlug : guidesByPlatform.keySet()) {
            String prefix = platformSlug + "-";
            if (!pathSegment.startsWith(prefix)) {
                continue;
            }
            String legacyGuideSlug = pathSegment.substring(prefix.length());
            if (legacyGuideSlug.isBlank()
                    || "evidence-upload-error".equals(legacyGuideSlug)
                    || "upload-error".equals(legacyGuideSlug)
                    || "evidence-upload-fix".equals(legacyGuideSlug)) {
                return "/guides/" + platformSlug;
            }
            GuidePageView guide = guideBySlug.get(platformSlug + "/" + legacyGuideSlug);
            if (guide != null) {
                return guide.path();
            }
            return "/guides/" + platformSlug;
        }
        return null;
    }

    @GetMapping("/seo/kpi")
    public String seoKpiDashboard(Model model) {
        model.addAttribute("canonicalUrl", publicBaseUrl + "/seo/kpi");
        model.addAttribute("metaDescription", "Internal SEO KPI dashboard for guide traffic and funnel events.");
        model.addAttribute("robotsMeta", "noindex,nofollow,noarchive");
        return "seoKpi";
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return String.join(
                "\n",
                "User-agent: *",
                "Allow: /",
                "Disallow: /c/",
                "Disallow: /api/",
                "Disallow: /webhooks/",
                "Sitemap: " + publicBaseUrl + "/sitemap.xml"
        ) + "\n";
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(xml, publicBaseUrl + "/", "weekly");
        appendUrl(xml, publicBaseUrl + "/new", "weekly");
        appendUrl(xml, publicBaseUrl + "/terms", "monthly");
        appendUrl(xml, publicBaseUrl + "/privacy", "monthly");
        appendUrl(xml, publicBaseUrl + "/guides", "daily");
        for (String platform : guidesByPlatform.keySet()) {
            appendUrl(xml, publicBaseUrl + "/guides/" + platform, "daily");
        }
        for (GuidePageView guide : guides) {
            appendUrl(xml, publicBaseUrl + guide.path(), "daily");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private List<GuidePageView> loadGuides(ObjectMapper objectMapper, String guidesPath) {
        ClassPathResource resource = new ClassPathResource(guidesPath);
        GuideCatalog catalog;
        try (InputStream in = resource.getInputStream()) {
            catalog = objectMapper.readValue(in, GuideCatalog.class);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load guide catalog: " + guidesPath, ex);
        }

        if (catalog == null || catalog.guides() == null || catalog.guides().isEmpty()) {
            throw new IllegalStateException("guide catalog is empty: " + guidesPath);
        }

        return catalog.guides().stream()
                .map(this::toGuideView)
                .sorted(Comparator
                        .comparing(GuidePageView::platformSlug)
                        .thenComparing(guide -> guide.isErrorGuide() ? 0 : 1)
                        .thenComparing(GuidePageView::reasonCodeLabel))
                .toList();
    }

    private GuidePageView toGuideView(GuideEntry entry) {
        requireText(entry.platformSlug(), "platformSlug");
        requireText(entry.slug(), "slug");
        requireText(entry.platformLabel(), "platformLabel");
        requireText(entry.reasonCodeLabel(), "reasonCodeLabel");
        requireText(entry.guideType(), "guideType");
        requireText(entry.title(), "title");
        requireText(entry.metaDescription(), "metaDescription");

        List<GuideFaqItem> faqItems = entry.faq() == null ? List.of() : entry.faq().stream()
                .filter(item -> item.question() != null && !item.question().isBlank())
                .filter(item -> item.answer() != null && !item.answer().isBlank())
                .map(item -> new GuideFaqItem(item.question().trim(), item.answer().trim()))
                .toList();

        return new GuidePageView(
                entry.platformSlug().trim().toLowerCase(Locale.ROOT),
                entry.slug().trim().toLowerCase(Locale.ROOT),
                entry.platformLabel().trim(),
                entry.reasonCodeLabel().trim(),
                entry.guideType().trim().toLowerCase(Locale.ROOT),
                entry.title().trim(),
                entry.metaDescription().trim(),
                cleanList(entry.targetSearchQueries()),
                cleanList(entry.keyChecks()),
                cleanList(entry.commonErrors()),
                cleanList(entry.nextSteps()),
                cleanList(entry.explanationPreviewLines()),
                cleanList(entry.sourceUrls()),
                faqItems
        );
    }

    private List<GuidePageView> findRelatedGuides(
            GuidePageView currentGuide,
            int limit,
            String guideTypeFilter,
            boolean requireKeywordOverlap
    ) {
        List<GuidePageView> platformGuides = guidesByPlatform.getOrDefault(currentGuide.platformSlug(), List.of());

        return platformGuides.stream()
                .filter(guide -> !guide.slugKey().equals(currentGuide.slugKey()))
                .filter(guide -> guideTypeFilter == null || guide.guideType().equalsIgnoreCase(guideTypeFilter))
                .map(guide -> new GuideSimilarity(guide, guideSimilarityScore(currentGuide, guide)))
                .filter(item -> !requireKeywordOverlap || item.score() > 0)
                .sorted(Comparator
                        .comparingInt(GuideSimilarity::score)
                        .reversed()
                        .thenComparing(item -> item.guide().guideType())
                        .thenComparing(item -> item.guide().reasonCodeLabel()))
                .limit(limit)
                .map(GuideSimilarity::guide)
                .toList();
    }

    private Platform platformForGuide(GuidePageView guide) {
        return switch (guide.platformSlug()) {
            case "stripe" -> Platform.STRIPE;
            case "shopify" -> Platform.SHOPIFY;
            default -> null;
        };
    }

    private ProductScope workflowScopeForGuide(GuidePageView guide) {
        return switch (guide.platformSlug()) {
            case "stripe" -> ProductScope.STRIPE_DISPUTE;
            case "shopify" -> ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK;
            default -> null;
        };
    }

    private List<String> buildWorkflowGuardrails(PolicyCatalogService.ResolvedPolicy policy) {
        if (policy == null) {
            return List.of();
        }

        LinkedHashSet<String> notes = new LinkedHashSet<>();
        if (!policy.allowedFormats().isEmpty()) {
            notes.add("Only upload " + humanizeFormats(policy.allowedFormats()) + " files.");
        }
        if (Boolean.TRUE.equals(policy.singleFilePerEvidenceType())) {
            notes.add("Keep one final file per evidence type in the exported pack.");
        }
        if (Boolean.FALSE.equals(policy.externalLinksAllowed())) {
            notes.add("Remove clickable external links before final upload.");
        }
        if (policy.totalSizeLimitBytes() != null) {
            notes.add("Keep the full evidence package at or under " + formatMegabytes(policy.totalSizeLimitBytes()) + ".");
        }
        if (policy.perFileSizeLimitBytes() != null) {
            notes.add("Keep each uploaded file at or under " + formatMegabytes(policy.perFileSizeLimitBytes()) + ".");
        }
        if (policy.totalPagesLimit() != null) {
            notes.add("Keep the combined packet at or under " + policy.totalPagesLimit() + " pages.");
        }
        if (policy.perPdfPageLimit() != null) {
            notes.add("Keep each PDF at or under " + policy.perPdfPageLimit() + " pages.");
        }
        if (Boolean.TRUE.equals(policy.pdfARequired())) {
            notes.add("Convert PDFs to PDF/A before upload.");
        }
        if (Boolean.FALSE.equals(policy.portfolioPdfAllowed())) {
            notes.add("Flatten portfolio PDFs into a standard PDF before upload.");
        }
        return List.copyOf(notes);
    }

    private List<String> mergeSources(List<String> primary, List<String> secondary) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            merged.addAll(primary);
        }
        if (secondary != null) {
            merged.addAll(secondary);
        }
        return List.copyOf(merged);
    }

    private String humanizeFormats(List<FileFormat> formats) {
        List<String> labels = formats.stream()
                .map(FileFormat::name)
                .toList();
        if (labels.size() == 1) {
            return labels.get(0);
        }
        if (labels.size() == 2) {
            return labels.get(0) + " or " + labels.get(1);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1))
                + ", or "
                + labels.get(labels.size() - 1);
    }

    private String formatMegabytes(long bytes) {
        double megabytes = bytes / (1024d * 1024d);
        if (Math.abs(megabytes - Math.rint(megabytes)) < 0.01d) {
            return (long) Math.rint(megabytes) + " MB";
        }
        return String.format(Locale.ROOT, "%.1f MB", megabytes);
    }

    @SafeVarargs
    private final List<GuidePageView> mergeRelatedGuides(int limit, List<GuidePageView>... groups) {
        LinkedHashMap<String, GuidePageView> merged = new LinkedHashMap<>();
        for (List<GuidePageView> group : groups) {
            for (GuidePageView guide : group) {
                if (merged.size() >= limit) {
                    break;
                }
                merged.putIfAbsent(guide.slugKey(), guide);
            }
            if (merged.size() >= limit) {
                break;
            }
        }
        return List.copyOf(merged.values());
    }

    private int guideSimilarityScore(GuidePageView currentGuide, GuidePageView candidate) {
        Set<String> seed = guideTokens(currentGuide);
        Set<String> other = guideTokens(candidate);

        int overlap = 0;
        for (String token : seed) {
            if (other.contains(token)) {
                overlap++;
            }
        }

        int score = overlap * 6;
        if (currentGuide.guideType().equalsIgnoreCase(candidate.guideType())) {
            score += 3;
        }
        if (sharesSlugStem(currentGuide.reasonCodeSlug(), candidate.reasonCodeSlug())) {
            score += 4;
        }
        return score;
    }

    private Set<String> guideTokens(GuidePageView guide) {
        Set<String> tokens = new HashSet<>();
        addTokens(tokens, guide.title());
        addTokens(tokens, guide.reasonCodeLabel());
        addTokens(tokens, guide.reasonCodeSlug());
        addTokens(tokens, guide.metaDescription());
        for (String phrase : guide.targetSearchQueries()) {
            addTokens(tokens, phrase);
        }
        return tokens;
    }

    private void addTokens(Set<String> out, String value) {
        String normalized = normalizeSearchText(value);
        if (normalized == null) {
            return;
        }
        for (String token : normalized.split(" ")) {
            if (token.length() < 3) {
                continue;
            }
            if (RELATED_TOKEN_STOPWORDS.contains(token)) {
                continue;
            }
            out.add(token);
        }
    }

    private boolean sharesSlugStem(String leftSlug, String rightSlug) {
        String left = normalizeSearchText(leftSlug);
        String right = normalizeSearchText(rightSlug);
        if (left == null || right == null) {
            return false;
        }
        String[] leftTokens = left.split(" ");
        String[] rightTokens = right.split(" ");
        if (leftTokens.length == 0 || rightTokens.length == 0) {
            return false;
        }
        return leftTokens[0].equals(rightTokens[0]) || left.contains(right) || right.contains(left);
    }

    private List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("guide field is missing: " + fieldName);
        }
    }

    private int scoreGuideMatch(GuidePageView guide, String query) {
        String normalizedTitle = normalizeSearchText(guide.title());
        String normalizedReason = normalizeSearchText(guide.reasonCodeLabel());
        String normalizedSlug = normalizeSearchText(guide.reasonCodeSlug());
        String normalizedMeta = normalizeSearchText(guide.metaDescription());
        String normalizedCorpus = buildNormalizedSearchCorpus(guide);

        int score = 0;

        if (normalizedTitle != null && (normalizedTitle.contains(query) || query.contains(normalizedTitle))) {
            score += 80;
        }
        if (normalizedReason != null && (normalizedReason.contains(query) || query.contains(normalizedReason))) {
            score += 50;
        }
        if (normalizedSlug != null && (normalizedSlug.contains(query) || query.contains(normalizedSlug))) {
            score += 45;
        }
        if (normalizedMeta != null && normalizedMeta.contains(query)) {
            score += 25;
        }
        if (normalizedCorpus != null && normalizedCorpus.contains(query)) {
            score += 22;
        }

        for (String phrase : guide.targetSearchQueries()) {
            String normalizedPhrase = normalizeSearchText(phrase);
            if (normalizedPhrase != null && (normalizedPhrase.contains(query) || query.contains(normalizedPhrase))) {
                score += 24;
            }
        }
        for (String errorPhrase : guide.commonErrors()) {
            String normalizedError = normalizeSearchText(errorPhrase);
            if (normalizedError != null && (normalizedError.contains(query) || query.contains(normalizedError))) {
                score += 14;
            }
        }

        for (String token : query.split(" ")) {
            if (token.length() < 2) {
                continue;
            }
            if (containsToken(normalizedTitle, token)
                    || containsToken(normalizedReason, token)
                    || containsToken(normalizedSlug, token)
                    || containsToken(normalizedMeta, token)
                    || containsToken(normalizedCorpus, token)) {
                score += 4;
            }
        }

        if (guide.isErrorGuide()) {
            score += 2;
        }
        return score;
    }

    private boolean containsToken(String value, String token) {
        return value != null && value.contains(token);
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        normalized = normalized
                .replace("pdf/a", "pdfa")
                .replace("pdf-a", "pdfa")
                .replace("4.5mb", "4 5 mb")
                .replace("2mb", "2 mb");
        normalized = normalized.replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return null;
        }
        normalized = normalized
                .replaceAll("\\bpdfa\\b", "pdf a")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String buildNormalizedSearchCorpus(GuidePageView guide) {
        List<String> segments = new ArrayList<>();
        addNormalizedSegment(segments, guide.title());
        addNormalizedSegment(segments, guide.reasonCodeLabel());
        addNormalizedSegment(segments, guide.reasonCodeSlug());
        addNormalizedSegment(segments, guide.metaDescription());
        for (String phrase : guide.targetSearchQueries()) {
            addNormalizedSegment(segments, phrase);
        }
        for (String phrase : guide.commonErrors()) {
            addNormalizedSegment(segments, phrase);
        }
        if (segments.isEmpty()) {
            return null;
        }
        return String.join(" ", segments);
    }

    private void addNormalizedSegment(List<String> segments, String value) {
        String normalized = normalizeSearchText(value);
        if (normalized != null && !normalized.isBlank()) {
            segments.add(normalized);
        }
    }

    private String normalizePlatform(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("stripe".equals(normalized) || "shopify".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String inferPlatformFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        if (query.contains("shopify")) {
            return "shopify";
        }
        if (query.contains("stripe")) {
            return "stripe";
        }
        return null;
    }

    private void appendUrl(StringBuilder xml, String loc, String changefreq) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        xml.append("    <lastmod>").append(LocalDate.now()).append("</lastmod>\n");
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("  </url>\n");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, FailureSnapshot> createTopErrorFailureSnapshots() {
        return Map.ofEntries(
                Map.entry(
                        "stripe/evidence-file-size-limit-4-5mb",
                        new FailureSnapshot(
                                "Before: 6.1MB package with duplicated screenshots and full chat dumps. Upload fails on Stripe total-size check.",
                                "After: Consolidated to 3.9MB by removing duplicates and keeping only reason-linked excerpts.",
                                "Expected result: First-pass upload succeeds without size rejection."
                        )
                ),
                Map.entry(
                        "stripe/upload-failed-no-external-links",
                        new FailureSnapshot(
                                "Before: PDF includes clickable tracking and help-center URLs in annotation objects.",
                                "After: External-link annotations removed and document re-exported as reviewer-safe PDF.",
                                "Expected result: Link-related blocker clears during validation and upload."
                        )
                ),
                Map.entry(
                        "stripe/invalid-file-format-pdf-jpg-png-only",
                        new FailureSnapshot(
                                "Before: Evidence bundle includes HEIC and WEBP files exported directly from mobile.",
                                "After: Unsupported files converted to JPEG/PDF with clear evidence-type labeling.",
                                "Expected result: Format validation passes and files are accepted by uploader."
                        )
                ),
                Map.entry(
                        "stripe/total-pages-over-limit",
                        new FailureSnapshot(
                                "Before: Combined pack reaches 67 pages due to verbose logs and repeated invoice pages.",
                                "After: Trimmed to 32 pages with only dispute-relevant windows and a short chronology table.",
                                "Expected result: Page-limit blocker is removed before submission."
                        )
                ),
                Map.entry(
                        "stripe/duplicate-evidence-type-file-error",
                        new FailureSnapshot(
                                "Before: Three separate ORDER_RECEIPT files uploaded for one evidence type.",
                                "After: Files merged into one reviewer-ordered PDF per evidence type.",
                                "Expected result: One-file-per-type rule passes and packaging is export-ready."
                        )
                ),
                Map.entry(
                        "shopify/pdf-a-format-required-error",
                        new FailureSnapshot(
                                "Before: Scanner-generated PDF lacks PDF/A metadata and fails Shopify Payments check.",
                                "After: Re-exported as PDF/A and validated again with policy-aware checks.",
                                "Expected result: PDF/A compliance blocker is cleared."
                        )
                ),
                Map.entry(
                        "shopify/evidence-file-too-large-2mb",
                        new FailureSnapshot(
                                "Before: Single proof image is 3.4MB PNG and rejected by per-file limit.",
                                "After: Compressed to readable JPEG under 2MB via auto-fix image pipeline.",
                                "Expected result: Per-file upload succeeds without manual retry loop."
                        )
                ),
                Map.entry(
                        "shopify/pdf-portfolio-not-accepted",
                        new FailureSnapshot(
                                "Before: Office-exported portfolio PDF embeds multiple sub-documents.",
                                "After: Flattened into a standard sequential PDF with one document body.",
                                "Expected result: Portfolio-specific rejection no longer appears."
                        )
                ),
                Map.entry(
                        "shopify/external-links-not-allowed-error",
                        new FailureSnapshot(
                                "Before: Statement PDF contains clickable external URLs in appendix notes.",
                                "After: Link annotations removed and references converted to plain text.",
                                "Expected result: External-link blocker is resolved before upload."
                        )
                ),
                Map.entry(
                        "shopify/duplicate-evidence-type-file-error",
                        new FailureSnapshot(
                                "Before: Multiple POLICY files attached to one Shopify evidence type.",
                                "After: Duplicate files merged into a single ordered evidence document.",
                                "Expected result: Shopify one-file-per-type requirement is satisfied."
                        )
                )
        );
    }

    private static Map<String, GuideHeroCopy> createTopErrorHeroCopy() {
        return Map.ofEntries(
                Map.entry(
                        "stripe/evidence-file-size-limit-4-5mb",
                        new GuideHeroCopy(
                                "Stripe \"evidence file size limit 4.5MB\" upload fix",
                                "Fix Stripe evidence file size limit 4.5MB upload failures. Trim low-signal pages, merge by type, and revalidate before export."
                        )
                ),
                Map.entry(
                        "stripe/upload-failed-no-external-links",
                        new GuideHeroCopy(
                                "Stripe \"upload failed: no external links\" error fix",
                                "Fix Stripe upload failed no external links errors by removing URL annotations and exporting reviewer-safe evidence files."
                        )
                ),
                Map.entry(
                        "stripe/invalid-file-format-pdf-jpg-png-only",
                        new GuideHeroCopy(
                                "Stripe \"invalid file format (PDF/JPG/PNG only)\" fix",
                                "Fix Stripe invalid file format errors by converting unsupported files to PDF, JPG, or PNG and rerunning validation."
                        )
                ),
                Map.entry(
                        "stripe/total-pages-over-limit",
                        new GuideHeroCopy(
                                "Stripe \"total pages over limit\" upload fix",
                                "Fix Stripe total pages over limit errors by trimming duplicate pages, keeping only reason-linked proofs, and rechecking page counts."
                        )
                ),
                Map.entry(
                        "stripe/duplicate-evidence-type-file-error",
                        new GuideHeroCopy(
                                "Stripe \"duplicate evidence type file\" error fix",
                                "Fix Stripe duplicate evidence type file errors by merging same-type files into one reviewer-ordered document."
                        )
                ),
                Map.entry(
                        "shopify/pdf-a-format-required-error",
                        new GuideHeroCopy(
                                "Shopify \"PDF/A format required\" upload error fix",
                                "Fix Shopify PDF/A format required errors by re-exporting compliant PDFs and verifying policy checks before submission."
                        )
                ),
                Map.entry(
                        "shopify/evidence-file-too-large-2mb",
                        new GuideHeroCopy(
                                "Shopify \"evidence file too large (2MB)\" fix",
                                "Fix Shopify evidence file too large 2MB errors by compressing images and preserving readability for reviewer decisions."
                        )
                ),
                Map.entry(
                        "shopify/pdf-portfolio-not-accepted",
                        new GuideHeroCopy(
                                "Shopify \"PDF portfolio not accepted\" upload fix",
                                "Fix Shopify PDF portfolio not accepted errors by flattening embedded PDFs into a single standard document."
                        )
                ),
                Map.entry(
                        "shopify/external-links-not-allowed-error",
                        new GuideHeroCopy(
                                "Shopify \"external links not allowed\" error fix",
                                "Fix Shopify external links not allowed errors by removing link annotations and attaching direct file evidence."
                        )
                ),
                Map.entry(
                        "shopify/duplicate-evidence-type-file-error",
                        new GuideHeroCopy(
                                "Shopify \"duplicate evidence type file\" upload fix",
                                "Fix Shopify duplicate evidence type file errors by consolidating duplicate attachments into one file per evidence type."
                        )
                )
        );
    }

    private static Map<String, IssueSupportCopy> createTopErrorIssueSupport() {
        return Map.ofEntries(
                Map.entry(
                        "stripe/evidence-file-size-limit-4-5mb",
                        new IssueSupportCopy(
                                "Validation highlights total package size pressure and helps surface related blockers that often appear after trimming or merging files.",
                                "Auto-fix can help on supported packaging blockers, but you still need to decide which pages, screenshots, and appendices are low-signal enough to remove safely.",
                                "You still need to protect readability and choose what evidence stays in the pack so the file gets smaller without becoming weaker."
                        )
                ),
                Map.entry(
                        "stripe/total-pages-over-limit",
                        new IssueSupportCopy(
                                "Validation measures combined page count and points to the longest PDFs first, so you can stop guessing which export is keeping the case over the limit.",
                                "Auto-fix can remove blank pages and exact duplicate PDF pages from supported Stripe evidence before you retry the same packet.",
                                "You still need to choose the final page window that stays tied to the dispute and cut appendix or event-log pages that do not help the reviewer."
                        )
                ),
                Map.entry(
                        "stripe/upload-failed-no-external-links",
                        new IssueSupportCopy(
                                "Validation is designed to catch external-link blockers in uploaded evidence so hidden PDF annotations do not slip into the final pack.",
                                "For supported PDFs, auto-fix can remove external-link annotations and let you rerun checks before export.",
                                "You still need to replace link-only proof with attached screenshots, excerpts, or exported records when the evidence depends on off-platform material."
                        )
                ),
                Map.entry(
                        "shopify/pdf-a-format-required-error",
                        new IssueSupportCopy(
                                "Validation checks whether the failing Shopify document is still non-compliant after conversion and helps confirm the blocker is really PDF/A-related.",
                                "Auto-fix can convert supported PDFs into PDF/A and rerun validation so the same file is checked again inside the workflow.",
                                "You still need to confirm the source file is the right one, especially when the same export also contains portfolio structure or hidden links, and verify readability after conversion."
                        )
                ),
                Map.entry(
                        "shopify/evidence-file-too-large-2mb",
                        new IssueSupportCopy(
                                "Validation catches when one Shopify evidence file is over the 2MB uploader limit and shows which file should shrink first.",
                                "Auto-fix can compress supported receipt photos and image captures into reviewer-readable JPEGs under the per-file limit.",
                                "You still need to decide when a rescan or a tighter crop is safer than heavy compression for text-heavy images."
                        )
                ),
                Map.entry(
                        "shopify/pdf-portfolio-not-accepted",
                        new IssueSupportCopy(
                                "Validation detects portfolio-style PDFs that look normal in desktop apps but fail Shopify's evidence uploader.",
                                "Auto-fix can flatten supported portfolio PDFs into one standard sequential document and rerun validation.",
                                "You still need to verify the flattened page order and confirm that every embedded sub-document survived the export."
                        )
                ),
                Map.entry(
                        "shopify/external-links-not-allowed-error",
                        new IssueSupportCopy(
                                "Validation catches hidden URL annotations that merchants often miss in portal exports, help-center printouts, and statement appendices.",
                                "Auto-fix can strip external-link annotations from supported PDFs and rerun the same Shopify checks immediately.",
                                "You still need to replace link-only proof with attached screenshots, exports, or excerpts when the evidence depends on off-platform pages."
                        )
                )
        );
    }

    private static Map<String, GuideProductProof> createTopErrorProductProof() {
        return Map.ofEntries(
                Map.entry(
                        "shopify/pdf-a-format-required-error",
                        new GuideProductProof(
                                "Scanner-exported receipt PDF failed Shopify's PDF/A rule in a beta browser run.",
                                "Observed on March 8, 2026",
                                "Case was BLOCKED on ERR_SHPFY_PDF_NOT_PDFA.",
                                "After AUTO_FIX, the case moved to READY and a free watermarked summary PDF downloaded successfully.",
                                "Export stayed locked until the PDF/A blocker cleared.",
                                true,
                                "/proof/beta/shopify-pdfa-summary-page-1.png",
                                "/proof/beta/shopify-pdfa-summary.pdf",
                                "Synthetic beta artifact preview generated after the PDF/A blocker cleared."
                        )
                ),
                Map.entry(
                        "shopify/pdf-portfolio-not-accepted",
                        new GuideProductProof(
                                "Office-exported portfolio PDF hit stacked Shopify blockers in beta.",
                                "Observed on March 8, 2026",
                                "Case was BLOCKED on ERR_SHPFY_PDF_NOT_PDFA and ERR_SHPFY_PDF_PORTFOLIO.",
                                "After AUTO_FIX, the case moved to READY and the normalized PDF replaced the original portfolio export.",
                                "The workflow cleared both portfolio and PDF/A blockers before export unlocked.",
                                true,
                                "/proof/beta/shopify-portfolio-summary-page-1.png",
                                "/proof/beta/shopify-portfolio-summary.pdf",
                                "Synthetic beta artifact preview generated after portfolio and PDF/A cleanup."
                        )
                ),
                Map.entry(
                        "shopify/evidence-file-too-large-2mb",
                        new GuideProductProof(
                                "A real oversized receipt photo was reduced below Shopify's per-file limit during beta testing.",
                                "Observed on March 8, 2026",
                                "Case was BLOCKED on ERR_SHPFY_FILE_TOO_LARGE plus ERR_SHPFY_PDF_NOT_PDFA.",
                                "After AUTO_FIX, the oversized PNG receipt became a 794013 B JPEG and the case moved to READY.",
                                "The workflow reduced a real oversized image below Shopify's 2 MB per-file limit before export unlocked.",
                                true,
                                "/proof/beta/shopify-oversized-photo-summary-page-1.png",
                                "/proof/beta/shopify-oversized-photo-summary.pdf",
                                "Synthetic beta artifact preview generated after oversized-image compression and revalidation."
                        )
                ),
                Map.entry(
                        "shopify/external-links-not-allowed-error",
                        new GuideProductProof(
                                "A help-center export with hidden links was normalized successfully in beta.",
                                "Observed on March 8, 2026",
                                "Case was BLOCKED on ERR_SHPFY_LINK_DETECTED plus ERR_SHPFY_PDF_NOT_PDFA.",
                                "After AUTO_FIX, the case moved to READY and the normalized PDF replaced the original link-bearing export.",
                                "Export stayed locked until the link-bearing PDF was normalized.",
                                true,
                                "/proof/beta/shopify-links-summary-page-1.png",
                                "/proof/beta/shopify-links-summary.pdf",
                                "Synthetic beta artifact preview generated after hidden-link cleanup and PDF normalization."
                        )
                ),
                Map.entry(
                        "stripe/invalid-file-format-pdf-jpg-png-only",
                        new GuideProductProof(
                                "Scanner TIFF delivery proof was auto-normalized before stored validation in beta.",
                                "Observed on March 8, 2026",
                                "Upload flow showed Auto-convert to JPEG for a TIFF delivery proof.",
                                "Stored file became scanner_delivery_proof_autoconvert.jpg and the case stayed READY on accepted final formats.",
                                "The workflow normalized an unsupported scanner TIFF before stored validation.",
                                true,
                                "/proof/beta/stripe-tiff-summary-page-1.png",
                                "/proof/beta/stripe-tiff-summary.pdf",
                                "Synthetic beta artifact preview generated after TIFF-to-JPEG normalization."
                        )
                ),
                Map.entry(
                        "stripe/total-pages-over-limit",
                        new GuideProductProof(
                                "A genuinely overlong Stripe evidence packet stayed blocked even after auto-fix in beta.",
                                "Observed on March 8, 2026",
                                "Case was BLOCKED on ERR_STRIPE_TOTAL_PAGES with a 52-page delivery dump.",
                                "After AUTO_FIX, the packet stayed BLOCKED, the delivery dump stayed 52 pages, and no free summary link appeared.",
                                "The workflow correctly refuses to unlock output for unresolved Stripe page-limit violations.",
                                false,
                                null,
                                null,
                                "No preview artifact is shown here because the case never unlocked a downloadable summary."
                        )
                )
        );
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

    private record FailureSnapshot(
            String beforeFix,
            String afterFix,
            String expectedResult
    ) {
    }

    private record GuideHeroCopy(
            String title,
            String metaDescription
    ) {
    }

    private record IssueSupportCopy(
            String validationScope,
            String autoFixScope,
            String manualWork
    ) {
    }

    private record GuideMatch(
            GuidePageView guide,
            int score
    ) {
    }

    private record GuideSimilarity(
            GuidePageView guide,
            int score
    ) {
    }
}
