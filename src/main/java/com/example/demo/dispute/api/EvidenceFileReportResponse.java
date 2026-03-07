package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import java.time.Instant;
import java.util.UUID;

public record EvidenceFileReportResponse(
        UUID fileId,
        EvidenceType evidenceType,
        String originalName,
        FileFormat fileFormat,
        long sizeBytes,
        int pageCount,
        boolean externalLinkDetected,
        boolean pdfACompliant,
        boolean pdfPortfolio,
        Instant createdAt
) {
}

