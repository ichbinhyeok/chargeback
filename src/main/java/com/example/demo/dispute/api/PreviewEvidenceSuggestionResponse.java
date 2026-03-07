package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.EvidenceType;

public record PreviewEvidenceSuggestionResponse(
        int index,
        EvidenceType evidenceType,
        String reason,
        String source
) {
}
