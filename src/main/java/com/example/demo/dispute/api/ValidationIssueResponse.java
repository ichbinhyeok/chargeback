package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueTargetScope;
import com.example.demo.dispute.domain.IssueSeverity;

public record ValidationIssueResponse(
        String code,
        String ruleId,
        IssueSeverity severity,
        String message,
        IssueTargetScope targetScope,
        EvidenceType targetEvidenceType,
        String targetFileId,
        String targetGroupKey,
        FixStrategy fixStrategy
) {
}

