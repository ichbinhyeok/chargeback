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
        long caseCreatedFromGuideCount,
        long guideExportClickCount,
        long guideRouterNoMatchCount,
        long newCaseViewFromRouterNoMatchCount,
        long caseCreatedFromRouterNoMatchCount,
        double guideToStartCaseClickRate,
        double startCaseClickToNewCaseViewRate,
        double newCaseViewToCaseCreatedRate,
        double caseCreatedToGuideExportClickRate,
        double routerNoMatchToNewCaseViewRate,
        double routerNoMatchToCaseCreatedRate,
        List<EventCount> eventCounts,
        List<PlatformCount> platformGuideViews,
        List<GuideCount> topGuidesByView,
        List<SourceChannelCount> sourceChannelCounts,
        List<GuideFunnelCount> guideFunnels,
        List<GuideFunnelCount> priorityGuidesToImprove,
        List<RouterOpportunity> routerOpportunities
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

    public record GuideFunnelCount(
            String platform,
            String guideSlug,
            long guideViews,
            long startCaseClicks,
            long newCaseViews,
            long caseCreated,
            long exportClicks,
            double viewToStartRate,
            double viewToCreatedRate,
            double createdToExportRate
    ) {
    }

    public record RouterOpportunity(
            String platform,
            String queryText,
            long count,
            String suggestedSlug,
            String suggestedTitle
    ) {
    }
}
