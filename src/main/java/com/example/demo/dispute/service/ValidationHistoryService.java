package com.example.demo.dispute.service;

import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.api.ValidationIssueResponse;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.ValidationIssueEntity;
import com.example.demo.dispute.persistence.ValidationIssueRepository;
import com.example.demo.dispute.persistence.ValidationRunEntity;
import com.example.demo.dispute.persistence.ValidationRunRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ValidationHistoryService {

    private final ValidationRunRepository validationRunRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final AuditLogService auditLogService;

    public ValidationHistoryService(
            ValidationRunRepository validationRunRepository,
            ValidationIssueRepository validationIssueRepository,
            AuditLogService auditLogService
    ) {
        this.validationRunRepository = validationRunRepository;
        this.validationIssueRepository = validationIssueRepository;
        this.auditLogService = auditLogService;
    }

    public ValidationRunEntity record(
            DisputeCase disputeCase,
            ValidateCaseResponse response,
            ValidationSource source,
            boolean earlySubmit
    ) {
        int nextRunNo = validationRunRepository.findFirstByDisputeCaseIdOrderByRunNoDesc(disputeCase.getId())
                .map(run -> run.getRunNo() + 1)
                .orElse(1);

        ValidationRunEntity run = new ValidationRunEntity();
        run.setDisputeCase(disputeCase);
        run.setRunNo(nextRunNo);
        run.setPassed(response.passed());
        run.setSource(source);
        run.setEarlySubmit(earlySubmit);
        ValidationRunEntity savedRun = validationRunRepository.save(run);

        List<ValidationIssueEntity> issueEntities = response.issues().stream()
                .map(issue -> toIssueEntity(savedRun, issue))
                .toList();
        validationIssueRepository.saveAll(issueEntities);
        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "VALIDATION_RECORDED",
                "runNo=" + savedRun.getRunNo() + ",source=" + source + ",passed=" + response.passed()
        );

        return savedRun;
    }

    private ValidationIssueEntity toIssueEntity(ValidationRunEntity run, ValidationIssueResponse issue) {
        ValidationIssueEntity entity = new ValidationIssueEntity();
        entity.setValidationRun(run);
        entity.setCode(issue.code());
        entity.setRuleId(issue.ruleId());
        entity.setSeverity(issue.severity());
        entity.setMessage(issue.message());
        return entity;
    }
}
