package com.example.demo.dispute.api;

import java.time.Instant;
import java.util.List;

public record SeoKpiResponse(
        Instant from,
        Instant to,
        long totalEvents,
        long guideViewCount,
        long startCaseClickCount,
        long newCaseViewFromGuideCount,
        double guideToStartCaseClickRate,
        double startCaseClickToNewCaseViewRate,
        List<EventCount> eventCounts,
        List<PlatformCount> platformGuideViews,
        List<GuideCount> topGuidesByView,
        List<SourceChannelCount> sourceChannelCounts
) {

    public record EventCount(
            String eventName,
            long count
    ) {
    }

    public record PlatformCount(
            String platform,
            long guideViews
    ) {
    }

    public record GuideCount(
            String platform,
            String guideSlug,
            long guideViews
    ) {
    }

    public record SourceChannelCount(
            String sourceChannel,
            long count
    ) {
    }
}
