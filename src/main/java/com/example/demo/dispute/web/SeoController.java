package com.example.demo.dispute.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class SeoController {

    private final String publicBaseUrl;
    private final List<GuidePageView> guides;
    private final Map<String, GuidePageView> guideBySlug;
    private final Map<String, List<GuidePageView>> guidesByPlatform;

    public SeoController(@Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.guides = List.copyOf(buildGuides());

        Map<String, GuidePageView> bySlug = new LinkedHashMap<>();
        Map<String, List<GuidePageView>> byPlatform = new LinkedHashMap<>();
        for (GuidePageView guide : guides) {
            bySlug.put(guide.slugKey(), guide);
            byPlatform.computeIfAbsent(guide.platformSlug(), ignored -> new ArrayList<>()).add(guide);
        }

        this.guideBySlug = Map.copyOf(bySlug);
        this.guidesByPlatform = byPlatform.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    private List<GuidePageView> buildGuides() {
        return List.of(
                new GuidePageView(
                        "stripe",
                        "fraud",
                        "Stripe",
                        "Fraud",
                        "Stripe Fraud Dispute Evidence Checklist",
                        "Format and file checklist for Stripe fraud disputes to avoid evidence submission rejections.",
                        List.of(
                                "Use only PDF, JPG, or PNG files.",
                                "Keep one file per evidence type.",
                                "Stay below 4.5MB total and below 50 pages total.",
                                "Remove all external links from uploaded PDFs."
                        ),
                        List.of(
                                "Multiple files uploaded for ORDER_RECEIPT.",
                                "External URL embedded in PDF evidence.",
                                "Total pages exceed Stripe and card-network limits."
                        ),
                        List.of(
                                "Merge duplicate files per evidence type before submit.",
                                "Compress oversized files before upload.",
                                "Run validation again and submit only after READY status."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "What causes Stripe evidence rejection most often?",
                                        "Incorrect format, over-limit pages or size, and duplicate files per evidence type are common blockers."
                                ),
                                new GuideFaqItem(
                                        "Can this guarantee a Stripe win?",
                                        "No. It only checks submission formatting and packaging, not legal merit."
                                )
                        )
                ),
                new GuidePageView(
                        "stripe",
                        "product-not-received",
                        "Stripe",
                        "Product Not Received",
                        "Stripe Product-Not-Received Evidence Checklist",
                        "Checklist for Stripe INR disputes with file packaging and validation guardrails.",
                        List.of(
                                "Upload tracking proof and customer communication as separate evidence types.",
                                "Ensure total PDF pages remain under 50.",
                                "Keep only one file per evidence type."
                        ),
                        List.of(
                                "Fulfillment proof split into multiple files.",
                                "Large combined PDF with low signal evidence.",
                                "Missing communication timeline."
                        ),
                        List.of(
                                "Merge duplicate evidence types into one file.",
                                "Place delivery proof first in final package.",
                                "Re-run validation and export only from READY."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "Is one tracking screenshot enough?",
                                        "Usually not. Include carrier tracking, delivery timestamp, and matching customer details."
                                ),
                                new GuideFaqItem(
                                        "Should I include all chat logs?",
                                        "Include only dispute-relevant excerpts to keep file size and reviewer attention under control."
                                )
                        )
                ),
                new GuidePageView(
                        "stripe",
                        "subscription-cancelled",
                        "Stripe",
                        "Subscription Cancelled",
                        "Stripe Subscription Cancellation Evidence Checklist",
                        "Submission checklist for subscription cancellation disputes in Stripe.",
                        List.of(
                                "Include cancellation policy and timestamped cancellation logs.",
                                "Attach customer communication around renewal reminders.",
                                "Keep artifacts under Stripe size and page constraints."
                        ),
                        List.of(
                                "Policy file missing or outdated version.",
                                "No proof of renewal notice delivery.",
                                "Multiple policy files uploaded under same evidence type."
                        ),
                        List.of(
                                "Prioritize policy, cancellation timeline, and user acknowledgment.",
                                "Remove unrelated communications.",
                                "Validate before checkout."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "What is the minimum cancellation evidence set?",
                                        "Policy terms, user account cancellation event, and customer notice timeline."
                                ),
                                new GuideFaqItem(
                                        "Do I need usage logs too?",
                                        "Include usage logs if they clarify access after alleged cancellation."
                                )
                        )
                ),
                new GuidePageView(
                        "stripe",
                        "not-as-described",
                        "Stripe",
                        "Not As Described",
                        "Stripe Not-As-Described Evidence Checklist",
                        "Packaging guide for Stripe product-not-as-described disputes.",
                        List.of(
                                "Include product detail page snapshot used at purchase time.",
                                "Attach support communication and refund/cancellation exchanges.",
                                "Use one clear file per evidence type."
                        ),
                        List.of(
                                "Policies evidence missing key return/quality section.",
                                "Evidence spread across too many files.",
                                "External links inside PDF references."
                        ),
                        List.of(
                                "Consolidate policy and communication into focused files.",
                                "Remove off-topic attachments.",
                                "Confirm all files pass link and size checks."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "Should product screenshots be current or historical?",
                                        "Use the version that existed when the order was placed."
                                ),
                                new GuideFaqItem(
                                        "Can external URLs be used as proof?",
                                        "Avoid them. Include screenshots or embedded content in accepted file formats."
                                )
                        )
                ),
                new GuidePageView(
                        "shopify",
                        "fraudulent",
                        "Shopify",
                        "Fraudulent",
                        "Shopify Fraud Chargeback Evidence Checklist",
                        "Shopify fraud evidence guide focused on accepted formats and constraints.",
                        List.of(
                                "Use PDF, JPG, or PNG only.",
                                "Keep each file at 2MB or below.",
                                "Avoid external links and PDF Portfolio artifacts."
                        ),
                        List.of(
                                "Oversized image files over 2MB.",
                                "Duplicate file uploads for the same evidence type.",
                                "Portfolio PDFs rejected by Shopify parser."
                        ),
                        List.of(
                                "Compress large images automatically where possible.",
                                "Merge duplicate evidence types into one file.",
                                "Validate again before export."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "Can Shopify accept multi-file evidence per type?",
                                        "Treat each evidence type as one final file to reduce rejection risk."
                                ),
                                new GuideFaqItem(
                                        "What if image compression reduces readability?",
                                        "Use high-contrast originals and check clarity after compression before submission."
                                )
                        )
                ),
                new GuidePageView(
                        "shopify",
                        "item-not-received",
                        "Shopify",
                        "Item Not Received",
                        "Shopify Item-Not-Received Evidence Checklist",
                        "Shopify dispute evidence guide with file-format, size, and PDF constraints for faster submission.",
                        List.of(
                                "Use PDF, JPG, or PNG only.",
                                "Keep each file at 2MB or below.",
                                "Use one file per evidence type.",
                                "Avoid PDF Portfolio and external links."
                        ),
                        List.of(
                                "PDF is not compliant with expected archive format.",
                                "Evidence category has duplicate files.",
                                "Total upload size exceeds Shopify product-scope limits."
                        ),
                        List.of(
                                "Use auto-fix to merge duplicate evidence categories.",
                                "Compress oversized images and re-run validation.",
                                "Export the final package and submit via Shopify admin."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "Should delivery screenshots be in one file?",
                                        "Yes. Combine related tracking screenshots into one evidence file."
                                ),
                                new GuideFaqItem(
                                        "How strict is Shopify on file size?",
                                        "Per-file limits are strict; oversized uploads can fail before reviewer checks."
                                )
                        )
                ),
                new GuidePageView(
                        "shopify",
                        "not-as-described",
                        "Shopify",
                        "Not As Described",
                        "Shopify Not-As-Described Evidence Checklist",
                        "Practical evidence packaging guide for Shopify product-quality disputes.",
                        List.of(
                                "Include product page snapshot and fulfillment evidence.",
                                "Add communication records and resolution attempts.",
                                "Keep file size under Shopify limits."
                        ),
                        List.of(
                                "Missing policy section for returns/quality.",
                                "Scattered communication in multiple files.",
                                "Image-only evidence with no order context."
                        ),
                        List.of(
                                "Combine communication into one timeline file.",
                                "Attach order receipt with SKU and item detail.",
                                "Run validation before final export."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "Can I submit only customer chat logs?",
                                        "Chat logs help but should be paired with order and policy evidence."
                                ),
                                new GuideFaqItem(
                                        "Are product photos enough?",
                                        "Use photos plus order and policy context so reviewers can connect the claim to transaction facts."
                                )
                        )
                ),
                new GuidePageView(
                        "shopify",
                        "cancelled-order",
                        "Shopify",
                        "Cancelled Order",
                        "Shopify Cancelled-Order Evidence Checklist",
                        "Checklist for Shopify cancelled-order disputes with policy and timeline focus.",
                        List.of(
                                "Attach cancellation confirmation and timestamp.",
                                "Include refund/cancellation policy excerpts.",
                                "Keep one file per evidence type."
                        ),
                        List.of(
                                "No timestamped cancellation proof.",
                                "Policy file exceeds limits or contains external links.",
                                "Refund communication missing."
                        ),
                        List.of(
                                "Merge policy materials into one file.",
                                "Add concise timeline of cancellation and refund actions.",
                                "Validate and export from READY state."
                        ),
                        List.of(
                                new GuideFaqItem(
                                        "What is most important in cancellation disputes?",
                                        "Timestamped proof of cancellation and clear policy language tied to the order."
                                ),
                                new GuideFaqItem(
                                        "Should I include all account activity?",
                                        "Only include activity relevant to cancellation and refund handling."
                                )
                        )
                )
        );
    }

    @GetMapping("/guides")
    public String guideIndex(Model model) {
        model.addAttribute("guides", guides);
        model.addAttribute("platforms", guidesByPlatform.keySet());
        model.addAttribute("canonicalUrl", publicBaseUrl + "/guides");
        model.addAttribute(
                "metaDescription",
                "Practical Stripe and Shopify dispute evidence guides: file constraints, common failures, and submission checklists."
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
        model.addAttribute("platform", key);
        model.addAttribute("platformLabel", platformLabel);
        model.addAttribute("guides", platformGuides);
        model.addAttribute("canonicalUrl", publicBaseUrl + "/guides/" + key);
        model.addAttribute("metaDescription", platformLabel + " dispute evidence checklists by reason code.");
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
        model.addAttribute("canonicalUrl", publicBaseUrl + guide.path());
        model.addAttribute("metaDescription", guide.metaDescription());
        return "guideDetail";
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
}
