package com.example.demo.dispute.api;

import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.service.CaseReportService;
import com.example.demo.dispute.service.CaseService;
import com.example.demo.dispute.service.EvidenceFileService;
import com.example.demo.dispute.service.AutoFixService;
import com.example.demo.dispute.service.ValidationHistoryService;
import com.example.demo.dispute.service.ValidationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService caseService;
    private final EvidenceFileService evidenceFileService;
    private final ValidationService validationService;
    private final ValidationHistoryService validationHistoryService;
    private final CaseReportService caseReportService;
    private final AutoFixService autoFixService;

    public CaseController(
            CaseService caseService,
            EvidenceFileService evidenceFileService,
            ValidationService validationService,
            ValidationHistoryService validationHistoryService,
            CaseReportService caseReportService,
            AutoFixService autoFixService
    ) {
        this.caseService = caseService;
        this.evidenceFileService = evidenceFileService;
        this.validationService = validationService;
        this.validationHistoryService = validationHistoryService;
        this.caseReportService = caseReportService;
        this.autoFixService = autoFixService;
    }

    @PostMapping
    public ResponseEntity<CreateCaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        DisputeCase disputeCase = caseService.createCase(request);
        CreateCaseResponse response = new CreateCaseResponse(
                disputeCase.getId(),
                disputeCase.getCaseToken(),
                disputeCase.getPlatform(),
                disputeCase.getProductScope(),
                disputeCase.getState()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{caseId}/validate")
    public ResponseEntity<ValidateCaseResponse> validate(
            @PathVariable UUID caseId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken,
            @Valid @RequestBody ValidateCaseRequest request
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        DisputeCase disputeCase = caseService.getCase(caseId);
        caseService.transitionState(disputeCase, CaseState.VALIDATING);
        ValidateCaseResponse response = validationService.validate(disputeCase, request);
        validationHistoryService.record(disputeCase, response, ValidationSource.REQUEST_FILES, request.earlySubmit());
        caseService.transitionState(disputeCase, response.passed() ? CaseState.READY : CaseState.BLOCKED);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{caseId}/files")
    public ResponseEntity<UploadFileResponse> uploadFile(
            @PathVariable UUID caseId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken,
            @RequestParam("evidenceType") EvidenceType evidenceType,
            @RequestParam("file") MultipartFile file
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        UploadFileResponse response = evidenceFileService.upload(caseId, evidenceType, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{caseId}/files")
    public ResponseEntity<List<UploadFileResponse>> files(
            @PathVariable UUID caseId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        return ResponseEntity.ok(evidenceFileService.listFiles(caseId));
    }

    @PatchMapping("/{caseId}/files/{fileId}/classification")
    public ResponseEntity<UploadFileResponse> reclassifyFile(
            @PathVariable UUID caseId,
            @PathVariable UUID fileId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken,
            @Valid @RequestBody ReclassifyFileRequest request
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        return ResponseEntity.ok(evidenceFileService.reclassify(caseId, fileId, request));
    }

    @PostMapping("/{caseId}/validate-stored")
    public ResponseEntity<ValidateCaseResponse> validateStored(
            @PathVariable UUID caseId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken,
            @RequestBody(required = false) ValidateStoredCaseRequest request
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        DisputeCase disputeCase = caseService.getCase(caseId);
        List<EvidenceFileInput> files = evidenceFileService.listAsValidationInputs(caseId);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("no uploaded files found for case");
        }

        caseService.transitionState(disputeCase, CaseState.VALIDATING);
        boolean earlySubmit = request != null && request.earlySubmit();
        ValidateCaseResponse response = validationService.validate(disputeCase, files, earlySubmit);
        validationHistoryService.record(disputeCase, response, ValidationSource.STORED_FILES, earlySubmit);
        caseService.transitionState(disputeCase, response.passed() ? CaseState.READY : CaseState.BLOCKED);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{caseId}/report")
    public ResponseEntity<CaseReportResponse> report(
            @PathVariable UUID caseId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        return ResponseEntity.ok(caseReportService.getReport(caseId));
    }

    @PostMapping("/{caseId}/fix")
    public ResponseEntity<FixJobResponse> requestFix(
            @PathVariable UUID caseId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        return ResponseEntity.ok(autoFixService.requestAutoFix(caseId));
    }

    @GetMapping("/{caseId}/fix/{jobId}")
    public ResponseEntity<FixJobResponse> getFixJob(
            @PathVariable UUID caseId,
            @PathVariable UUID jobId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        return ResponseEntity.ok(autoFixService.getFixJob(caseId, jobId));
    }

    @DeleteMapping("/{caseId}")
    public ResponseEntity<Void> deleteCase(
            @PathVariable UUID caseId,
            @RequestHeader(value = "X-Case-Token", required = false) String caseToken
    ) {
        caseService.assertCaseToken(caseId, caseToken);
        caseService.deleteCase(caseId);
        return ResponseEntity.noContent().build();
    }
}
