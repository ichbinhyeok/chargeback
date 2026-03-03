package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.FixJobResponse;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FixJobStatus;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.persistence.FixJobEntity;
import com.example.demo.dispute.persistence.FixJobRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AutoFixService {

    private static final String FAIL_CODE_NOTHING_TO_FIX = "ERR_FIX_NOTHING_TO_FIX";
    private static final String FAIL_CODE_INTERNAL = "ERR_FIX_INTERNAL";

    private final CaseService caseService;
    private final FixJobRepository fixJobRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceFileService evidenceFileService;
    private final ValidationService validationService;
    private final ValidationHistoryService validationHistoryService;
    private final AuditLogService auditLogService;

    public AutoFixService(
            CaseService caseService,
            FixJobRepository fixJobRepository,
            EvidenceFileRepository evidenceFileRepository,
            EvidenceFileService evidenceFileService,
            ValidationService validationService,
            ValidationHistoryService validationHistoryService,
            AuditLogService auditLogService
    ) {
        this.caseService = caseService;
        this.fixJobRepository = fixJobRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceFileService = evidenceFileService;
        this.validationService = validationService;
        this.validationHistoryService = validationHistoryService;
        this.auditLogService = auditLogService;
    }

    public FixJobResponse requestAutoFix(UUID caseId) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        caseService.transitionState(disputeCase, CaseState.FIXING);

        FixJobEntity job = new FixJobEntity();
        job.setDisputeCase(disputeCase);
        job.setStatus(FixJobStatus.QUEUED);
        job.setSummary("Auto-fix job queued.");
        FixJobEntity saved = fixJobRepository.save(job);

        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "AUTO_FIX_REQUESTED",
                "jobId=" + saved.getId()
        );

        FixJobEntity processed = processFixJob(saved.getId());
        return toResponse(processed);
    }

    @Transactional(readOnly = true)
    public FixJobResponse getFixJob(UUID caseId, UUID jobId) {
        caseService.getCase(caseId);
        FixJobEntity job = fixJobRepository.findByIdAndDisputeCaseId(jobId, caseId)
                .orElseThrow(() -> new EntityNotFoundException("fix job not found: " + jobId));
        return toResponse(job);
    }

    private FixJobEntity processFixJob(UUID jobId) {
        FixJobEntity job = fixJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("fix job not found: " + jobId));
        DisputeCase disputeCase = job.getDisputeCase();

        try {
            job.setStatus(FixJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            job.setSummary("Auto-fix in progress.");
            fixJobRepository.save(job);

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_STARTED",
                    "jobId=" + job.getId()
            );

            int removedCount = dedupeFilesByEvidenceType(disputeCase.getId());
            if (removedCount == 0) {
                List<EvidenceFileInput> unchangedFiles = evidenceFileService.listAsValidationInputs(disputeCase.getId());
                ValidateCaseResponse unchangedValidation = validationService.validate(disputeCase, unchangedFiles, false);
                validationHistoryService.record(disputeCase, unchangedValidation, ValidationSource.AUTO_FIX, false);
                caseService.transitionState(disputeCase, unchangedValidation.passed() ? CaseState.READY : CaseState.BLOCKED);

                return failJob(
                        job,
                        FAIL_CODE_NOTHING_TO_FIX,
                        "No supported auto-fix issue found. Currently only multi-file-per-type is supported."
                );
            }

            List<EvidenceFileInput> files = evidenceFileService.listAsValidationInputs(disputeCase.getId());
            ValidateCaseResponse response = validationService.validate(disputeCase, files, false);
            validationHistoryService.record(disputeCase, response, ValidationSource.AUTO_FIX, false);
            caseService.transitionState(disputeCase, response.passed() ? CaseState.READY : CaseState.BLOCKED);

            job.setStatus(FixJobStatus.SUCCEEDED);
            job.setSummary(
                    "Removed " + removedCount + " duplicate file(s). Validation passed=" + response.passed() + "."
            );
            job.setFailCode(null);
            job.setFailMessage(null);
            job.setFinishedAt(Instant.now());
            FixJobEntity saved = fixJobRepository.save(job);

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_COMPLETED",
                    "jobId=" + saved.getId() + ",removedFiles=" + removedCount + ",passed=" + response.passed()
            );
            return saved;
        } catch (RuntimeException ex) {
            caseService.transitionState(disputeCase, CaseState.BLOCKED);
            return failJob(job, FAIL_CODE_INTERNAL, "Auto-fix failed: " + ex.getMessage());
        }
    }

    private int dedupeFilesByEvidenceType(UUID caseId) {
        List<EvidenceFileEntity> allFiles = evidenceFileRepository.findByDisputeCaseId(caseId);
        Map<EvidenceType, List<EvidenceFileEntity>> grouped = new EnumMap<>(EvidenceType.class);
        for (EvidenceFileEntity file : allFiles) {
            grouped.computeIfAbsent(file.getEvidenceType(), ignored -> new ArrayList<>()).add(file);
        }

        int removedCount = 0;
        for (List<EvidenceFileEntity> filesByType : grouped.values()) {
            if (filesByType.size() <= 1) {
                continue;
            }

            List<EvidenceFileEntity> ordered = filesByType.stream()
                    .sorted(Comparator.comparing(EvidenceFileEntity::getCreatedAt).reversed()
                            .thenComparing(EvidenceFileEntity::getId, Comparator.reverseOrder()))
                    .toList();

            List<EvidenceFileEntity> toDelete = ordered.subList(1, ordered.size());
            for (EvidenceFileEntity entity : toDelete) {
                deletePathQuietly(Path.of(entity.getStoragePath()));
                evidenceFileRepository.delete(entity);
                removedCount++;
            }
        }

        return removedCount;
    }

    private FixJobEntity failJob(FixJobEntity job, String failCode, String failMessage) {
        DisputeCase disputeCase = job.getDisputeCase();
        job.setStatus(FixJobStatus.FAILED);
        job.setSummary("Auto-fix failed.");
        job.setFailCode(failCode);
        job.setFailMessage(failMessage);
        job.setFinishedAt(Instant.now());
        FixJobEntity saved = fixJobRepository.save(job);

        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "AUTO_FIX_FAILED",
                "jobId=" + saved.getId() + ",failCode=" + failCode
        );
        return saved;
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Ignore cleanup failures; retention sweep handles leftovers.
        }
    }

    private FixJobResponse toResponse(FixJobEntity job) {
        return new FixJobResponse(
                job.getId(),
                job.getDisputeCase().getId(),
                job.getStatus(),
                job.getSummary(),
                job.getFailCode(),
                job.getFailMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
