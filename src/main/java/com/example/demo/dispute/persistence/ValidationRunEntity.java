package com.example.demo.dispute.persistence;

import com.example.demo.dispute.domain.ValidationSource;
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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_runs")
public class ValidationRunEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private DisputeCase disputeCase;

    @Column(name = "run_no", nullable = false)
    private int runNo;

    @Column(name = "is_passed", nullable = false)
    private boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ValidationSource source;

    @Column(name = "early_submit", nullable = false)
    private boolean earlySubmit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DisputeCase getDisputeCase() {
        return disputeCase;
    }

    public void setDisputeCase(DisputeCase disputeCase) {
        this.disputeCase = disputeCase;
    }

    public int getRunNo() {
        return runNo;
    }

    public void setRunNo(int runNo) {
        this.runNo = runNo;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public ValidationSource getSource() {
        return source;
    }

    public void setSource(ValidationSource source) {
        this.source = source;
    }

    public boolean isEarlySubmit() {
        return earlySubmit;
    }

    public void setEarlySubmit(boolean earlySubmit) {
        this.earlySubmit = earlySubmit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

