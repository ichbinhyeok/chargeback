package com.example.demo.dispute.web;

import java.util.List;

public record GuidePageView(
        String platformSlug,
        String reasonCodeSlug,
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
        List<GuideFaqItem> faqItems
) {
    public String platformPath() {
        return "/guides/" + platformSlug;
    }

    public String path() {
        return "/guides/" + platformSlug + "/" + reasonCodeSlug;
    }

    public String slugKey() {
        return platformSlug + "/" + reasonCodeSlug;
    }

    public boolean isErrorGuide() {
        return "error".equalsIgnoreCase(guideType);
    }

    public String guideTypeLabel() {
        return isErrorGuide() ? "Upload/Error Fix Guide" : "Reason-Code Guide";
    }
}
