package com.example.demo.dispute.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationRunRepository extends JpaRepository<ValidationRunEntity, UUID> {
    Optional<ValidationRunEntity> findFirstByDisputeCaseIdOrderByRunNoDesc(UUID caseId);

    void deleteByDisputeCaseId(UUID caseId);
}
