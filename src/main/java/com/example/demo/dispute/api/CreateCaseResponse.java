package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import java.util.UUID;

public record CreateCaseResponse(
        UUID caseId,
        String caseToken,
        Platform platform,
        ProductScope productScope,
        CaseState state
) {
}

