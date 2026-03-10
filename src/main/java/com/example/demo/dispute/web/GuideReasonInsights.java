package com.example.demo.dispute.web;

import java.util.List;

public record GuideReasonInsights(
        List<String> requiredEvidence,
        List<String> recommendedEvidence,
        List<String> weakEvidenceWarnings,
        List<String> priorityActions,
        List<String> workflowGuardrails,
        List<String> officialSources
) {
}
