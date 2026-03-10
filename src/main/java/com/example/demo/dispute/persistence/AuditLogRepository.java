package com.example.demo.dispute.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    long countByDisputeCaseIdAndAction(UUID caseId, String action);

    boolean existsByDisputeCaseIdAndActionAndMetadataContaining(UUID caseId, String action, String metadataFragment);

    void deleteByDisputeCaseId(UUID caseId);
}
