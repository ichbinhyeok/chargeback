package com.example.demo.dispute.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFileEntity, UUID> {
    List<EvidenceFileEntity> findByDisputeCaseId(UUID caseId);
    Optional<EvidenceFileEntity> findByIdAndDisputeCaseId(UUID fileId, UUID caseId);
    long countByDisputeCaseId(UUID caseId);

    void deleteByDisputeCaseId(UUID caseId);
}
