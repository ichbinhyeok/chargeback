package com.example.demo.dispute.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixJobRepository extends JpaRepository<FixJobEntity, UUID> {
    Optional<FixJobEntity> findByIdAndDisputeCaseId(UUID jobId, UUID caseId);

    void deleteByDisputeCaseId(UUID caseId);
}
