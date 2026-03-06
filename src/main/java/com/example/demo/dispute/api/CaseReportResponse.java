package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.CardNetwork;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseReportResponse(
        UUID caseId,
        String caseToken,
        Platform platform,
        ProductScope productScope,
        String reasonCode,
        CardNetwork cardNetwork,
        CaseState state,
        Instant createdAt,
        ValidationRunReportResponse latestValidation,
        List<EvidenceFileReportResponse> files
) {
}

