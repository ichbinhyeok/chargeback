package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.ValidationSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValidationRunReportResponse(
        UUID validationRunId,
        int runNo,
        boolean passed,
        ValidationSource source,
        boolean earlySubmit,
        Instant createdAt,
        List<ValidationIssueResponse> issues
) {
}

