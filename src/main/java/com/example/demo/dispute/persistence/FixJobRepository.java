package com.example.demo.dispute.persistence;

import com.example.demo.dispute.domain.FixJobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixJobRepository extends JpaRepository<FixJobEntity, UUID> {
    Optional<FixJobEntity> findByIdAndDisputeCaseId(UUID jobId, UUID caseId);

    Optional<FixJobEntity> findFirstByDisputeCaseIdAndStatusOrderByCreatedAtDesc(UUID caseId, FixJobStatus status);

    void deleteByDisputeCaseId(UUID caseId);
}
