package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.FixJobStatus;
import java.time.Instant;
import java.util.UUID;

public record FixJobResponse(
        UUID jobId,
        UUID caseId,
        FixJobStatus status,
        String summary,
        String failCode,
        String failMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
