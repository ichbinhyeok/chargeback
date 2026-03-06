package com.example.demo.dispute.service;

import com.example.demo.dispute.persistence.DisputeCase;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RetentionPolicyService {

    private final int retentionDays;
    private final int dueDateBufferDays;

    public RetentionPolicyService(
            @Value("${app.retention.days:30}") int retentionDays,
            @Value("${app.retention.due-date-buffer-days:7}") int dueDateBufferDays
    ) {
        this.retentionDays = Math.max(1, retentionDays);
        this.dueDateBufferDays = Math.max(0, dueDateBufferDays);
    }

    public int retentionDays() {
        return retentionDays;
    }

    public int dueDateBufferDays() {
        return dueDateBufferDays;
    }

    public Instant resolveExpiry(DisputeCase disputeCase) {
        Instant createdBase = disputeCase.getCreatedAt().plus(retentionDays, ChronoUnit.DAYS);
        if (disputeCase.getDueAt() == null) {
            return createdBase;
        }

        // Keep case until the end of due-date + buffer day in server timezone.
        Instant dueDateBase = disputeCase.getDueAt()
                .plusDays(dueDateBufferDays + 1L)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        return createdBase.isAfter(dueDateBase) ? createdBase : dueDateBase;
    }

    public boolean isExpired(DisputeCase disputeCase, Instant now) {
        return !resolveExpiry(disputeCase).isAfter(now);
    }
}
