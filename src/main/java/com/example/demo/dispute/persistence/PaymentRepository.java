package com.example.demo.dispute.persistence;

import com.example.demo.dispute.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByCheckoutSessionId(String checkoutSessionId);

    Optional<PaymentEntity> findFirstByDisputeCaseIdOrderByCreatedAtDesc(UUID caseId);

    Optional<PaymentEntity> findFirstByDisputeCaseIdAndStatusOrderByCreatedAtDesc(UUID caseId, PaymentStatus status);

    boolean existsByDisputeCaseIdAndStatus(UUID caseId, PaymentStatus status);

    List<PaymentEntity> findByDisputeCaseId(UUID caseId);
}
