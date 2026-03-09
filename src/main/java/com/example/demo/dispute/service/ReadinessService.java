package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.IssueSeverity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReadinessService {

    private final PolicyCatalogService policyCatalogService;
    private final EvidenceFactsService evidenceFactsService;

    public ReadinessService(
            PolicyCatalogService policyCatalogService,
            EvidenceFactsService evidenceFactsService
    ) {
        this.policyCatalogService = policyCatalogService;
        this.evidenceFactsService = evidenceFactsService;
    }

    public ReadinessSummary summarize(CaseReportResponse report) {
        int blocked = 0;
        int fixable = 0;
        int warning = 0;
        if (report.latestValidation() != null) {
            for (ValidationIssueResponse issue : report.latestValidation().issues()) {
                if (issue.severity() == IssueSeverity.BLOCKED) {
                    blocked++;
                } else if (issue.severity() == IssueSeverity.FIXABLE) {
                    fixable++;
                } else if (issue.severity() == IssueSeverity.WARNING) {
                    warning++;
                }
            }
        }

        EnumSet<EvidenceType> presentTypes = EnumSet.noneOf(EvidenceType.class);
        report.files().forEach(file -> presentTypes.add(file.evidenceType()));

        PolicyCatalogService.ResolvedPolicy policy = policyCatalogService.resolve(
                report.platform(),
                report.productScope(),
                report.reasonCode(),
                report.cardNetwork()
        );
        List<EvidenceType> missingRequired = policy.requiredEvidenceTypes().stream()
                .filter(type -> !presentTypes.contains(type))
                .toList();
        List<EvidenceType> missingRecommended = policy.recommendedEvidenceTypes().stream()
                .filter(type -> !presentTypes.contains(type))
                .toList();
        EvidenceFactsService.CaseEvidenceFacts evidenceFacts = evidenceFactsService.analyze(
                report.caseId(),
                report.files()
        );

        int score = 100;
        score -= blocked * 30;
        score -= fixable * 12;
        score -= warning * 5;
        score -= Math.min(36, missingRequired.size() * 12);
        score -= Math.min(16, missingRecommended.size() * 4);
        score += Math.round((evidenceFacts.coherenceScore() - 50) / 4.0f);
        if (report.files().isEmpty()) {
            score = Math.min(score, 10);
        }
        if (report.latestValidation() == null && !report.files().isEmpty()) {
            score = Math.min(score, 70);
        }
        score = Math.max(0, Math.min(100, score));

        String label;
        if (score >= 90) {
            label = "Excellent";
        } else if (score >= 70) {
            label = "Good";
        } else if (score >= 50) {
            label = "Needs Work";
        } else {
            label = "Critical";
        }

        List<String> missingRequiredNames = new ArrayList<>();
        missingRequired.forEach(type -> missingRequiredNames.add(type.name()));
        List<String> missingRecommendedNames = new ArrayList<>();
        missingRecommended.forEach(type -> missingRecommendedNames.add(type.name()));

        List<String> missingAll = new ArrayList<>(missingRequiredNames);
        missingAll.addAll(missingRecommendedNames);
        return new ReadinessSummary(
                score,
                label,
                blocked,
                fixable,
                warning,
                missingAll,
                missingRequiredNames,
                missingRecommendedNames,
                evidenceFacts.coherenceScore(),
                evidenceFacts.coherenceHighlights()
        );
    }

    public int minimumCoreEvidenceTargetCount(CaseReportResponse report) {
        PolicyCatalogService.ResolvedPolicy policy = resolvePolicy(report);
        if (policy == null
                || policy.canonicalReasonKey() == null
                || policy.canonicalReasonKey().isBlank()
                || policy.requiredEvidenceTypes() == null
                || policy.requiredEvidenceTypes().size() < 3) {
            return 0;
        }
        return 2;
    }

    public int coreRequiredEvidenceReadyCount(CaseReportResponse report) {
        PolicyCatalogService.ResolvedPolicy policy = resolvePolicy(report);
        if (policy == null || policy.requiredEvidenceTypes() == null || policy.requiredEvidenceTypes().isEmpty()) {
            return 0;
        }

        EnumSet<EvidenceType> presentTypes = EnumSet.noneOf(EvidenceType.class);
        report.files().forEach(file -> presentTypes.add(file.evidenceType()));

        return (int) policy.requiredEvidenceTypes().stream()
                .distinct()
                .filter(presentTypes::contains)
                .count();
    }

    public boolean hasMinimumCoreEvidenceCoverage(CaseReportResponse report) {
        int targetCount = minimumCoreEvidenceTargetCount(report);
        if (targetCount <= 0) {
            return true;
        }
        return coreRequiredEvidenceReadyCount(report) >= targetCount;
    }

    private PolicyCatalogService.ResolvedPolicy resolvePolicy(CaseReportResponse report) {
        return policyCatalogService.resolve(
                report.platform(),
                report.productScope(),
                report.reasonCode(),
                report.cardNetwork()
        );
    }

    public record ReadinessSummary(
            int score,
            String label,
            int blockedCount,
            int fixableCount,
            int warningCount,
            List<String> missingEvidenceTypes,
            List<String> missingRequiredEvidenceTypes,
            List<String> missingRecommendedEvidenceTypes,
            int coherenceScore,
            List<String> coherenceHighlights
    ) {
    }
}
