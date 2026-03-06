package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.RetentionPolicyService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class RetentionPolicyServiceTest {

    @Test
    void usesCreatedAtRetentionWhenDueDateMissing() {
        RetentionPolicyService service = new RetentionPolicyService(30, 7);
        DisputeCase disputeCase = new DisputeCase();
        Instant createdAt = Instant.parse("2026-03-01T00:00:00Z");
        disputeCase.setCreatedAt(createdAt);

        Instant expected = createdAt.plus(30, ChronoUnit.DAYS);
        assertEquals(expected, service.resolveExpiry(disputeCase));
    }

    @Test
    void extendsExpiryToDueDateBufferWhenLaterThanCreatedRetention() {
        RetentionPolicyService service = new RetentionPolicyService(30, 7);
        DisputeCase disputeCase = new DisputeCase();
        disputeCase.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        disputeCase.setDueAt(LocalDate.of(2026, 3, 28));

        Instant expected = LocalDate.of(2026, 4, 5)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        assertEquals(expected, service.resolveExpiry(disputeCase));
    }

    @Test
    void expiryDecisionUsesResolvedTimestamp() {
        RetentionPolicyService service = new RetentionPolicyService(30, 7);
        DisputeCase disputeCase = new DisputeCase();
        disputeCase.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        disputeCase.setDueAt(LocalDate.of(2026, 1, 20));

        Instant beforeExpiry = LocalDate.of(2026, 1, 27)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        Instant afterExpiry = LocalDate.of(2026, 2, 2)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        assertFalse(service.isExpired(disputeCase, beforeExpiry));
        assertTrue(service.isExpired(disputeCase, afterExpiry));
    }
}
