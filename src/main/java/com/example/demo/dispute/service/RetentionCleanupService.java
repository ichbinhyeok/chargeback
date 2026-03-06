package com.example.demo.dispute.service;

import com.example.demo.dispute.persistence.DisputeCaseRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionCleanupService {

    private final DisputeCaseRepository disputeCaseRepository;
    private final CaseService caseService;
    private final RetentionPolicyService retentionPolicyService;

    public RetentionCleanupService(
            DisputeCaseRepository disputeCaseRepository,
            CaseService caseService,
            RetentionPolicyService retentionPolicyService
    ) {
        this.disputeCaseRepository = disputeCaseRepository;
        this.caseService = caseService;
        this.retentionPolicyService = retentionPolicyService;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredCases() {
        Instant now = Instant.now();
        // Query only cases old enough to be potentially expired by base retention.
        Instant cutoff = now.minus(retentionPolicyService.retentionDays(), ChronoUnit.DAYS);
        disputeCaseRepository.findByCreatedAtBefore(cutoff)
                .stream()
                .filter(disputeCase -> retentionPolicyService.isExpired(disputeCase, now))
                .forEach(disputeCase -> caseService.deleteCase(disputeCase.getId()));
    }
}

