package com.example.demo.dispute.service;

import com.example.demo.dispute.api.SeoEventTrackRequest;
import com.example.demo.dispute.api.SeoKpiResponse;
import com.example.demo.dispute.persistence.SeoEventEntity;
import com.example.demo.dispute.persistence.SeoEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        String userAgent = request.userAgent();
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = fallbackUserAgent;
        }
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

        double guideToStartRate = rate(startCaseClicks, guideViews);
        double startToNewCaseRate = rate(newCaseViews, startCaseClicks);

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

        return new SeoKpiResponse(
                from,
                to,
                totalEvents,
                guideViews,
                startCaseClicks,
                newCaseViews,
                guideToStartRate,
                startToNewCaseRate,
                eventCounts,
                platformGuideViews,
                topGuides,
                sourceChannels
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
}
