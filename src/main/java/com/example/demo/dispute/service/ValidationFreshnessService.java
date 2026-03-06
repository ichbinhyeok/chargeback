package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.domain.ValidationFreshness;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.DisputeCaseRepository;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.persistence.ValidationRunEntity;
import com.example.demo.dispute.persistence.ValidationRunRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ValidationFreshnessService {

    private final ValidationRunRepository validationRunRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final DisputeCaseRepository disputeCaseRepository;
    private final InputFingerprintService inputFingerprintService;

    public ValidationFreshnessService(
            ValidationRunRepository validationRunRepository,
            EvidenceFileRepository evidenceFileRepository,
            DisputeCaseRepository disputeCaseRepository,
            InputFingerprintService inputFingerprintService
    ) {
        this.validationRunRepository = validationRunRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.disputeCaseRepository = disputeCaseRepository;
        this.inputFingerprintService = inputFingerprintService;
    }

    public ValidationFreshness freshness(UUID caseId) {
        ValidationRunEntity latest = validationRunRepository.findFirstByDisputeCaseIdOrderByRunNoDesc(caseId)
                .orElse(null);
        if (latest == null) {
            return ValidationFreshness.NEVER_RUN;
        }

        if (isLegacyStale(latest, caseId)) {
            return ValidationFreshness.STALE_FILES_CHANGED;
        }

        DisputeCase disputeCase = disputeCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("case not found: " + caseId));
        List<EvidenceFileInput> currentInputs = evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .map(file -> new EvidenceFileInput(
                        file.getEvidenceType(),
                        file.getFileFormat(),
                        file.getSizeBytes(),
                        file.getPageCount(),
                        file.isExternalLinkDetected(),
                        file.isPdfACompliant(),
                        file.isPdfPortfolio()
                ))
                .toList();
        String currentFingerprint = inputFingerprintService.fingerprint(disputeCase, currentInputs, latest.isEarlySubmit());
        if (!currentFingerprint.equals(latest.getInputFingerprint())) {
            return ValidationFreshness.STALE_FILES_CHANGED;
        }

        return ValidationFreshness.FRESH;
    }

    public boolean isFresh(UUID caseId) {
        return freshness(caseId) == ValidationFreshness.FRESH;
    }

    public boolean hasFreshPassedValidation(UUID caseId) {
        ValidationRunEntity latest = validationRunRepository.findFirstByDisputeCaseIdOrderByRunNoDesc(caseId)
                .orElse(null);
        if (latest == null || !latest.isPassed()) {
            return false;
        }
        return freshness(caseId) == ValidationFreshness.FRESH;
    }

    private boolean isLegacyStale(ValidationRunEntity latest, UUID caseId) {
        if (latest.getInputFingerprint() != null && !latest.getInputFingerprint().isBlank()) {
            return false;
        }
        return evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .anyMatch(file -> file.getCreatedAt().isAfter(latest.getCreatedAt()));
    }
}
