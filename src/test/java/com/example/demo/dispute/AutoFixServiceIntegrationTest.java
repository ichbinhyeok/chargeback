package com.example.demo.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.api.FixJobResponse;
import com.example.demo.dispute.domain.FixJobStatus;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.FixJobEntity;
import com.example.demo.dispute.persistence.FixJobRepository;
import com.example.demo.dispute.service.AutoFixService;
import com.example.demo.dispute.service.CaseService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.autofix.inline-processing=false",
        "app.autofix.worker.enabled=false",
        "app.autofix.recover-running-after-ms=1000"
})
class AutoFixServiceIntegrationTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private AutoFixService autoFixService;

    @Autowired
    private FixJobRepository fixJobRepository;

    @Test
    void requestAutoFixRequeuesStaleRunningJob() {
        DisputeCase disputeCase = caseService.createCase(new CreateCaseRequest(
                Platform.SHOPIFY,
                ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK,
                "product_not_received",
                null,
                null
        ));

        try {
            FixJobEntity staleJob = new FixJobEntity();
            staleJob.setDisputeCase(disputeCase);
            staleJob.setStatus(FixJobStatus.RUNNING);
            staleJob.setSummary("Auto-fix in progress.");
            staleJob.setCreatedAt(Instant.now().minusSeconds(10));
            staleJob.setStartedAt(Instant.now().minusSeconds(10));
            FixJobEntity saved = fixJobRepository.save(staleJob);

            FixJobResponse response = autoFixService.requestAutoFix(disputeCase.getId());

            assertEquals(saved.getId(), response.jobId());
            assertEquals(FixJobStatus.QUEUED, response.status());

            FixJobEntity refreshed = fixJobRepository.findById(saved.getId()).orElseThrow();
            assertEquals(FixJobStatus.QUEUED, refreshed.getStatus());
            assertNull(refreshed.getStartedAt());
            assertEquals("Recovered a stale auto-fix job after interruption. Re-queued.", refreshed.getSummary());
        } finally {
            caseService.deleteCase(disputeCase.getId());
        }
    }
}
