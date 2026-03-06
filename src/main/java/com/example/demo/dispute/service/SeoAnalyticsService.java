package com.example.demo.dispute.service;

import com.example.demo.dispute.api.SeoEventTrackRequest;
import com.example.demo.dispute.api.SeoKpiResponse;
import com.example.demo.dispute.persistence.SeoEventEntity;
import com.example.demo.dispute.persistence.SeoEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeoAnalyticsService {

    private static final String EVENT_GUIDE_VIEW = "guide_view";
    private static final String EVENT_START_CASE_CLICK = "guide_start_case_click";
    private static final String EVENT_NEW_CASE_VIEW_FROM_GUIDE = "new_case_view_from_guide";
    private static final String EVENT_CASE_CREATED_FROM_GUIDE = "case_created_from_guide";
    private static final String EVENT_GUIDE_EXPORT_CLICK = "guide_export_click";
    private static final String EVENT_GUIDE_ROUTER_MATCH = "guide_router_match";
    private static final String EVENT_GUIDE_ROUTER_NOMATCH = "guide_router_nomatch";
    private static final String EVENT_NEW_CASE_VIEW_FROM_ROUTER_NOMATCH = "new_case_view_from_router_nomatch";
    private static final String EVENT_CASE_CREATED_FROM_ROUTER_NOMATCH = "case_created_from_router_nomatch";

    private final SeoEventRepository seoEventRepository;

    public SeoAnalyticsService(SeoEventRepository seoEventRepository) {
        this.seoEventRepository = seoEventRepository;
    }

    @Transactional
    public void track(SeoEventTrackRequest request, String fallbackUserAgent) {
        String eventName = normalize(request.eventName(), 64);
        String pagePath = normalize(request.pagePath(), 255);
        String sessionId = normalize(request.sessionId(), 64);

        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        if (pagePath == null || pagePath.isBlank()) {
            throw new IllegalArgumentException("pagePath is required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }

        SeoEventEntity entity = new SeoEventEntity();
        entity.setEventName(eventName);
        entity.setPlatformSlug(normalizeLower(request.platformSlug(), 32));
        entity.setGuideSlug(normalizeLower(request.guideSlug(), 128));
        entity.setPagePath(pagePath);
        entity.setSourceChannel(normalizeLower(request.sourceChannel(), 32));
        entity.setSessionId(sessionId);
        entity.setReferrer(normalize(request.referrer(), 512));
        entity.setQueryText(normalize(request.queryText(), 255));
        entity.setMatchTarget(normalize(request.matchTarget(), 255));
        String userAgent = request.userAgent();
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = fallbackUserAgent;
        }
        entity.setUserAgent(normalize(userAgent, 512));
        entity.setOccurredAt(Instant.now());

        seoEventRepository.save(entity);
    }

    @Transactional
    public void trackGuideRouterDecision(
            String queryText,
            String platformSlug,
            String guideSlug,
            String matchTarget,
            boolean matched,
            String userAgent
    ) {
        String normalizedQuery = normalize(queryText, 255);
        if (normalizedQuery == null) {
            return;
        }

        SeoEventEntity entity = new SeoEventEntity();
        entity.setEventName(matched ? EVENT_GUIDE_ROUTER_MATCH : EVENT_GUIDE_ROUTER_NOMATCH);
        entity.setPlatformSlug(normalizeLower(platformSlug, 32));
        entity.setGuideSlug(normalizeLower(guideSlug, 128));
        entity.setPagePath("/guides/router");
        entity.setSourceChannel("guide_router");
        entity.setSessionId("router-" + System.nanoTime());
        entity.setQueryText(normalizedQuery);
        entity.setMatchTarget(normalize(matchTarget, 255));
        entity.setUserAgent(normalize(userAgent, 512));
        entity.setOccurredAt(Instant.now());

        seoEventRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public SeoKpiResponse summarize(int days) {
        int boundedDays = Math.max(1, Math.min(days, 180));
        Instant to = Instant.now();
        Instant from = to.minus(boundedDays, ChronoUnit.DAYS);
        List<SeoEventEntity> events = seoEventRepository.findByOccurredAtBetween(from, to);

        long totalEvents = events.size();
        Map<String, Long> eventCountsMap = countBy(events, SeoEventEntity::getEventName);
        Map<String, Long> sourceChannelMap = countBy(
                events,
                event -> valueOrDefault(event.getSourceChannel(), "unknown")
        );

        long guideViews = eventCountsMap.getOrDefault(EVENT_GUIDE_VIEW, 0L);
        long startCaseClicks = eventCountsMap.getOrDefault(EVENT_START_CASE_CLICK, 0L);
        long newCaseViews = eventCountsMap.getOrDefault(EVENT_NEW_CASE_VIEW_FROM_GUIDE, 0L);
        long caseCreated = eventCountsMap.getOrDefault(EVENT_CASE_CREATED_FROM_GUIDE, 0L);
        long exportClicks = eventCountsMap.getOrDefault(EVENT_GUIDE_EXPORT_CLICK, 0L);
        long routerNoMatch = eventCountsMap.getOrDefault(EVENT_GUIDE_ROUTER_NOMATCH, 0L);
        long routerNewCaseViews = eventCountsMap.getOrDefault(EVENT_NEW_CASE_VIEW_FROM_ROUTER_NOMATCH, 0L);
        long routerCaseCreated = eventCountsMap.getOrDefault(EVENT_CASE_CREATED_FROM_ROUTER_NOMATCH, 0L);

        double guideToStartRate = rate(startCaseClicks, guideViews);
        double startToNewCaseRate = rate(newCaseViews, startCaseClicks);
        double newCaseToCreatedRate = rate(caseCreated, newCaseViews);
        double createdToExportRate = rate(exportClicks, caseCreated);
        double routerNoMatchToNewCaseRate = rate(routerNewCaseViews, routerNoMatch);
        double routerNoMatchToCreatedRate = rate(routerCaseCreated, routerNoMatch);

        List<SeoKpiResponse.EventCount> eventCounts = eventCountsMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new SeoKpiResponse.EventCount(entry.getKey(), entry.getValue()))
                .toList();

        List<SeoKpiResponse.PlatformCount> platformGuideViews = events.stream()
                .filter(event -> EVENT_GUIDE_VIEW.equals(event.getEventName()))
                .collect(Collectors.groupingBy(
                        event -> valueOrDefault(event.getPlatformSlug(), "unknown"),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new SeoKpiResponse.PlatformCount(entry.getKey(), entry.getValue()))
                .toList();

        List<SeoKpiResponse.GuideCount> topGuides = events.stream()
                .filter(event -> EVENT_GUIDE_VIEW.equals(event.getEventName()))
                .collect(Collectors.groupingBy(
                        event -> valueOrDefault(event.getPlatformSlug(), "unknown")
                                + "|"
                                + valueOrDefault(event.getGuideSlug(), "unknown"),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    String platform = parts.length > 0 ? parts[0] : "unknown";
                    String guide = parts.length > 1 ? parts[1] : "unknown";
                    return new SeoKpiResponse.GuideCount(platform, guide, entry.getValue());
                })
                .toList();

        List<SeoKpiResponse.SourceChannelCount> sourceChannels = sourceChannelMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new SeoKpiResponse.SourceChannelCount(entry.getKey(), entry.getValue()))
                .toList();

        Map<String, GuideFunnelAccumulator> funnelMap = new LinkedHashMap<>();
        for (SeoEventEntity event : events) {
            String platform = valueOrDefault(event.getPlatformSlug(), "unknown");
            String guide = valueOrDefault(event.getGuideSlug(), "unknown");
            if ("unknown".equals(platform) || "unknown".equals(guide)) {
                continue;
            }
            String key = platform + "|" + guide;
            GuideFunnelAccumulator accumulator = funnelMap.computeIfAbsent(
                    key,
                    ignored -> new GuideFunnelAccumulator(platform, guide)
            );
            accumulator.accept(event.getEventName());
        }

        List<SeoKpiResponse.GuideFunnelCount> guideFunnels = funnelMap.values().stream()
                .map(this::toGuideFunnelCount)
                .sorted((left, right) -> {
                    int byViews = Long.compare(right.guideViews(), left.guideViews());
                    if (byViews != 0) {
                        return byViews;
                    }
                    int byCreated = Long.compare(right.caseCreated(), left.caseCreated());
                    if (byCreated != 0) {
                        return byCreated;
                    }
                    return left.guideSlug().compareTo(right.guideSlug());
                })
                .limit(30)
                .toList();

        List<SeoKpiResponse.GuideFunnelCount> priorityGuidesToImprove = guideFunnels.stream()
                .filter(row -> row.guideViews() >= 5)
                .filter(row -> row.viewToCreatedRate() < 0.20d || row.createdToExportRate() < 0.50d)
                .sorted((left, right) -> {
                    int byViews = Long.compare(right.guideViews(), left.guideViews());
                    if (byViews != 0) {
                        return byViews;
                    }
                    return Double.compare(left.viewToCreatedRate(), right.viewToCreatedRate());
                })
                .limit(10)
                .toList();

        List<SeoKpiResponse.RouterOpportunity> routerOpportunities = buildRouterOpportunities(events);

        return new SeoKpiResponse(
                from,
                to,
                totalEvents,
                guideViews,
                startCaseClicks,
                newCaseViews,
                caseCreated,
                exportClicks,
                routerNoMatch,
                routerNewCaseViews,
                routerCaseCreated,
                guideToStartRate,
                startToNewCaseRate,
                newCaseToCreatedRate,
                createdToExportRate,
                routerNoMatchToNewCaseRate,
                routerNoMatchToCreatedRate,
                eventCounts,
                platformGuideViews,
                topGuides,
                sourceChannels,
                guideFunnels,
                priorityGuidesToImprove,
                routerOpportunities
        );
    }

    private List<SeoKpiResponse.RouterOpportunity> buildRouterOpportunities(List<SeoEventEntity> events) {
        Map<String, Long> grouped = events.stream()
                .filter(event -> EVENT_GUIDE_ROUTER_NOMATCH.equals(event.getEventName()))
                .filter(event -> event.getQueryText() != null && !event.getQueryText().isBlank())
                .collect(Collectors.groupingBy(
                        event -> valueOrDefault(event.getPlatformSlug(), "all")
                                + "|"
                                + normalizeQueryForGrouping(event.getQueryText()),
                        Collectors.counting()
                ));

        List<SeoKpiResponse.RouterOpportunity> opportunities = new ArrayList<>();
        for (Map.Entry<String, Long> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String platform = parts.length > 0 ? parts[0] : "all";
            String query = parts.length > 1 ? parts[1] : "";
            if (query.isBlank()) {
                continue;
            }
            String suggestedSlug = buildSuggestedSlug(query);
            String suggestedTitle = buildSuggestedTitle(platform, query);
            opportunities.add(new SeoKpiResponse.RouterOpportunity(
                    platform,
                    query,
                    entry.getValue(),
                    suggestedSlug,
                    suggestedTitle
            ));
        }

        return opportunities.stream()
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.count(), left.count());
                    if (byCount != 0) {
                        return byCount;
                    }
                    return left.queryText().compareTo(right.queryText());
                })
                .limit(20)
                .toList();
    }

    private String normalizeQueryForGrouping(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.replaceAll("\\s+", " ");
    }

    private String buildSuggestedSlug(String query) {
        String[] tokens = query.split(" ");
        List<String> selected = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            selected.add(token);
            if (selected.size() >= 8) {
                break;
            }
        }
        if (selected.isEmpty()) {
            return "new-error-fix-guide";
        }
        return String.join("-", selected);
    }

    private String buildSuggestedTitle(String platform, String query) {
        String platformLabel = switch (platform) {
            case "stripe" -> "Stripe";
            case "shopify" -> "Shopify";
            default -> "Payment Platform";
        };
        String title = platformLabel + " " + toTitleWords(query) + " Evidence Upload Error Fix Guide";
        if (title.length() <= 90) {
            return title;
        }
        return title.substring(0, 90).trim();
    }

    private String toTitleWords(String value) {
        String[] tokens = value.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            if (token.length() == 1) {
                out.append(token.toUpperCase(Locale.ROOT));
            } else {
                out.append(Character.toUpperCase(token.charAt(0)))
                        .append(token.substring(1));
            }
            if (out.length() > 45) {
                break;
            }
        }
        return out.toString().trim();
    }

    private SeoKpiResponse.GuideFunnelCount toGuideFunnelCount(GuideFunnelAccumulator accumulator) {
        return new SeoKpiResponse.GuideFunnelCount(
                accumulator.platform,
                accumulator.guideSlug,
                accumulator.guideViews,
                accumulator.startCaseClicks,
                accumulator.newCaseViews,
                accumulator.caseCreated,
                accumulator.exportClicks,
                rate(accumulator.startCaseClicks, accumulator.guideViews),
                rate(accumulator.caseCreated, accumulator.guideViews),
                rate(accumulator.exportClicks, accumulator.caseCreated)
        );
    }

    private Map<String, Long> countBy(List<SeoEventEntity> events, Function<SeoEventEntity, String> classifier) {
        return events.stream()
                .map(classifier)
                .map(value -> valueOrDefault(value, "unknown"))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    private String normalizeLower(String value, int maxLength) {
        String normalized = normalize(value, maxLength);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (double) numerator / (double) denominator;
    }

    private static class GuideFunnelAccumulator {

        private final String platform;
        private final String guideSlug;
        private long guideViews;
        private long startCaseClicks;
        private long newCaseViews;
        private long caseCreated;
        private long exportClicks;

        private GuideFunnelAccumulator(String platform, String guideSlug) {
            this.platform = platform;
            this.guideSlug = guideSlug;
        }

        private void accept(String eventName) {
            if (EVENT_GUIDE_VIEW.equals(eventName)) {
                guideViews++;
                return;
            }
            if (EVENT_START_CASE_CLICK.equals(eventName)) {
                startCaseClicks++;
                return;
            }
            if (EVENT_NEW_CASE_VIEW_FROM_GUIDE.equals(eventName)) {
                newCaseViews++;
                return;
            }
            if (EVENT_CASE_CREATED_FROM_GUIDE.equals(eventName)) {
                caseCreated++;
                return;
            }
            if (EVENT_GUIDE_EXPORT_CLICK.equals(eventName)) {
                exportClicks++;
            }
        }
    }
}
