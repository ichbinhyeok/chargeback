package com.example.demo.dispute.persistence;

import com.example.demo.dispute.domain.FixJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixJobRepository extends JpaRepository<FixJobEntity, UUID> {
    Optional<FixJobEntity> findByIdAndDisputeCaseId(UUID jobId, UUID caseId);

    Optional<FixJobEntity> findFirstByDisputeCaseIdAndStatusOrderByCreatedAtDesc(UUID caseId, FixJobStatus status);

    Optional<FixJobEntity> findFirstByDisputeCaseIdAndStatusInOrderByCreatedAtDesc(
            UUID caseId,
            Collection<FixJobStatus> statuses
    );

    List<FixJobEntity> findTop5ByStatusOrderByCreatedAtAsc(FixJobStatus status);

    void deleteByDisputeCaseId(UUID caseId);
}
