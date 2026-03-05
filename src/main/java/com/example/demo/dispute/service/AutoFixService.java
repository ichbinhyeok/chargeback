package com.example.demo.dispute.service;

import com.example.demo.dispute.api.EvidenceFileInput;
import com.example.demo.dispute.api.FixJobResponse;
import com.example.demo.dispute.api.ValidateCaseResponse;
import com.example.demo.dispute.domain.CaseState;
import com.example.demo.dispute.domain.EvidenceType;
import com.example.demo.dispute.domain.FileFormat;
import com.example.demo.dispute.domain.FixJobStatus;
import com.example.demo.dispute.domain.Platform;
import com.example.demo.dispute.domain.ProductScope;
import com.example.demo.dispute.domain.ValidationSource;
import com.example.demo.dispute.persistence.DisputeCase;
import com.example.demo.dispute.persistence.EvidenceFileEntity;
import com.example.demo.dispute.persistence.EvidenceFileRepository;
import com.example.demo.dispute.persistence.FixJobEntity;
import com.example.demo.dispute.persistence.FixJobRepository;
import com.example.demo.dispute.service.pdf.PdfMetadata;
import com.example.demo.dispute.service.pdf.PdfMetadataExtractor;
import jakarta.persistence.EntityNotFoundException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AutoFixService {

    private static final String FAIL_CODE_NOTHING_TO_FIX = "ERR_FIX_NOTHING_TO_FIX";
    private static final String FAIL_CODE_INTERNAL = "ERR_FIX_INTERNAL";
    private static final long SHOPIFY_FILE_LIMIT_BYTES = 2L * 1024L * 1024L;

    private final CaseService caseService;
    private final FixJobRepository fixJobRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceFileService evidenceFileService;
    private final ValidationService validationService;
    private final ValidationHistoryService validationHistoryService;
    private final AuditLogService auditLogService;
    private final PdfMetadataExtractor pdfMetadataExtractor;

    public AutoFixService(
            CaseService caseService,
            FixJobRepository fixJobRepository,
            EvidenceFileRepository evidenceFileRepository,
            EvidenceFileService evidenceFileService,
            ValidationService validationService,
            ValidationHistoryService validationHistoryService,
            AuditLogService auditLogService,
            PdfMetadataExtractor pdfMetadataExtractor
    ) {
        this.caseService = caseService;
        this.fixJobRepository = fixJobRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceFileService = evidenceFileService;
        this.validationService = validationService;
        this.validationHistoryService = validationHistoryService;
        this.auditLogService = auditLogService;
        this.pdfMetadataExtractor = pdfMetadataExtractor;
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

            FixChange change = applySupportedFixes(disputeCase);
            if (change.isNoop()) {
                List<EvidenceFileInput> unchangedFiles = evidenceFileService.listAsValidationInputs(disputeCase.getId());
                ValidateCaseResponse unchangedValidation = validationService.validate(disputeCase, unchangedFiles, false);
                validationHistoryService.record(disputeCase, unchangedValidation, ValidationSource.AUTO_FIX, false);
                caseService.transitionState(disputeCase, unchangedValidation.passed() ? CaseState.READY : CaseState.BLOCKED);

                return failJob(
                        job,
                        FAIL_CODE_NOTHING_TO_FIX,
                        "No supported auto-fix issue found. Supported: merge multi-file-per-type and Shopify oversized image compression."
                );
            }

            List<EvidenceFileInput> files = evidenceFileService.listAsValidationInputs(disputeCase.getId());
            ValidateCaseResponse response = validationService.validate(disputeCase, files, false);
            validationHistoryService.record(disputeCase, response, ValidationSource.AUTO_FIX, false);
            caseService.transitionState(disputeCase, response.passed() ? CaseState.READY : CaseState.BLOCKED);

            job.setStatus(FixJobStatus.SUCCEEDED);
            job.setSummary(
                    "Merged " + change.mergedTypes() + " type(s), compressed " + change.compressedImages()
                            + " image(s). Validation passed=" + response.passed() + "."
            );
            job.setFailCode(null);
            job.setFailMessage(null);
            job.setFinishedAt(Instant.now());
            FixJobEntity saved = fixJobRepository.save(job);

            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_COMPLETED",
                    "jobId=" + saved.getId() + ",mergedTypes=" + change.mergedTypes()
                            + ",compressedImages=" + change.compressedImages() + ",passed=" + response.passed()
            );
            return saved;
        } catch (RuntimeException ex) {
            caseService.transitionState(disputeCase, CaseState.BLOCKED);
            return failJob(job, FAIL_CODE_INTERNAL, "Auto-fix failed: " + ex.getMessage());
        }
    }

    private FixChange applySupportedFixes(DisputeCase disputeCase) {
        int mergedTypes = mergeFilesByEvidenceType(disputeCase);
        int compressedImages = compressOversizedShopifyImages(disputeCase);
        return new FixChange(mergedTypes, compressedImages);
    }

    private int mergeFilesByEvidenceType(DisputeCase disputeCase) {
        UUID caseId = disputeCase.getId();
        List<EvidenceFileEntity> allFiles = evidenceFileRepository.findByDisputeCaseId(caseId);
        Map<EvidenceType, List<EvidenceFileEntity>> grouped = new EnumMap<>(EvidenceType.class);
        for (EvidenceFileEntity file : allFiles) {
            grouped.computeIfAbsent(file.getEvidenceType(), ignored -> new ArrayList<>()).add(file);
        }

        int mergedTypeCount = 0;
        for (Map.Entry<EvidenceType, List<EvidenceFileEntity>> entry : grouped.entrySet()) {
            EvidenceType type = entry.getKey();
            List<EvidenceFileEntity> filesByType = entry.getValue();
            if (filesByType.size() <= 1) {
                continue;
            }

            List<EvidenceFileEntity> ordered = filesByType.stream()
                    .sorted(Comparator.comparing(EvidenceFileEntity::getCreatedAt).thenComparing(EvidenceFileEntity::getId))
                    .toList();

            Path mergedPath = buildTargetPdfPath(ordered.get(ordered.size() - 1).getStoragePath());
            mergeEvidenceFilesToPdf(ordered, mergedPath);
            PdfMetadata metadata = pdfMetadataExtractor.extract(mergedPath);
            long mergedSize;
            try {
                mergedSize = Files.size(mergedPath);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to read merged file size", ex);
            }

            EvidenceFileEntity merged = new EvidenceFileEntity();
            merged.setDisputeCase(disputeCase);
            merged.setEvidenceType(type);
            merged.setOriginalName(type.name().toLowerCase(Locale.ROOT) + "_merged.pdf");
            merged.setContentType("application/pdf");
            merged.setStoragePath(mergedPath.toString());
            merged.setSizeBytes(mergedSize);
            merged.setPageCount(metadata.pageCount());
            merged.setFileFormat(FileFormat.PDF);
            merged.setPdfACompliant(metadata.pdfACompliant());
            merged.setPdfPortfolio(metadata.pdfPortfolio());
            merged.setExternalLinkDetected(metadata.externalLinkDetected());
            evidenceFileRepository.save(merged);

            for (EvidenceFileEntity existing : ordered) {
                deletePathQuietly(Path.of(existing.getStoragePath()));
                evidenceFileRepository.delete(existing);
            }

            mergedTypeCount++;
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_MERGED_TYPE",
                    "evidenceType=" + type + ",inputCount=" + ordered.size() + ",mergedFileId=" + merged.getId()
            );
        }

        return mergedTypeCount;
    }

    private int compressOversizedShopifyImages(DisputeCase disputeCase) {
        if (disputeCase.getPlatform() != Platform.SHOPIFY
                || disputeCase.getProductScope() != ProductScope.SHOPIFY_PAYMENTS_CHARGEBACK) {
            return 0;
        }

        int compressed = 0;
        List<EvidenceFileEntity> files = evidenceFileRepository.findByDisputeCaseId(disputeCase.getId());
        for (EvidenceFileEntity file : files) {
            if (!isImage(file.getFileFormat())) {
                continue;
            }
            if (file.getSizeBytes() <= SHOPIFY_FILE_LIMIT_BYTES) {
                continue;
            }

            Path originalPath = Path.of(file.getStoragePath());
            Path compressedPath = buildTargetJpegPath(file.getStoragePath());
            byte[] compressedBytes = compressImageToTargetSize(originalPath, SHOPIFY_FILE_LIMIT_BYTES);
            if (compressedBytes.length == 0 || compressedBytes.length >= file.getSizeBytes()) {
                continue;
            }
            if (compressedBytes.length > SHOPIFY_FILE_LIMIT_BYTES) {
                continue;
            }

            try {
                Files.write(compressedPath, compressedBytes);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to write compressed image", ex);
            }

            file.setOriginalName(stripExtension(file.getOriginalName()) + "_autofix.jpg");
            file.setContentType("image/jpeg");
            file.setStoragePath(compressedPath.toString());
            file.setSizeBytes(compressedBytes.length);
            file.setPageCount(1);
            file.setFileFormat(FileFormat.JPEG);
            file.setPdfACompliant(true);
            file.setPdfPortfolio(false);
            file.setExternalLinkDetected(false);
            evidenceFileRepository.save(file);
            deletePathQuietly(originalPath);

            compressed++;
            auditLogService.log(
                    disputeCase,
                    "SYSTEM",
                    "AUTO_FIX_COMPRESSED_IMAGE",
                    "fileId=" + file.getId() + ",newSize=" + compressedBytes.length
            );
        }

        return compressed;
    }

    private void mergeEvidenceFilesToPdf(List<EvidenceFileEntity> files, Path targetPath) {
        try (PDDocument merged = new PDDocument()) {
            for (EvidenceFileEntity file : files) {
                Path sourcePath = Path.of(file.getStoragePath());
                if (file.getFileFormat() == FileFormat.PDF) {
                    try (PDDocument source = Loader.loadPDF(sourcePath.toFile())) {
                        for (PDPage page : source.getPages()) {
                            merged.importPage(page);
                        }
                    }
                } else if (isImage(file.getFileFormat())) {
                    appendImageAsPage(merged, sourcePath);
                }
            }
            Files.createDirectories(targetPath.getParent());
            merged.save(targetPath.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to merge files into PDF", ex);
        }
    }

    private void appendImageAsPage(PDDocument merged, Path imagePath) throws IOException {
        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new IllegalStateException("unsupported image content for file: " + imagePath);
        }

        PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
        merged.addPage(page);

        PDImageXObject pdImage = JPEGFactory.createFromImage(merged, image, 0.9f);
        try (PDPageContentStream content = new PDPageContentStream(merged, page)) {
            content.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
        }
    }

    private byte[] compressImageToTargetSize(Path sourcePath, long maxBytes) {
        BufferedImage original;
        try {
            original = ImageIO.read(sourcePath.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read image for compression", ex);
        }
        if (original == null) {
            throw new IllegalStateException("unsupported image file: " + sourcePath);
        }

        float[] scales = new float[] {1.0f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f};
        float[] qualities = new float[] {0.9f, 0.75f, 0.6f, 0.45f, 0.3f};
        byte[] best = null;

        for (float scale : scales) {
            BufferedImage scaled = scaleImage(original, scale);
            for (float quality : qualities) {
                byte[] candidate = writeJpeg(scaled, quality);
                if (best == null || candidate.length < best.length) {
                    best = candidate;
                }
                if (candidate.length <= maxBytes) {
                    return candidate;
                }
            }
        }

        return best == null ? new byte[0] : best;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to encode JPEG", ex);
        }
    }

    private BufferedImage scaleImage(BufferedImage source, float scale) {
        int targetWidth = Math.max(1, Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private boolean isImage(FileFormat fileFormat) {
        return fileFormat == FileFormat.JPEG || fileFormat == FileFormat.PNG;
    }

    private Path buildTargetPdfPath(String referencePath) {
        Path parent = Path.of(referencePath).getParent();
        return parent.resolve(UUID.randomUUID() + "_merged.pdf");
    }

    private Path buildTargetJpegPath(String referencePath) {
        Path parent = Path.of(referencePath).getParent();
        return parent.resolve(UUID.randomUUID() + "_compressed.jpg");
    }

    private String stripExtension(String fileName) {
        if (fileName == null) {
            return "file";
        }
        int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
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

    private record FixChange(int mergedTypes, int compressedImages) {
        boolean isNoop() {
            return mergedTypes <= 0 && compressedImages <= 0;
        }
    }
}
