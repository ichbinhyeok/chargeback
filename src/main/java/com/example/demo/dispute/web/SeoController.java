package com.example.demo.dispute.web;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SeoController {

    private final String publicBaseUrl;
    private final List<GuidePageView> guides;
    private final Map<String, GuidePageView> guideBySlug;
    private final Map<String, List<GuidePageView>> guidesByPlatform;

    public SeoController(
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${app.seo.guides-path:seo/guides-v1.json}") String guidesPath
    ) {
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
    public String guideIndex(Model model) {
        model.addAttribute("guides", guides);
        model.addAttribute("platforms", guidesByPlatform.keySet());
        model.addAttribute("canonicalUrl", publicBaseUrl + "/guides");
        model.addAttribute(
                "metaDescription",
                "Fix Stripe and Shopify dispute upload errors with reason-code checklists, file format fixes, and evidence packaging guides."
        );
        return "guidesIndex";
    }

    @GetMapping("/guides/{platform}")
    public String guidePlatform(@PathVariable String platform, Model model) {
        String key = platform.toLowerCase(Locale.ROOT);
        List<GuidePageView> platformGuides = guidesByPlatform.get(key);
        if (platformGuides == null || platformGuides.isEmpty()) {
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

        model.addAttribute("guide", guide);
        model.addAttribute("relatedGuides", findRelatedGuides(guide, 5));
        model.addAttribute("canonicalUrl", publicBaseUrl + guide.path());
        model.addAttribute("metaDescription", guide.metaDescription());
        return "guideDetail";
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

    private List<GuidePageView> findRelatedGuides(GuidePageView currentGuide, int limit) {
        List<GuidePageView> platformGuides = guidesByPlatform.getOrDefault(currentGuide.platformSlug(), List.of());

        List<GuidePageView> sameType = platformGuides.stream()
                .filter(guide -> !guide.slugKey().equals(currentGuide.slugKey()))
                .filter(guide -> guide.guideType().equalsIgnoreCase(currentGuide.guideType()))
                .limit(limit)
                .toList();

        if (sameType.size() >= limit) {
            return sameType;
        }

        List<GuidePageView> mixed = new ArrayList<>(sameType);
        for (GuidePageView guide : platformGuides) {
            if (mixed.size() >= limit) {
                break;
            }
            if (guide.slugKey().equals(currentGuide.slugKey())) {
                continue;
            }
            if (guide.guideType().equalsIgnoreCase(currentGuide.guideType())) {
                continue;
            }
            mixed.add(guide);
        }
        return List.copyOf(mixed);
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
