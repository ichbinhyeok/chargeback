package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.ReclassifyFileRequest;
import com.example.demo.dispute.api.UploadFileResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.service.pdf.PdfMetadata;
import com.example.demo.dispute.service.pdf.PdfMetadataExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class EvidenceFileService {

    private final CaseService caseService;
    private final EvidenceFileRepository evidenceFileRepository;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public EvidenceFileService(
            CaseService caseService,
            EvidenceFileRepository evidenceFileRepository,
            PdfMetadataExtractor pdfMetadataExtractor,
            AuditLogService auditLogService,
            @Value("${app.storage.root:./data/evidence}") String storageRoot
    ) {
        this.caseService = caseService;
        this.evidenceFileRepository = evidenceFileRepository;
        this.pdfMetadataExtractor = pdfMetadataExtractor;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageRoot);
    }

    public UploadFileResponse upload(UUID caseId, EvidenceType evidenceType, MultipartFile file) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        caseService.transitionState(disputeCase, CaseState.UPLOADING);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("uploaded file is empty");
        }

        FileFormat format = detectFormat(file.getOriginalFilename(), file.getContentType());
        String extension = extensionFor(format);
        Path savedPath = saveFile(disputeCase.getId(), file, extension);

        PdfMetadata pdfMetadata = format == FileFormat.PDF
                ? pdfMetadataExtractor.extract(savedPath)
                : new PdfMetadata(1, false, true, false);

        EvidenceFileEntity entity = new EvidenceFileEntity();
        entity.setDisputeCase(disputeCase);
        entity.setEvidenceType(evidenceType);
        entity.setOriginalName(defaultString(file.getOriginalFilename(), "uploaded" + extension));
        entity.setContentType(file.getContentType());
        entity.setStoragePath(savedPath.toString());
        entity.setSizeBytes(file.getSize());
        entity.setPageCount(pdfMetadata.pageCount());
        entity.setFileFormat(format);
        entity.setPdfACompliant(pdfMetadata.pdfACompliant());
        entity.setPdfPortfolio(pdfMetadata.pdfPortfolio());
        entity.setExternalLinkDetected(pdfMetadata.externalLinkDetected());

        EvidenceFileEntity saved = evidenceFileRepository.save(entity);
        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "FILE_UPLOADED",
                "fileId=" + saved.getId() + ",evidenceType=" + saved.getEvidenceType() + ",format=" + saved.getFileFormat()
        );

        return toUploadResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EvidenceFileInput> listAsValidationInputs(UUID caseId) {
        caseService.getCase(caseId);
        return evidenceFileRepository.findByDisputeCaseId(caseId).stream()
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
    }

    @Transactional(readOnly = true)
    public List<UploadFileResponse> listFiles(UUID caseId) {
        caseService.getCase(caseId);
        return evidenceFileRepository.findByDisputeCaseId(caseId).stream()
                .map(this::toUploadResponse)
                .toList();
    }

    public UploadFileResponse reclassify(UUID caseId, UUID fileId, ReclassifyFileRequest request) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        EvidenceFileEntity entity = evidenceFileRepository.findByIdAndDisputeCaseId(fileId, caseId)
                .orElseThrow(() -> new IllegalArgumentException("file not found in case"));

        EvidenceType previousType = entity.getEvidenceType();
        entity.setEvidenceType(request.evidenceType());
        EvidenceFileEntity saved = evidenceFileRepository.save(entity);
        auditLogService.log(
                disputeCase,
                "SYSTEM",
                "FILE_RECLASSIFIED",
                "fileId=" + saved.getId() + ",from=" + previousType + ",to=" + saved.getEvidenceType()
        );
        return toUploadResponse(saved);
    }

    private Path saveFile(UUID caseId, MultipartFile file, String extension) {
        try {
            Path caseDir = storageRoot.resolve(caseId.toString());
            Files.createDirectories(caseDir);
            Path target = caseDir.resolve(UUID.randomUUID() + extension);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    private FileFormat detectFormat(String originalName, String contentType) {
        String lowerName = defaultString(originalName, "").toLowerCase(Locale.ROOT);
        String lowerContentType = defaultString(contentType, "").toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".pdf") || lowerContentType.contains("pdf")) {
            return FileFormat.PDF;
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerContentType.contains("jpeg")) {
            return FileFormat.JPEG;
        }
        if (lowerName.endsWith(".png") || lowerContentType.contains("png")) {
            return FileFormat.PNG;
        }

        throw new IllegalArgumentException("unsupported file format");
    }

    private String extensionFor(FileFormat format) {
        return switch (format) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
        };
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private UploadFileResponse toUploadResponse(EvidenceFileEntity saved) {
        return new UploadFileResponse(
                saved.getId(),
                saved.getEvidenceType(),
                saved.getFileFormat(),
                saved.getSizeBytes(),
                saved.getPageCount(),
                saved.isExternalLinkDetected(),
                saved.isPdfACompliant(),
                saved.isPdfPortfolio()
        );
    }
}
