package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.EvidenceType;
import jakarta.validation.constraints.NotNull;

public record ReclassifyFileRequest(
        @NotNull EvidenceType evidenceType
) {
}

