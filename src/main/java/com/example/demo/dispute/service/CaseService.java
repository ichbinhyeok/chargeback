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
import com.example.demo.dispute.persistence.WebhookEventReceiptRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CaseService {

    private static final Map<CaseState, EnumSet<CaseState>> ALLOWED_TRANSITIONS = createTransitionMatrix();

    private final DisputeCaseRepository disputeCaseRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final FixJobRepository fixJobRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final ValidationRunRepository validationRunRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventReceiptRepository webhookEventReceiptRepository;
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
            WebhookEventReceiptRepository webhookEventReceiptRepository,
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
        this.webhookEventReceiptRepository = webhookEventReceiptRepository;
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
        disputeCase.setCaseToken(generateUniqueCaseToken());
        disputeCase.setPlatform(request.platform());
        disputeCase.setProductScope(request.productScope());
        disputeCase.setReasonCode(normalizeReasonCode(request.reasonCode()));
        disputeCase.setDueAt(request.dueAt());
        disputeCase.setCardNetwork(request.cardNetwork());
        disputeCase.setState(CaseState.CASE_CREATED);

        DisputeCase saved = disputeCaseRepository.save(disputeCase);
        auditLogService.log(saved, "SYSTEM", "CASE_CREATED", "platform=" + saved.getPlatform() + ",scope=" + saved.getProductScope());
        return saved;
    }

    public DisputeCase rotateCaseToken(String caseToken) {
        DisputeCase disputeCase = getCaseByToken(caseToken);
        String previousToken = disputeCase.getCaseToken();
        String rotatedToken = generateUniqueCaseToken();
        disputeCase.setCaseToken(rotatedToken);
        DisputeCase saved = disputeCaseRepository.save(disputeCase);

        auditLogService.log(
                saved,
                "SYSTEM",
                "CASE_TOKEN_ROTATED",
                "from=" + previousToken + ",to=" + rotatedToken
        );
        return saved;
    }

    public DisputeCase transitionState(DisputeCase disputeCase, CaseState nextState) {
        CaseState previousState = disputeCase.getState();
        if (previousState == nextState) {
            return disputeCase;
        }
        assertTransition(previousState, nextState);

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

    @Transactional(readOnly = true)
    public boolean canTransition(CaseState from, CaseState to) {
        if (from == to) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(CaseState.class)).contains(to);
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
        webhookEventReceiptRepository.deleteByDisputeCaseId(caseId);
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
        return "case_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateUniqueCaseToken() {
        String token = generateCaseToken();
        while (disputeCaseRepository.existsByCaseToken(token)) {
            token = generateCaseToken();
        }
        return token;
    }

    private void assertTransition(CaseState from, CaseState to) {
        if (!canTransition(from, to)) {
            throw new IllegalArgumentException("illegal state transition: " + from + " -> " + to);
        }
    }

    private String normalizeReasonCode(String reasonCode) {
        if (reasonCode == null) {
            return null;
        }
        String trimmed = reasonCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 80) {
            throw new IllegalArgumentException("reason_code is too long (max 80 chars)");
        }
        return trimmed;
    }

    private static Map<CaseState, EnumSet<CaseState>> createTransitionMatrix() {
        Map<CaseState, EnumSet<CaseState>> matrix = new EnumMap<>(CaseState.class);
        matrix.put(CaseState.CASE_CREATED, EnumSet.of(
                CaseState.UPLOADING,
                CaseState.VALIDATING,
                CaseState.ARCHIVED
        ));
        matrix.put(CaseState.UPLOADING, EnumSet.of(
                CaseState.VALIDATING,
                CaseState.FIXING,
                CaseState.BLOCKED,
                CaseState.ARCHIVED
        ));
        matrix.put(CaseState.VALIDATING, EnumSet.of(
                CaseState.READY,
                CaseState.BLOCKED,
                CaseState.UPLOADING,
                CaseState.ARCHIVED
        ));
        matrix.put(CaseState.FIXING, EnumSet.of(
                CaseState.READY,
                CaseState.BLOCKED,
                CaseState.UPLOADING,
                CaseState.ARCHIVED
        ));
        matrix.put(CaseState.BLOCKED, EnumSet.of(
                CaseState.UPLOADING,
                CaseState.VALIDATING,
                CaseState.FIXING,
                CaseState.ARCHIVED
        ));
        matrix.put(CaseState.READY, EnumSet.of(
                CaseState.PAID,
                CaseState.DOWNLOADED,
                CaseState.UPLOADING,
                CaseState.VALIDATING,
                CaseState.FIXING,
                CaseState.ARCHIVED
        ));
        matrix.put(CaseState.PAID, EnumSet.of(
                CaseState.DOWNLOADED,
                CaseState.ARCHIVED
        ));
        matrix.put(CaseState.DOWNLOADED, EnumSet.of(CaseState.ARCHIVED));
        matrix.put(CaseState.ARCHIVED, EnumSet.noneOf(CaseState.class));
        return matrix;
    }
}
