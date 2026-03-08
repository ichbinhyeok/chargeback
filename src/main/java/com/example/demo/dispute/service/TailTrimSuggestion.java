package com.example.demo.dispute.service;

import java.util.UUID;

public record TailTrimSuggestion(
        String title,
        UUID fileId,
        int trimStartPage,
        int trimEndPage,
        String fileLabel,
        String pageRange,
        String reason,
        String signalSummary,
        String caution
) {
}
