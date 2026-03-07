package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.domain.IssueSeverity;
import com.example.demo.dispute.persistence.ValidationIssueEntity;
import com.example.demo.dispute.persistence.ValidationIssueRepository;
import com.example.demo.dispute.persistence.ValidationRunEntity;
import com.example.demo.dispute.persistence.ValidationRunRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExportMetricsService {

    private final ValidationRunRepository validationRunRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public ExportMetricsService(
            ValidationRunRepository validationRunRepository,
            ValidationIssueRepository validationIssueRepository
    ) {
        this.validationRunRepository = validationRunRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    public ExportMetrics summarize(
            CaseReportResponse report,
            ReadinessService.ReadinessSummary readiness,
            ReasonCodeChecklistService.ReasonChecklist reasonChecklist
    ) {
        int actionableNow = readiness.blockedCount() + readiness.fixableCount();
        int totalIssuesNow = actionableNow + readiness.warningCount();

        int requiredTotal = reasonChecklist == null ? 0 : reasonChecklist.requiredEvidence().size();
        int requiredReady = Math.max(0, requiredTotal - readiness.missingRequiredEvidenceTypes().size());
        int requiredCoveragePercent = requiredTotal == 0
                ? 100
                : (int) Math.round((requiredReady * 100.0d) / requiredTotal);

        ValidationDelta delta = previousValidationDelta(report.caseId(), actionableNow);

        return new ExportMetrics(
                readiness.score(),
                readiness.label(),
                actionableNow,
                readiness.blockedCount(),
                readiness.fixableCount(),
                readiness.warningCount(),
                totalIssuesNow,
                requiredReady,
                requiredTotal,
                requiredCoveragePercent,
                requiredTotal > 0,
                readiness.missingRequiredEvidenceTypes().size(),
                delta.hasPreviousValidation(),
                delta.previousRunNo(),
                delta.previousActionableCount(),
                delta.actionableReduction()
        );
    }

    private ValidationDelta previousValidationDelta(UUID caseId, int currentActionableCount) {
        List<ValidationRunEntity> recentRuns = validationRunRepository.findTop2ByDisputeCaseIdOrderByRunNoDesc(caseId);
        if (recentRuns.size() < 2) {
            return new ValidationDelta(false, 0, 0, 0);
        }

        ValidationRunEntity previousRun = recentRuns.get(1);
        List<ValidationIssueEntity> previousIssues = validationIssueRepository.findByValidationRunIdOrderByCodeAsc(previousRun.getId());
        int previousActionableCount = 0;
        for (ValidationIssueEntity issue : previousIssues) {
            if (issue.getSeverity() == IssueSeverity.BLOCKED || issue.getSeverity() == IssueSeverity.FIXABLE) {
                previousActionableCount++;
            }
        }

        return new ValidationDelta(
                true,
                previousRun.getRunNo(),
                previousActionableCount,
                Math.max(0, previousActionableCount - currentActionableCount)
        );
    }

    public record ExportMetrics(
            int readinessScore,
            String readinessLabel,
            int actionableIssueCount,
            int blockedCount,
            int fixableCount,
            int warningCount,
            int totalIssueCount,
            int requiredEvidenceReadyCount,
            int requiredEvidenceTotalCount,
            int requiredEvidenceCoveragePercent,
            boolean hasRequiredEvidenceTarget,
            int missingRequiredEvidenceCount,
            boolean hasPreviousValidation,
            int previousRunNo,
            int previousActionableCount,
            int actionableReduction
    ) {
    }

    private record ValidationDelta(
            boolean hasPreviousValidation,
            int previousRunNo,
            int previousActionableCount,
            int actionableReduction
    ) {
    }
}
