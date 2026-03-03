package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EvidenceFileInput(
        @NotNull EvidenceType evidenceType,
        @NotNull FileFormat format,
        @Min(1) long sizeBytes,
        @Min(1) int pageCount,
        boolean externalLinkDetected,
        boolean pdfACompliant,
        boolean pdfPortfolio
) {
}

