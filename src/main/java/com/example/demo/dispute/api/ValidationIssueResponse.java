package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.IssueSeverity;

public record ValidationIssueResponse(
        String code,
        String ruleId,
        IssueSeverity severity,
        String message
) {
}

