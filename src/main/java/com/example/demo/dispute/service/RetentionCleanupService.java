package com.example.demo.dispute.service;

import com.example.demo.dispute.persistence.DisputeCaseRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionCleanupService {

    private final DisputeCaseRepository disputeCaseRepository;
    private final CaseService caseService;
    private final int retentionDays;

    public RetentionCleanupService(
            DisputeCaseRepository disputeCaseRepository,
            CaseService caseService,
            @Value("${app.retention.days:7}") int retentionDays
    ) {
        this.disputeCaseRepository = disputeCaseRepository;
        this.caseService = caseService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredCases() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        disputeCaseRepository.findByCreatedAtBefore(cutoff)
                .forEach(disputeCase -> caseService.deleteCase(disputeCase.getId()));
    }
}

