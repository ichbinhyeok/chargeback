package com.example.demo.dispute.service;

import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.domain.IssueTargetScope;
import org.springframework.stereotype.Component;

@Component
public class ValidationIssueContractResolver {

    public ValidationIssueResponse build(
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
        return new ValidationIssueResponse(
                code,
                ruleId,
                severity,
                message,
                targetScope,
                targetEvidenceType,
                targetFileId,
                targetGroupKey,
                normalizeFixStrategy(fixStrategy, severity)
        );
    }

    public ValidationIssueResponse rehydrate(String code, String ruleId, IssueSeverity severity, String message) {
        return build(
                code,
                ruleId,
                severity,
                message,
                inferTargetScope(code),
                null,
                null,
                null,
                inferFixStrategy(code, severity)
        );
    }

    private IssueTargetScope inferTargetScope(String code) {
        if (code != null && code.contains("MULTI_FILE_PER_TYPE")) {
            return IssueTargetScope.EVIDENCE_TYPE;
        }
        return IssueTargetScope.GLOBAL;
    }

    private FixStrategy inferFixStrategy(String code, IssueSeverity severity) {
        if (severity != IssueSeverity.FIXABLE) {
            return FixStrategy.MANUAL;
        }
        if (code != null && code.contains("MULTI_FILE_PER_TYPE")) {
            return FixStrategy.MERGE_PER_TYPE;
        }
        if ("ERR_SHPFY_FILE_TOO_LARGE".equals(code)) {
            return FixStrategy.COMPRESS_SHOPIFY_IMAGE_IF_IMAGE;
        }
        return FixStrategy.NONE;
    }

    private FixStrategy normalizeFixStrategy(FixStrategy fixStrategy, IssueSeverity severity) {
        if (severity == IssueSeverity.FIXABLE) {
            return fixStrategy == null ? FixStrategy.NONE : fixStrategy;
        }
        return fixStrategy == null ? FixStrategy.MANUAL : fixStrategy;
    }
}

