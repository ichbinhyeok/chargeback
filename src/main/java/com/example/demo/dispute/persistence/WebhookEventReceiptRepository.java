package com.example.demo.dispute.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventReceiptRepository extends JpaRepository<WebhookEventReceiptEntity, UUID> {
    boolean existsByProviderAndEventTypeAndEventId(String provider, String eventType, String eventId);

    long countByProviderAndEventTypeAndEventId(String provider, String eventType, String eventId);

    void deleteByDisputeCaseId(UUID caseId);
}
