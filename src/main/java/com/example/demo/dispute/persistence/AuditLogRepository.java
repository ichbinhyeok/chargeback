package com.example.demo.dispute.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    void deleteByDisputeCaseId(UUID caseId);
}
