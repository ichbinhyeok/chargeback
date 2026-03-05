package com.example.demo.dispute.service;

import com.example.demo.dispute.api.CreateCaseRequest;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.persistence.AuditLogRepository;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.DisputeCaseRepository;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.persistence.FixJobRepository;
import com.example.demo.dispute.persistence.PaymentRepository;
import com.example.demo.dispute.persistence.ValidationIssueRepository;
import com.example.demo.dispute.persistence.ValidationRunRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CaseService {

    private final DisputeCaseRepository disputeCaseRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final FixJobRepository fixJobRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final ValidationRunRepository validationRunRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final Path storageRoot;
    private final boolean enforceCaseToken;

    public CaseService(
            DisputeCaseRepository disputeCaseRepository,
            EvidenceFileRepository evidenceFileRepository,
            FixJobRepository fixJobRepository,
            ValidationIssueRepository validationIssueRepository,
            ValidationRunRepository validationRunRepository,
            PaymentRepository paymentRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService,
            @Value("${app.storage.root:./data/evidence}") String storageRoot,
            @Value("${app.api.enforce-case-token:true}") boolean enforceCaseToken
    ) {
        this.disputeCaseRepository = disputeCaseRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.fixJobRepository = fixJobRepository;
        this.validationIssueRepository = validationIssueRepository;
        this.validationRunRepository = validationRunRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageRoot);
        this.enforceCaseToken = enforceCaseToken;
    }

    public DisputeCase createCase(CreateCaseRequest request) {
        if (!ProductScope.matchesPlatform(request.platform(), request.productScope())) {
            throw new IllegalArgumentException(
                    "product_scope does not match platform. platform=" + request.platform()
                            + ", product_scope=" + request.productScope()
            );
        }

        DisputeCase disputeCase = new DisputeCase();
        disputeCase.setCaseToken(generateCaseToken());
        disputeCase.setPlatform(request.platform());
        disputeCase.setProductScope(request.productScope());
        disputeCase.setReasonCode(request.reasonCode());
        disputeCase.setDueAt(request.dueAt());
        disputeCase.setCardNetwork(request.cardNetwork());
        disputeCase.setState(CaseState.CASE_CREATED);

        DisputeCase saved = disputeCaseRepository.save(disputeCase);
        auditLogService.log(saved, "SYSTEM", "CASE_CREATED", "platform=" + saved.getPlatform() + ",scope=" + saved.getProductScope());
        return saved;
    }

    public DisputeCase transitionState(DisputeCase disputeCase, CaseState nextState) {
        CaseState previousState = disputeCase.getState();
        disputeCase.setState(nextState);
        DisputeCase saved = disputeCaseRepository.save(disputeCase);
        auditLogService.log(
                saved,
                "SYSTEM",
                "CASE_STATE_CHANGED",
                "from=" + previousState + ",to=" + nextState
        );
        return saved;
    }

    public DisputeCase transitionState(UUID caseId, CaseState nextState) {
        DisputeCase disputeCase = getCase(caseId);
        return transitionState(disputeCase, nextState);
    }

    public void deleteCase(UUID caseId) {
        DisputeCase disputeCase = getCase(caseId);

        // Delete file artifacts on disk first.
        List<String> paths = evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .map(file -> file.getStoragePath())
                .toList();
        for (String storagePath : paths) {
            deletePathQuietly(Path.of(storagePath));
        }
        // Delete case directory if empty.
        deletePathQuietly(storageRoot.resolve(caseId.toString()));

        validationIssueRepository.deleteByValidationRunDisputeCaseId(caseId);
        validationRunRepository.deleteByDisputeCaseId(caseId);
        fixJobRepository.deleteByDisputeCaseId(caseId);
        paymentRepository.deleteAll(paymentRepository.findByDisputeCaseId(caseId));
        auditLogRepository.deleteByDisputeCaseId(caseId);
        evidenceFileRepository.deleteByDisputeCaseId(caseId);
        disputeCaseRepository.delete(disputeCase);
    }

    @Transactional(readOnly = true)
    public DisputeCase getCase(UUID caseId) {
        return disputeCaseRepository.findById(caseId)
                .orElseThrow(() -> new EntityNotFoundException("case not found: " + caseId));
    }

    @Transactional(readOnly = true)
    public DisputeCase getCaseByToken(String caseToken) {
        return disputeCaseRepository.findByCaseToken(caseToken)
                .orElseThrow(() -> new EntityNotFoundException("case not found for token: " + caseToken));
    }

    @Transactional(readOnly = true)
    public void assertCaseToken(UUID caseId, String caseToken) {
        if (!enforceCaseToken) {
            return;
        }
        if (caseToken == null || caseToken.isBlank()) {
            throw new IllegalArgumentException("missing X-Case-Token header");
        }

        DisputeCase disputeCase = getCase(caseId);
        if (!caseToken.equals(disputeCase.getCaseToken())) {
            throw new IllegalArgumentException("invalid case token for case: " + caseId);
        }
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Keep delete flow resilient; leftovers are cleaned by retention jobs.
        }
    }

    private String generateCaseToken() {
        return "case_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
