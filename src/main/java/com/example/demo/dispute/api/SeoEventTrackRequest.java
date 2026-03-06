package com.example.demo.dispute.api;

public record SeoEventTrackRequest(
        String eventName,
        String platformSlug,
        String guideSlug,
        String pagePath,
        String sourceChannel,
        String sessionId,
        String referrer,
        String userAgent
) {
}
