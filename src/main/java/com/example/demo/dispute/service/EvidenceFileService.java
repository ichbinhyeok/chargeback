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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
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
    private final int caseMaxFiles;

    public EvidenceFileService(
            CaseService caseService,
            EvidenceFileRepository evidenceFileRepository,
            PdfMetadataExtractor pdfMetadataExtractor,
            AuditLogService auditLogService,
            @Value("${app.storage.root:./data/evidence}") String storageRoot,
            @Value("${app.case.max-files:100}") int caseMaxFiles
    ) {
        this.caseService = caseService;
        this.evidenceFileRepository = evidenceFileRepository;
        this.pdfMetadataExtractor = pdfMetadataExtractor;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageRoot);
        this.caseMaxFiles = caseMaxFiles;
    }

    public UploadFileResponse upload(UUID caseId, EvidenceType evidenceType, MultipartFile file) {
        DisputeCase disputeCase = caseService.getCase(caseId);
        caseService.transitionState(disputeCase, CaseState.UPLOADING);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("uploaded file is empty");
        }
        long uploadedFileCount = evidenceFileRepository.countByDisputeCaseId(caseId);
        if (uploadedFileCount >= caseMaxFiles) {
            throw new IllegalArgumentException(
                    "file limit reached for this case (" + caseMaxFiles + "). Delete unused files before uploading more."
            );
        }

        StoredUpload storedUpload = storeUpload(disputeCase.getId(), file);
        FileFormat format = storedUpload.format();
        Path savedPath = storedUpload.savedPath();

        try {
            validateBinaryContent(format, savedPath);

            PdfMetadata pdfMetadata = format == FileFormat.PDF
                    ? pdfMetadataExtractor.extract(savedPath)
                    : new PdfMetadata(1, false, true, false);

            EvidenceFileEntity entity = new EvidenceFileEntity();
            entity.setDisputeCase(disputeCase);
            entity.setEvidenceType(evidenceType);
            entity.setOriginalName(storedUpload.storedOriginalName());
            entity.setContentType(storedUpload.contentType());
            entity.setStoragePath(savedPath.toString());
            entity.setSizeBytes(storedUpload.sizeBytes());
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
                    "fileId=" + saved.getId()
                            + ",evidenceType=" + saved.getEvidenceType()
                            + ",format=" + saved.getFileFormat()
                            + ",autoConverted=" + storedUpload.autoConverted()
                            + (storedUpload.sourceContentType() == null ? "" : ",sourceContentType=" + storedUpload.sourceContentType())
            );

            return toUploadResponse(saved);
        } catch (RuntimeException ex) {
            deleteStoredFileQuietly(savedPath);
            throw ex;
        }
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
        caseService.transitionState(disputeCase, CaseState.UPLOADING);
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

    private StoredUpload storeUpload(UUID caseId, MultipartFile file) {
        FileFormat directFormat = detectDirectFormat(file.getOriginalFilename(), file.getContentType());
        if (directFormat != null) {
            String extension = extensionFor(directFormat);
            Path savedPath = saveFile(caseId, file, extension);
            return new StoredUpload(
                    savedPath,
                    directFormat,
                    defaultString(file.getOriginalFilename(), "uploaded" + extension),
                    normalizedContentType(file.getContentType(), directFormat),
                    readStoredFileSize(savedPath),
                    false,
                    file.getContentType()
            );
        }

        return convertUnsupportedImageUpload(caseId, file);
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

    private Path saveBytes(UUID caseId, byte[] bytes, String extension) {
        try {
            Path caseDir = storageRoot.resolve(caseId.toString());
            Files.createDirectories(caseDir);
            Path target = caseDir.resolve(UUID.randomUUID() + extension);
            Files.write(target, bytes);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    private FileFormat detectDirectFormat(String originalName, String contentType) {
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

        return null;
    }

    private StoredUpload convertUnsupportedImageUpload(UUID caseId, MultipartFile file) {
        BufferedImage original;
        try (InputStream inputStream = file.getInputStream()) {
            original = ImageIO.read(inputStream);
        } catch (IOException ex) {
            throw unsupportedFormatException(ex);
        }

        if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
            throw unsupportedFormatException(null);
        }

        byte[] jpegBytes = writeImageAsJpeg(original);
        Path savedPath = saveBytes(caseId, jpegBytes, ".jpg");
        return new StoredUpload(
                savedPath,
                FileFormat.JPEG,
                convertedName(file.getOriginalFilename(), ".jpg"),
                "image/jpeg",
                jpegBytes.length,
                true,
                file.getContentType()
        );
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

    private String normalizedContentType(String contentType, FileFormat format) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return switch (format) {
            case PDF -> "application/pdf";
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
        };
    }

    private void validateBinaryContent(FileFormat format, Path savedPath) {
        if (format != FileFormat.JPEG && format != FileFormat.PNG) {
            return;
        }

        try (InputStream in = Files.newInputStream(savedPath)) {
            var image = ImageIO.read(in);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalArgumentException("invalid image file: unreadable image content");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid image file: unreadable image content", ex);
        }
    }

    private void deleteStoredFileQuietly(Path savedPath) {
        try {
            Files.deleteIfExists(savedPath);
        } catch (IOException ignored) {
            // best-effort cleanup only
        }
    }

    private long readStoredFileSize(Path savedPath) {
        try {
            return Files.size(savedPath);
        } catch (IOException ex) {
            deleteStoredFileQuietly(savedPath);
            throw new IllegalStateException("failed to read uploaded file size", ex);
        }
    }

    private byte[] writeImageAsJpeg(BufferedImage original) {
        BufferedImage rgbImage = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            graphics.drawImage(original, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(rgbImage, "jpeg", out)) {
                throw unsupportedFormatException(null);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to auto-convert image to JPEG", ex);
        }
        return out.toByteArray();
    }

    private String convertedName(String originalName, String extension) {
        String baseName = defaultString(originalName, "uploaded").trim();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        if (baseName.isBlank()) {
            baseName = "uploaded";
        }
        return baseName + "_autoconvert" + extension;
    }

    private IllegalArgumentException unsupportedFormatException(Exception cause) {
        String message = "unsupported file format. Upload PDF, JPEG, or PNG directly, or use a decodable image file that can be auto-converted to JPEG.";
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
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

    private record StoredUpload(
            Path savedPath,
            FileFormat format,
            String storedOriginalName,
            String contentType,
            long sizeBytes,
            boolean autoConverted,
            String sourceContentType
    ) {
    }
}
