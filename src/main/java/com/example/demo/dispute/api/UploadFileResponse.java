package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import java.util.UUID;

public record UploadFileResponse(
        UUID fileId,
        EvidenceType evidenceType,
        FileFormat fileFormat,
        long sizeBytes,
        int pageCount,
        boolean externalLinkDetected,
        boolean pdfACompliant,
        boolean pdfPortfolio
) {
}

