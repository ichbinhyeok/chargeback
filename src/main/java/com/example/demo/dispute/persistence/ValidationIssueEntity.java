package com.example.demo.dispute.persistence;

import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixStrategy;
import com.example.demo.dispute.domain.IssueTargetScope;
import com.example.demo.dispute.domain.IssueSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "validation_issues")
public class ValidationIssueEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_run_id", nullable = false)
    private ValidationRunEntity validationRun;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private IssueSeverity severity;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_scope")
    private IssueTargetScope targetScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_evidence_type")
    private EvidenceType targetEvidenceType;

    @Column(name = "target_file_id", length = 64)
    private String targetFileId;

    @Column(name = "target_group_key", length = 128)
    private String targetGroupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "fix_strategy")
    private FixStrategy fixStrategy;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ValidationRunEntity getValidationRun() {
        return validationRun;
    }

    public void setValidationRun(ValidationRunEntity validationRun) {
        this.validationRun = validationRun;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IssueSeverity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public IssueTargetScope getTargetScope() {
        return targetScope;
    }

    public void setTargetScope(IssueTargetScope targetScope) {
        this.targetScope = targetScope;
    }

    public EvidenceType getTargetEvidenceType() {
        return targetEvidenceType;
    }

    public void setTargetEvidenceType(EvidenceType targetEvidenceType) {
        this.targetEvidenceType = targetEvidenceType;
    }

    public String getTargetFileId() {
        return targetFileId;
    }

    public void setTargetFileId(String targetFileId) {
        this.targetFileId = targetFileId;
    }

    public String getTargetGroupKey() {
        return targetGroupKey;
    }

    public void setTargetGroupKey(String targetGroupKey) {
        this.targetGroupKey = targetGroupKey;
    }

    public FixStrategy getFixStrategy() {
        return fixStrategy;
    }

    public void setFixStrategy(FixStrategy fixStrategy) {
        this.fixStrategy = fixStrategy;
    }
}

