package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CaseReportResponse;
import com.example.demo.dispute.api.EvidenceFileReportResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.api.ValidationRunReportResponse;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.persistence.ValidationIssueRepository;
import com.example.demo.dispute.persistence.ValidationRunEntity;
import com.example.demo.dispute.persistence.ValidationRunRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CaseReportService {

    private final CaseService caseService;
    private final EvidenceFileRepository evidenceFileRepository;
    private final ValidationRunRepository validationRunRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final ValidationIssueContractResolver validationIssueContractResolver;

    public CaseReportService(
            CaseService caseService,
            EvidenceFileRepository evidenceFileRepository,
            ValidationRunRepository validationRunRepository,
            ValidationIssueRepository validationIssueRepository,
            ValidationIssueContractResolver validationIssueContractResolver
    ) {
        this.caseService = caseService;
        this.evidenceFileRepository = evidenceFileRepository;
        this.validationRunRepository = validationRunRepository;
        this.validationIssueRepository = validationIssueRepository;
        this.validationIssueContractResolver = validationIssueContractResolver;
    }

    public CaseReportResponse getReport(java.util.UUID caseId) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        List<EvidenceFileReportResponse> files = evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .map(this::toEvidenceFileReport)
                .toList();

        ValidationRunReportResponse latestValidation = validationRunRepository
                .findFirstByDisputeCaseIdOrderByRunNoDesc(caseId)
                .map(this::toValidationRunReport)
                .orElse(null);

        return new CaseReportResponse(
                disputeCase.getId(),
                disputeCase.getCaseToken(),
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getReasonCode(),
                disputeCase.getCardNetwork(),
                disputeCase.getState(),
                disputeCase.getCreatedAt(),
                latestValidation,
                files
        );
    }

    private ValidationRunReportResponse toValidationRunReport(ValidationRunEntity run) {
        List<ValidationIssueResponse> issues = validationIssueRepository
                .findByValidationRunIdOrderByCodeAsc(run.getId())
                .stream()
                .map(item -> validationIssueContractResolver.rehydrate(
                        item.getCode(),
                        item.getRuleId(),
                        item.getSeverity(),
                        item.getMessage(),
                        item.getTargetScope(),
                        item.getTargetEvidenceType(),
                        item.getTargetFileId(),
                        item.getTargetGroupKey(),
                        item.getFixStrategy()
                ))
                .toList();

        return new ValidationRunReportResponse(
                run.getId(),
                run.getRunNo(),
                run.isPassed(),
                run.getSource(),
                run.isEarlySubmit(),
                run.getCreatedAt(),
                issues
        );
    }

    private EvidenceFileReportResponse toEvidenceFileReport(EvidenceFileEntity file) {
        return new EvidenceFileReportResponse(
                file.getId(),
                file.getEvidenceType(),
                file.getOriginalName(),
                file.getFileFormat(),
                file.getSizeBytes(),
                file.getPageCount(),
                file.isExternalLinkDetected(),
                file.isPdfACompliant(),
                file.isPdfPortfolio(),
                file.getCreatedAt()
        );
    }
}

