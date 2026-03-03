package com.example.demo.dispute.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationIssueRepository extends JpaRepository<ValidationIssueEntity, UUID> {
    List<ValidationIssueEntity> findByValidationRunIdOrderByCodeAsc(UUID validationRunId);

    void deleteByValidationRunDisputeCaseId(UUID caseId);
}
