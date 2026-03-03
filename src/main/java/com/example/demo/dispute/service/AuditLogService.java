package com.example.demo.dispute.service;

import com.example.demo.dispute.persistence.AuditLogEntity;
import com.example.demo.dispute.persistence.AuditLogRepository;
import com.example.demo.dispute.persistence.DisputeCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(DisputeCase disputeCase, String actorType, String action, String metadata) {
        AuditLogEntity log = new AuditLogEntity();
        log.setDisputeCase(disputeCase);
        log.setActorType(actorType);
        log.setAction(action);
        log.setMetadata(metadata);
        auditLogRepository.save(log);
    }
}

