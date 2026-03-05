package com.example.demo.dispute.web;

import java.util.List;

public record GuidePageView(
        String platformSlug,
        String reasonCodeSlug,
        String platformLabel,
        String reasonCodeLabel,
        String title,
        String metaDescription,
        List<String> keyChecks,
        List<String> commonErrors,
        List<String> nextSteps,
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
}
